package com.alexlim.smartindoorcamera.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureRequest.Builder;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;

import kotlin.collections.CollectionsKt;

import java.nio.ByteBuffer;

import static android.hardware.camera2.CameraAccessException.CAMERA_ERROR;

public class CaptureDevice implements AutoCloseable {

    private static final String TAG = CaptureDevice.class.getSimpleName();
    private static final int IMAGE_WIDTH = 640;
    private static final int IMAGE_HEIGHT = 480;
    private static final int MAX_IMAGE = 1;
    private static CaptureDevice mCaptureDevice = new CaptureDevice();
    
    private ImageReader mImageReader;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCameraCaptureSession;
    private ImageCapturedListener imageCapturedListener;
    
    public interface ImageCapturedListener {
        void onImageCaptured(Bitmap bitmap);
    }

    public void initializeCamera(Context context, Handler backgroundHandler, ImageCapturedListener imageListener) {
        CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        String[] camIds = null;

        try {
            camIds = cameraManager.getCameraIdList();
        } catch (CameraAccessException e) {
            Log.e(TAG, "initializeCamera: Camera access exception getting IDs", e);
            try {
                throw new CameraAccessException(CAMERA_ERROR, "Camera access exception getting IDs");
            } catch (CameraAccessException ignored) {

            }
        }

        if (camIds.length == 0) {
            Log.e(TAG, "initializeCamera: No camera found");
            try {
                throw new CameraAccessException(CAMERA_ERROR, "No camera found");
            } catch (CameraAccessException ignored) {

            }
        }

        String id = camIds[0];
        mImageReader = ImageReader.newInstance(IMAGE_WIDTH, IMAGE_HEIGHT, ImageFormat.JPEG, MAX_IMAGE);
        imageCapturedListener = imageListener;
        if (mImageReader != null) {
            mImageReader.setOnImageAvailableListener(imageAvailableListener, backgroundHandler);
        }
        try {
            cameraManager.openCamera(id, mStateCallback, backgroundHandler);
        } catch (Exception e) {
            Log.e(TAG, "initializeCamera: Camera access exception", e);
        }
    }

    private ImageReader.OnImageAvailableListener imageAvailableListener = reader -> {
        Image image = reader.acquireLatestImage();
        Image.Plane[] imagePlane = image.getPlanes();
        ByteBuffer imageBuffer = imagePlane[0].getBuffer();
        byte[] imageBytes = new byte[imageBuffer.remaining()];
        imageBuffer.get(imageBytes);
        image.close();
        Bitmap bitmap = getBitmapFromByteArray(imageBytes);
        imageCapturedListener.onImageCaptured(bitmap);
    };

    public void takePicture() throws CameraAccessException {
        mCameraDevice.createCaptureSession(CollectionsKt.arrayListOf(mImageReader != null ? mImageReader.getSurface() : null),
                    mSessionCallback, null);
    }

    private Bitmap getBitmapFromByteArray(byte[] imageBytes) {
        Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        Matrix matrix = new Matrix();
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private void triggerImageCapture() throws CameraAccessException {
        Builder captureBuilder = mCameraDevice != null ? mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE) : null;
        if (captureBuilder != null) {
            captureBuilder.addTarget(mImageReader.getSurface());

            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            captureBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);
            Log.d(TAG, "triggerImageCapture: Session initialized.");
            mCameraCaptureSession.capture(captureBuilder.build(), mCaptureCallback, null);
        }
    }

    private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            Log.d(TAG, "onCaptureProgressed: Partial result");
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            Log.d(TAG, "onCaptureFailed: Capture session failed");
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            session.close();
            mCameraCaptureSession = null;
            Log.d(TAG, "onCaptureCompleted: Capture session closed");
        }
    };

    private CameraCaptureSession.StateCallback mSessionCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            if (mCameraDevice == null) {
                return;
            }
            mCameraCaptureSession = session;
            try {
                triggerImageCapture();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            Log.w(TAG, "onConfigureFailed: Configure camera failed.");
        }
    };

    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.d(TAG, "onOpened: Camera opened.");
            mCameraDevice = camera;
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.d(TAG, "onDisconnected: Camera disconnected.");
            camera.close();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.d(TAG, "onError: Camera device error.");
            camera.close();
        }

        @Override
        public void onClosed(@NonNull CameraDevice camera) {
            Log.d(TAG, "onClosed: Camera closed.");
            mCameraDevice = null;
        }
    };

    @Override
    public void close() {
        if (mCameraDevice != null) {
            mCameraDevice.close();
        }
    }

    public static CaptureDevice getInstance() {
        return mCaptureDevice;
    }
}
