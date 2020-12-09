package com.alexlim.smartindoorcamera;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManager;

import java.io.IOException;

public class MotionSensor implements LifecycleObserver {
    private static final String TAG = MotionSensor.class.getSimpleName();

    private final Gpio motionSensorGpioPin;
    private final MotionListener motionListener;

    MotionSensor(MotionListener motionListener, String motionSensorPin) {
        this.motionListener = motionListener;

        try {
            motionSensorGpioPin = PeripheralManager.getInstance().openGpio(motionSensorPin);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to open GPIO.", e);
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    void start() {
        try {
            motionSensorGpioPin.setDirection(Gpio.DIRECTION_IN);
            motionSensorGpioPin.setActiveType(Gpio.ACTIVE_HIGH);
            motionSensorGpioPin.setEdgeTriggerType(Gpio.EDGE_BOTH);
        } catch (IOException e) {
            throw new IllegalStateException("Sensor error.", e);
        }

        try {
            motionSensorGpioPin.registerGpioCallback(gpio -> {
                if (gpio != null) {
                    try {
                        if (gpio.getValue()) {
                            motionListener.onMotionDetected();
                        } else {
                            motionListener.onMotionStopped();
                        }
                    } catch (IOException e) {
                        throw new IllegalStateException("Failed to get gpio value.", e);
                    }
                }
                return true;
            });
        } catch (IOException e) {
            throw new IllegalStateException("Sensor callback register failed.", e);
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    void stop() {
        try {
            motionSensorGpioPin.close();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to close Motion Sensor.", e);
        }
    }

    interface MotionListener {
        void onMotionDetected() throws IOException;
        void onMotionStopped() throws IOException;
    }
}
