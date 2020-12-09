'use strict';

// The Cloud Functions for Firebase SDK to create Cloud Functions and setup triggers.
const functions = require('firebase-functions');

const {smarthome} = require('actions-on-google');
const util = require('util');

// The Firebase Admin SDK to access the Firebase Realtime Database.
const admin = require('firebase-admin');
admin.initializeApp();

const firebaseRef = admin.database().ref('/');

exports.annotateImage = functions.database.ref('/motion-logs/{id}')
  .onCreate((snapshot, context) => {
    const original = snapshot.val();
    console.log('AnnotatingImage', context.params.pushId, original);
    const fileName = 'gs://smart-indoor-camera.appspot.com' + original.imageRef;
    console.log('Filename:', fileName);
    const request = {
      source: {
        imageUri: fileName
      }
    };

    var topic = "motions";

    var payload = {
      data: {
        title: "Motion Alert!",
        body: "A motion has been detected",
        imageRef: original.imageRef,
        timestamp: original.timestamp.toString()
      }
    };
    return admin.messaging().sendToTopic(topic, payload)
      .then(function (response) {
        // See the MessagingTopicResponse reference documentation for the
        // contents of response.
        console.log("Successfully sent message:", response);
      })
      .catch(function (error) {
        console.log("Error sending message:", error);
      });
  });

exports.fakeauth = functions.https.onRequest((request, response) => {
  const responseurl = util.format('%s?code=%s&state=%s',
    decodeURIComponent(request.query.redirect_uri), 'xxxxxx',
    request.query.state);
  console.log(responseurl);
  return response.redirect(responseurl);
});

exports.faketoken = functions.https.onRequest((request, response) => {
  const grantType = request.query.grant_type
    ? request.query.grant_type : request.body.grant_type;
  const secondsInDay = 86400; // 60 * 60 * 24
  const HTTP_STATUS_OK = 200;
  console.log(`Grant type ${grantType}`);

  let obj;
  if (grantType === 'authorization_code') {
    obj = {
      token_type: 'bearer',
      access_token: '123access',
      refresh_token: '123refresh',
      expires_in: secondsInDay,
    };
  } else if (grantType === 'refresh_token') {
    obj = {
      token_type: 'bearer',
      access_token: '123access',
      expires_in: secondsInDay,
    };
  }
  response.status(HTTP_STATUS_OK)
    .json(obj);
});

let jwt;
try {
  jwt = require('./key.json');
} catch (e) {
  console.warn('Service account key is not found');
  console.warn('Report state will be unavailable');
}

const app = smarthome({
  debug: true,
  key: '<api-key>',
  jwt: jwt,
});

app.onSync((body) => {
  return {
    requestId: body.requestId,
    payload: {
      devices: [{
        id: '1',
        type: 'action.devices.types.CAMERA',
        traits: [
          'action.devices.traits.OnOff'
        ],
        name: {
          name: 'Motion Camera'
        },
        willReportState: true
      }]
    }
  };
});

const queryFirebase = async (deviceId) => {
  const snapshot = await firebaseRef.child('OnOff').once('value');
  const snapshotVal = snapshot.val();
  return {
    on: snapshotVal.on,
  };
}
const queryDevice = async (deviceId) => {
  const data = await queryFirebase(deviceId);
  return {
    on: data.on,
  };
}

app.onQuery(async (body) => {
  const {requestId} = body;
  const payload = {
    devices: {},
  };
  const queryPromises = [];
  for (const input of body.inputs) {
    for (const device of input.payload.devices) {
      const deviceId = device.id;
      queryPromises.push(queryDevice(deviceId)
        .then((data) => {
          // Add response to device payload
          payload.devices[deviceId] = data;
        }
      ));
    }
  }
  // Wait for all promises to resolve
  await Promise.all(queryPromises)
  return {
    requestId: requestId,
    payload: payload,
  };
});

app.onExecute((body) => {
  const {requestId} = body;
  const payload = {
    commands: [{
      ids: [],
      status: 'SUCCESS',
      states: {
        online: true,
      },
    }],
  };
  for (const input of body.inputs) {
    for (const command of input.payload.commands) {
      for (const device of command.devices) {
        const deviceId = device.id;
        payload.commands[0].ids.push(deviceId);
        for (const execution of command.execution) {
          const execCommand = execution.command;
          const {params} = execution;
          switch (execCommand) {
            case 'action.devices.commands.OnOff':
              firebaseRef.child('OnOff').update({
                on: params.on,
              });
              payload.commands[0].states.on = params.on;
              break;
          }
        }
      }
    }
  }
  return {
    requestId: requestId,
    payload: payload,
  };
});

exports.smarthome = functions.https.onRequest(app);

exports.requestsync = functions.https.onRequest(async (request, response) => {
  console.info('Request SYNC for user 123');
  try {
    await app.requestSync('123');
    console.log('Request sync completed');
    response.json(res.data);
  } catch (err) {
    console.error(err);
    response.status(500).send(`Error requesting sync: ${err}`)
  }
});

/**
 * Send a REPORT STATE call to the homegraph when data for any device id
 * has been changed.
 */
exports.reportstate = functions.database.ref('/').onWrite(async (change, context) => {
  console.info('Firebase write event triggered this cloud function');
  if (!app.jwt) {
    console.warn('Service account key is not configured');
    console.warn('Report state is unavailable');
    return;
  }
  const snapshot = change.after.val();

  const postData = {
    requestId: 'ff36a3cc', /* Any unique ID */
    agentUserId: '123', /* Hardcoded user ID */
    payload: {
      devices: {
        states: {
          /* Report the current state of Motion Camera */
          [context.params.deviceId]: {
            on: snapshot.OnOff.on,
          },
        },
      },
    },
  };

  const data = await app.reportState(postData);
  console.log('Report state came back');
  console.info(data);
});
