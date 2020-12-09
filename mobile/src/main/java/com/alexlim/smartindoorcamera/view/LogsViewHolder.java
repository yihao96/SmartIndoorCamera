package com.alexlim.smartindoorcamera.view;

import android.text.format.DateUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.alexlim.smartindoorcamera.R;
import com.alexlim.smartindoorcamera.model.FirebaseImageLog;
import com.bumptech.glide.Glide;
import com.google.firebase.storage.FirebaseStorage;

public class LogsViewHolder extends RecyclerView.ViewHolder {

    ImageView imageViewLog;
    TextView timestampTextView;

    public LogsViewHolder(View itemView) {
        super(itemView);

        imageViewLog = itemView.findViewById(R.id.imageViewScreenshot);
        timestampTextView = itemView.findViewById(R.id.textViewTimestamp);
    }

    public void setLog(FirebaseImageLog log) {
        Long timestamp = log.getTimestamp();
        CharSequence timeDifference = DateUtils.getRelativeTimeSpanString(timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
        timestampTextView.setText(timeDifference);

        String imageRef = log.getImageRef();
        Glide.with(imageViewLog.getContext())
                .load(FirebaseStorage.getInstance().getReference(imageRef))
                .into(imageViewLog);
    }
}
