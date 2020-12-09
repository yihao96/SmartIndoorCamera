package com.alexlim.smartindoorcamera;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import com.alexlim.smartindoorcamera.adapter.LogAdapter;
import com.alexlim.smartindoorcamera.model.FirebaseImageLog;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static String ORDER_BY_TIMESTAMP = "timestamp";
    private static String FIREBASE_MOTION_LOGS_REF = "motion-logs";
    private static String FIREBASE_ON_OFF_REF = "OnOff";

    private LogAdapter adapter;
    private SwitchCompat armSystemToggleButton;
    private ImageView armSystemImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//        FirebaseMessaging.getInstance().subscribeToTopic("motions");
//        FirebaseDatabase.getInstance().setPersistenceEnabled(true);

        setupMotionLogsRecyclerView();
        setupArmSystemToggle();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void setupMotionLogsRecyclerView() {
        RecyclerView recyclerViewImages = findViewById(R.id.recyclerViewImages);
        recyclerViewImages.setNestedScrollingEnabled(false);

        DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference(FIREBASE_MOTION_LOGS_REF);

        adapter = new LogAdapter(new FirebaseRecyclerOptions.Builder<FirebaseImageLog>().setQuery(dbRef.orderByChild(ORDER_BY_TIMESTAMP).getRef(), FirebaseImageLog.class).setLifecycleOwner(this).build());
//        FirebaseRecyclerOptions<FirebaseImageLog> options = new FirebaseRecyclerOptions.Builder<FirebaseImageLog>().setQuery(dbRef.orderByChild(ORDER_BY_TIMESTAMP), FirebaseImageLog.class).setLifecycleOwner(this).build();
//        adapter = new LogAdapter(options);

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        linearLayoutManager.setReverseLayout(true);
        linearLayoutManager.setStackFromEnd(true);
        recyclerViewImages.setLayoutManager(linearLayoutManager);
        recyclerViewImages.setAdapter(adapter);
    }

    private void setupArmSystemToggle() {
        armSystemToggleButton = findViewById(R.id.switchArmSystem);
        armSystemImageView = findViewById(R.id.imageViewArmSystem);
        DatabaseReference armedValue = FirebaseDatabase.getInstance().getReference(FIREBASE_ON_OFF_REF).child("on");

        armSystemToggleButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            armedValue.setValue(isChecked);
        });

        armedValue.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Log.d(TAG, "onDataChange: " + dataSnapshot.toString());
                Boolean isArmed = (Boolean) dataSnapshot.getValue();
                toggleUIState(isArmed);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    private void toggleUIState(Boolean isArmed) {
        armSystemToggleButton.setChecked(isArmed);

        if (isArmed) {
            armSystemToggleButton.setText("System armed");
            armSystemImageView.setImageResource(R.drawable.ic_armed);
        } else {
            armSystemToggleButton.setText("System disarmed");
            armSystemImageView.setImageResource(R.drawable.ic_not_armed);
        }
    }
}
