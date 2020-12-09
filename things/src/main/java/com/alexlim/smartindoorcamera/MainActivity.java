package com.alexlim.smartindoorcamera;

import android.content.SharedPreferences;
import android.hardware.camera2.CameraAccessException;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.ImageView;

import com.alexlim.smartindoorcamera.assistant.EmbeddedAssistant;
import com.alexlim.smartindoorcamera.camera.CaptureDevice;
import com.google.android.things.contrib.driver.button.Button;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManager;
import com.google.assistant.embedded.v1alpha2.SpeechRecognitionResult;
import com.google.auth.oauth2.UserCredentials;

import org.json.JSONException;

import java.io.IOException;
import java.util.List;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProviders;

/**
 * Skeleton of an Android Things activity.
 * <p>
 * Android Things peripheral APIs are accessible through the class
 * PeripheralManagerService. For example, the snippet below will open a GPIO pin and
 * set it to HIGH:
 *
 * <pre>{@code
 * PeripheralManagerService service = new PeripheralManagerService();
 * mLedGpio = service.openGpio("BCM6");
 * mLedGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
 * mLedGpio.setValue(true);
 * }</pre>
 * <p>
 * For more complex peripherals, look for an existing user-space driver, or implement one if none
 * is available.
 *
 * @see <a href="https://github.com/androidthings/contrib-drivers#readme">https://github.com/androidthings/contrib-drivers#readme</a>
 */

