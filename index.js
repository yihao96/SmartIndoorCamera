'use strict';

// The Cloud Functions for Firebase SDK to create Cloud Functions and setup triggers.
const functions = require('firebase-functions');

// new added
const {smarthome} = require('actions-on-google');
const util = require('util');

// The Firebase Admin SDK to access the Firebase Realtime Database.
const admin = require('firebase-admin');
admin.initializeApp(functions.config().firebase);
//admin.initializeApp();

exports.annotateImage = functions.database.ref('/motion-logs/{id}')
  .onCreate((snapshot, context) => {
    const original = snapshot.val();
    console.log('AnnotatingImage', context.params.pushId, original);
    const fileName = 'gs://smart-indoor-camera.appspot.com' + original.imageRef;
    console.log('Filename:', fileName)
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
  }
  );

// new added
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

exports.smarthome = functions.https.onRequest(app);