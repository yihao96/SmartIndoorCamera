package com.alexlim.smartindoorcamera.model;

public class FirebaseImageLog {
    private long timestamp;
    private String imageRef;

    public FirebaseImageLog() {
        // For Firebase
    }

    public FirebaseImageLog(long timestamp, String imageRef) {
        this.timestamp = timestamp;
        this.imageRef = imageRef;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getImageRef() {
        return imageRef;
    }
}
