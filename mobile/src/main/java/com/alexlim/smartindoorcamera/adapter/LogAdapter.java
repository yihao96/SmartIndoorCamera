package com.alexlim.smartindoorcamera.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.alexlim.smartindoorcamera.R;
import com.alexlim.smartindoorcamera.model.FirebaseImageLog;
import com.alexlim.smartindoorcamera.view.LogsViewHolder;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;

public class LogAdapter extends FirebaseRecyclerAdapter<FirebaseImageLog, LogsViewHolder> {
    /**
     * Initialize a {@link RecyclerView.Adapter} that listens to a Firebase query. See
     * {@link FirebaseRecyclerOptions} for configuration options.
     *
     * @param options
     */
    public LogAdapter(FirebaseRecyclerOptions<FirebaseImageLog> options) {
        super(options);
    }

    @Override
    protected void onBindViewHolder(LogsViewHolder logsViewHolder, int i, FirebaseImageLog firebaseImageLog) {
        logsViewHolder.setLog(firebaseImageLog);
    }

    @Override
    public LogsViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_log, parent, false);
        LogsViewHolder lvh = new LogsViewHolder(v);

        return lvh;
    }
}
