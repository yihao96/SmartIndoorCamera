package com.alexlim.smartindoorcamera;

import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;

import com.alexlim.smartindoorcamera.model.FirebaseImageLog;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;

public class MainActivityViewModel extends ViewModel {
    private static final String TAG = MainActivityViewModel.class.getSimpleName();

    private static String FIREBASE_MOTION_REF = "motion";
    private static String FIREBASE_MOTION_LOGS = "motion-logs";
    private static String FIREBASE_IMAGE_PREFIX = "images/motion";
    private static String FIREBASE_ON_OFF_REF = "OnOff";

    SingleLiveEvent armed = new SingleLiveEvent<Boolean>();

    private DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference(FIREBASE_ON_OFF_REF).child("on");

    SingleLiveEvent getArmed() {
        return armed;
    }

    public MainActivityViewModel() {
        dbRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Boolean systemArmed = (Boolean) dataSnapshot.getValue();
                Log.d(TAG, "onDataChange: " + systemArmed);
                armed.setValue(systemArmed);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    void uploadMotionImage(Bitmap imageBytes) {
        Boolean isArmed = (Boolean) armed.getValue();
        if (isArmed != null && isArmed) {
            StorageReference storageReference = FirebaseStorage.getInstance().getReference(FIREBASE_MOTION_REF);
            StorageReference imageStorageRef = storageReference.child(FIREBASE_IMAGE_PREFIX + System.currentTimeMillis() + ".jpg");
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            imageBytes.compress(Bitmap.CompressFormat.JPEG, 100, stream);
            UploadTask uploadTask = imageStorageRef.putBytes(stream.toByteArray());

            uploadTask.addOnFailureListener(e -> Log.d(TAG, "uploadMotionImage: upload failed"))
                    .addOnSuccessListener(taskSnapshot -> {
                        Log.d(TAG, "uploadMotionImage: upload succeed");
                        String downloadUrl = imageStorageRef.getPath();
                        DatabaseReference ref = FirebaseDatabase.getInstance().getReference(FIREBASE_MOTION_LOGS).push();
                        ref.setValue(new FirebaseImageLog(System.currentTimeMillis(), downloadUrl));
                    });
        }
    }

    void toggleSystemArmedStatus() {
//        Boolean isArmed = (Boolean) armed.getValue();
//        if (isArmed) {
//            dbRef.setValue(false);
//        } else {
//            dbRef.setValue(true);
//        }
    }
}