public class MainActivity extends AppCompatActivity implements MotionSensor.MotionListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    // GPIO constants
    final static String LED_GPIO_PIN = "BCM13";
    final static String LED_ARMED_INDICATOR_PIN = "BCM17";
    final static String MOTION_SENSOR_GPIO_PIN = "BCM18";
    final static String BUTTON_GPIO_PIN = "BCM20";
    final static String LED_ASSISTANT = "BCM19";

    // Audio constants
    private static final String PREF_CURRENT_VOLUME = "current_volume";
    private static final int SAMPLE_RATE = 16000;
    private static final int DEFAULT_VOLUME = 100;

    // Assistant SDK constants
    private static final String DEVICE_MODEL_ID = "smart-indoor-camera-motion-alert-pq0zck";
    private static final String DEVICE_INSTANCE_ID = "something-unique-they-said";
    private static final String LANGUAGE_CODE = "en-US";

    private Gpio ledMotionIndicatorGpio;
    private Gpio ledArmedIndicatorGpio;
    private CaptureDevice camera;
    private MotionSensor motionSensor;

    private Button assistButton;
    private Gpio ledAssistantIndicatorGpio;
    private EmbeddedAssistant mEmbeddedAssistant;
    private MainActivityViewModel mainActivityViewModel;

    private ImageView imageViewUI;
    private android.widget.Button buttonUI;

    private Boolean isAssistantActive = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle(R.string.app_name);

        setupViewModel();
        setupCamera();
        setupActuators();
        setupSensors();
        setupUI();

        setupAssistant();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Destroy motion LEDs
        try {
            ledMotionIndicatorGpio.close();
            ledArmedIndicatorGpio.close();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to close LED.", e);
        }

        // Destroy GA's LED and button
        if (isAssistantActive) {
            try {
                ledAssistantIndicatorGpio.close();
                assistButton.close();
            } catch (IOException e) {
                Log.e(TAG, "onDestroy: failed to close led/button.", e);
            }
        }
    }

    private void setupUI() {
        imageViewUI = findViewById(R.id.image_view_motion);

        buttonUI = findViewById(R.id.button_arm_disarm);
        buttonUI.setOnClickListener(v -> mainActivityViewModel.toggleSystemArmedStatus());
        mainActivityViewModel.getArmed().observe(this, armed -> {
            if (armed != null) {
                if ((Boolean) armed) {
                    buttonUI.setText("DISARM SYSTEM");
                } else {
                    buttonUI.setText("ARM SYSTEM");
                }

                try {
                    ledArmedIndicatorGpio.setValue((Boolean) armed);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void setupAssistant() {
        isAssistantActive = true;
        try {
            assistButton = new Button(BUTTON_GPIO_PIN, Button.LogicState.PRESSED_WHEN_LOW);
            assistButton.setOnButtonEventListener((button, pressed) -> {
                Log.d(TAG, "setupAssistant: button event: " + pressed);
                try {
                    ledAssistantIndicatorGpio.setValue(pressed);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to set LED value.", e);
                }

                if (pressed) {
                    mEmbeddedAssistant.startConversation();
                }
            });
            assistButton.setDebounceDelay(1000);
            ledAssistantIndicatorGpio = PeripheralManager.getInstance().openGpio(LED_ASSISTANT);
            ledAssistantIndicatorGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            ledAssistantIndicatorGpio.setValue(false);
        } catch (IOException e) {
            throw new IllegalStateException("Button/LED initializing failed.", e);
        }

        // Set volume from preferences
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        int initVolume = preferences.getInt(PREF_CURRENT_VOLUME, DEFAULT_VOLUME);
        Log.i(TAG, "setupAssistant: setting audio track volume to: " + initVolume);

        UserCredentials userCredentials = null;
        try {
            userCredentials = EmbeddedAssistant.generateCredentials(this, R.raw.credentials);
        } catch (IOException | JSONException e) {
            Log.e(TAG, "setupAssistant: error getting user credentials", e);
        }
        mEmbeddedAssistant = new EmbeddedAssistant.Builder()
                .setCredentials(userCredentials)
                .setDeviceInstanceId(DEVICE_INSTANCE_ID)
                .setDeviceModelId(DEVICE_MODEL_ID)
                .setLanguageCode(LANGUAGE_CODE)
                .setAudioSampleRate(SAMPLE_RATE)
                .setAudioVolume(initVolume)
                .setRequestCallback(new EmbeddedAssistant.RequestCallback() {
                    @Override
                    public void onRequestStart() {
                        Log.i(TAG, "onRequestStart: starting assistant request, enable microphone");
                    }

                    @Override
                    public void onSpeechRecognition(List<SpeechRecognitionResult> results) {
                        for (final SpeechRecognitionResult result : results) {
                            Log.i(TAG, "onSpeechRecognition: assistant request text: "
                                    + result.getTranscript()
                                    + " stability: "
                                    + Float.toString(result.getStability()));
                        }
                    }
                })
                .setConversationCallback(new EmbeddedAssistant.ConversationCallback() {
                    @Override
                    public void onError(Throwable throwable) {
                        Log.e(TAG, "onError: assist error: "+ throwable.getLocalizedMessage(), throwable);
                    }

                    @Override
                    public void onVolumeChanged(int percentage) {
                        Log.i(TAG, "onVolumeChanged: assistant volume changed: " + percentage);
                        // Update shared preferences
                        SharedPreferences.Editor editor = PreferenceManager
                                .getDefaultSharedPreferences(MainActivity.this)
                                .edit();
                        editor.putInt(PREF_CURRENT_VOLUME, percentage);
                        editor.apply();
                    }

                    @Override
                    public void onAssistantResponse(String response) {
                        Log.i(TAG, "onAssistantResponse: response: " + response);
                    }

                    @Override
                    public void onConversationFinished() {
                        Log.i(TAG, "onConversationFinished: assistant conversation finished");
                    }
                }).build();
        mEmbeddedAssistant.connect();
    }

    private void setupViewModel() {
        mainActivityViewModel = ViewModelProviders.of(this).get(MainActivityViewModel.class);
    }

    private void setupActuators() {
        try {
            ledMotionIndicatorGpio = PeripheralManager.getInstance().openGpio(LED_GPIO_PIN);
            ledMotionIndicatorGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            ledArmedIndicatorGpio = PeripheralManager.getInstance().openGpio(LED_ARMED_INDICATOR_PIN);
            ledArmedIndicatorGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
        } catch (IOException e) {
            throw new IllegalStateException("LED initializing failed.", e);
        }
    }

    private void setupSensors() {
        motionSensor = new MotionSensor(this, MOTION_SENSOR_GPIO_PIN);
        Lifecycle lifecycle = this.getLifecycle();
        lifecycle.addObserver(motionSensor);
    }

    private void setupCamera() {
        camera = CaptureDevice.getInstance();
        camera.initializeCamera(this, new Handler(), imageAvailableListener);
    }

    private CaptureDevice.ImageCapturedListener imageAvailableListener = bitmap -> {
        imageViewUI.setImageBitmap(bitmap);
        mainActivityViewModel.uploadMotionImage(bitmap);
    };

    @Override
    public void onMotionDetected() throws IOException {
        Log.d(TAG, "onMotionDetected");
        ledMotionIndicatorGpio.setValue(true);

        try {
            camera.takePicture();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onMotionStopped() throws IOException {
        Log.d(TAG, "onMotionStopped");
        ledMotionIndicatorGpio.setValue(false);
    }
}
