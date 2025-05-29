package com.lztek.api.demo;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SessionsAdapter extends RecyclerView.Adapter<SessionsAdapter.SessionViewHolder> {

    private static final String TAG = "SessionsAdapter";
    private List<Session> sessions; // Updated to use Room's Session
    private Context context;

    public SessionsAdapter(Context context, List<Session> sessions) {
        this.context = context;
        this.sessions = sessions;
    }

    @NotNull
    @Override
    public SessionViewHolder onCreateViewHolder(@NotNull ViewGroup parent, int viewType) {
        try {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_session, parent, false);
            if (view == null) {
                Log.e(TAG, "Failed to inflate item_session layout");
            }
            return new SessionViewHolder(view);
        } catch (Exception e) {
            Log.e(TAG, "Error inflating layout: " + e.getMessage());
            throw new RuntimeException("Failed to inflate item_session layout", e);
        }
    }

    @Override
    public void onBindViewHolder(@NotNull SessionViewHolder holder, int position) {
        Session session = sessions.get(position);
        try {
            holder.tvSessionName.setText(session.sessionName);
            holder.tvPatientName.setText(session.patientName);
            holder.tvLocationName.setText(session.locationName);
            holder.tvDateTime.setText(session.dateTime);
            holder.tvStatus.setText(session.status);

            holder.btnEdit.setOnClickListener(v -> {
                Intent intent = new Intent(context, EditSessionActivity.class);
                intent.putExtra("session_id", session.id);
                context.startActivity(intent);
            });

            holder.btnDelete.setOnClickListener(v -> {
                if (context instanceof OfflineModeActivity) {
                    ((OfflineModeActivity) context).deleteSession(session.id);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error binding data at position " + position + ": " + e.getMessage());
        }
    }

    @Override
    public int getItemCount() {
        return sessions != null ? sessions.size() : 0;
    }

    static class SessionViewHolder extends RecyclerView.ViewHolder {
        TextView tvSessionName, tvPatientName, tvLocationName, tvDateTime, tvStatus;
        Button btnEdit, btnDelete;

        public SessionViewHolder(@NotNull View itemView) {
            super(itemView);
            try {
                tvSessionName = itemView.findViewById(R.id.tv_session_name);
                tvPatientName = itemView.findViewById(R.id.tv_patient_name);
                tvLocationName = itemView.findViewById(R.id.tv_location_name);
                tvDateTime = itemView.findViewById(R.id.tv_date_time);
                tvStatus = itemView.findViewById(R.id.tv_status);
                btnEdit = itemView.findViewById(R.id.btn_edit_session);
                btnDelete = itemView.findViewById(R.id.btn_delete_session);

                if (tvSessionName == null) Log.e(TAG, "tv_session_name not found in item_session.xml");
                if (tvPatientName == null) Log.e(TAG, "tv_patient_name not found in item_session.xml");
                if (tvLocationName == null) Log.e(TAG, "tv_location_name not found in item_session.xml");
                if (tvDateTime == null) Log.e(TAG, "tv_date_time not found in item_session.xml");
                if (tvStatus == null) Log.e(TAG, "tv_status not found in item_session.xml");
                if (btnEdit == null) Log.e(TAG, "btn_edit_session not found in item_session.xml");
                if (btnDelete == null) Log.e(TAG, "btn_delete_session not found in item_session.xml");

                if (tvSessionName == null || tvPatientName == null || tvLocationName == null ||
                        tvDateTime == null || tvStatus == null || btnEdit == null || btnDelete == null) {
                    throw new RuntimeException("One or more views not found in item_session.xml");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error initializing views in SessionViewHolder: " + e.getMessage());
                throw new RuntimeException("Failed to initialize views in SessionViewHolder", e);
            }
        }
    }
}