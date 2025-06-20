//package com.lztek.api.demo;
//
//import android.view.LayoutInflater;
//import android.view.View;
//import android.view.ViewGroup;
//import android.widget.Button;
//import android.widget.TextView;
////import androidx.annotation.NonNull;
//import androidx.recyclerview.widget.RecyclerView;
//
//import org.jetbrains.annotations.NotNull;
//
//import java.util.ArrayList;
//import java.util.List;
//
//public class AppointmentAdapter extends RecyclerView.Adapter<AppointmentAdapter.AppointmentViewHolder> {
//
//    private List<Appointment> appointmentList;
//    private OnDetailsClickListener onDetailsClickListener;
//    private OnStartClickListener onStartClickListener;
//
//    // Constructor
//    public AppointmentAdapter(List<Appointment> appointmentList, OnDetailsClickListener onDetailsClickListener, OnStartClickListener onStartClickListener) {
//        this.appointmentList = appointmentList != null ? appointmentList : new ArrayList<>();
//        this.onDetailsClickListener = onDetailsClickListener;
//        this.onStartClickListener = onStartClickListener;
//    }
//
//    public void updateAppointments(List<Appointment> newAppointments) {
//        appointmentList.clear();
//        appointmentList.addAll(newAppointments);
//        notifyDataSetChanged();
//    }
//
//    @NotNull
//    @Override
//    public AppointmentViewHolder onCreateViewHolder(@NotNull ViewGroup parent, int viewType) {
//        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_appointment, parent, false);
//        return new AppointmentViewHolder(view);
//    }
//
//    @Override
//    public void onBindViewHolder(@NotNull AppointmentViewHolder holder, int position) {
//        Appointment appointment = appointmentList.get(position);
//
//        // Bind data to views
//        holder.patientName.setText(appointment.getPatientName());
//        holder.paramedicName.setText(appointment.getParamedicName());
//        holder.doctorName.setText(appointment.getDoctorName());
//        holder.appointmentDate.setText(appointment.getAppointmentDate());
//        holder.appointmentTime.setText(appointment.getAppointmentTime());
//        holder.appointmentStatus.setText(appointment.getAppointmentStatus());
//        holder.bookedOn.setText(appointment.getCreatedAt());
//
//        // Set click listeners for Details and Start buttons
//        holder.detailsButton.setOnClickListener(v -> onDetailsClickListener.onDetailsClick(appointment));
//        holder.startButton.setOnClickListener(v -> onStartClickListener.onStartClick(appointment));
//    }
//
//    @Override
//    public int getItemCount() {
//        return appointmentList.size();
//    }
//
//    // ViewHolder class for Appointment items
//    public static class AppointmentViewHolder extends RecyclerView.ViewHolder {
//        TextView patientName, paramedicName, doctorName, appointmentDate, appointmentTime, appointmentStatus, bookedOn;
//
//        Button detailsButton, startButton;
//
//        public AppointmentViewHolder(@NotNull View itemView) {
//            super(itemView);
//            patientName = itemView.findViewById(R.id.patient_name);
//            paramedicName = itemView.findViewById(R.id.paramedic_name);
//            doctorName = itemView.findViewById(R.id.doctor_name);
//            appointmentDate = itemView.findViewById(R.id.appointment_date);
//            appointmentTime = itemView.findViewById(R.id.appointment_time);
//            appointmentStatus = itemView.findViewById(R.id.appointment_status);
//            bookedOn = itemView.findViewById(R.id.booked_on);
//            detailsButton = itemView.findViewById(R.id.button_details);
//            startButton = itemView.findViewById(R.id.button_start);
//        }
//    }
//
//    // Interfaces for button click listeners
//    public interface OnDetailsClickListener {
//        void onDetailsClick(Appointment appointment);
//    }
//
//    public interface OnStartClickListener {
//        void onStartClick(Appointment appointment);
//    }
//}
//

package com.lztek.api.demo;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.List;

public class AppointmentAdapter extends RecyclerView.Adapter<AppointmentAdapter.AppointmentViewHolder> {

    private List<Appointment> appointmentList;
    private OnDetailsClickListener onDetailsClickListener;
    private OnStartClickListener onStartClickListener;

    public AppointmentAdapter(List<Appointment> appointmentList, OnDetailsClickListener onDetailsClickListener, OnStartClickListener onStartClickListener) {
        this.appointmentList = appointmentList != null ? appointmentList : new ArrayList<>();
        this.onDetailsClickListener = onDetailsClickListener;
        this.onStartClickListener = onStartClickListener;
    }

    public void updateAppointments(List<Appointment> newAppointments) {
        appointmentList.clear();
        appointmentList.addAll(newAppointments);
        notifyDataSetChanged();
    }

    @NotNull
    @Override
    public AppointmentViewHolder onCreateViewHolder(@NotNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_appointment, parent, false);
        return new AppointmentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NotNull AppointmentViewHolder holder, int position) {
        Appointment appointment = appointmentList.get(position);

        // Bind data to views
        holder.patientName.setText(appointment.getPatientName());
        holder.doctorName.setText(appointment.getDoctorName());
        holder.appointmentDateTime.setText(appointment.getAppointmentDate() + " " + appointment.getAppointmentTime());
        holder.appointmentStatus.setText(appointment.getAppointmentStatus());
        holder.bookedOn.setText(appointment.getCreatedAt());

        // Set click listeners for Details and Start buttons
        holder.detailsButton.setOnClickListener(v -> {
            if (onDetailsClickListener != null) {
                onDetailsClickListener.onDetailsClick(appointment);
            }
        });
        holder.startButton.setOnClickListener(v -> {
            if (onStartClickListener != null) {
                onStartClickListener.onStartClick(appointment);
            }
        });
    }

    @Override
    public int getItemCount() {
        return appointmentList.size();
    }

    public static class AppointmentViewHolder extends RecyclerView.ViewHolder {
        TextView patientName, doctorName, appointmentDateTime, appointmentStatus, bookedOn;
        Button detailsButton, startButton;

        public AppointmentViewHolder(@NotNull View itemView) {
            super(itemView);
            patientName = itemView.findViewById(R.id.patient_name);
            doctorName = itemView.findViewById(R.id.doctor_name);
            appointmentDateTime = itemView.findViewById(R.id.appointment_date_time);
            appointmentStatus = itemView.findViewById(R.id.appointment_status);
            bookedOn = itemView.findViewById(R.id.booked_on);
            detailsButton = itemView.findViewById(R.id.button_details);
            startButton = itemView.findViewById(R.id.button_start);
        }
    }

    public interface OnDetailsClickListener {
        void onDetailsClick(Appointment appointment);
    }

    public interface OnStartClickListener {
        void onStartClick(Appointment appointment);
    }
}