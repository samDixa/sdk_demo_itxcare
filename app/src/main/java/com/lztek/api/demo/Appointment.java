package com.lztek.api.demo;

public class Appointment {
    private int appointmentId;
    private int doctorId;
    private int patientId;
    private String patientName;
    private String paramedicName;
    private String doctorName;
    private String appointmentDate;
    private String appointmentTime;
    private String appointmentStatus;
    private String appointmentNotificationType;
    private String meetingId;
    private String meetingUrl;
    private String passcode;
    private String createdAt;
    private boolean isActive;

    // Updated Constructor with additional fields
    public Appointment(int appointmentId, int doctorId, int patientId, String patientName, String paramedicName,
                       String doctorName, String appointmentDate, String appointmentTime, String appointmentStatus,
                       String appointmentNotificationType, String meetingId, String meetingUrl, String passcode,
                       String createdAt, boolean isActive) {
        this.appointmentId = appointmentId;
        this.doctorId = doctorId;
        this.patientId = patientId;
        this.patientName = patientName;
        this.paramedicName = paramedicName;
        this.doctorName = doctorName;
        this.appointmentDate = appointmentDate;
        this.appointmentTime = appointmentTime;
        this.appointmentStatus = appointmentStatus;
        this.appointmentNotificationType = appointmentNotificationType;
        this.meetingId = meetingId;
        this.meetingUrl = meetingUrl;
        this.passcode = passcode;
        this.createdAt = createdAt;
        this.isActive = isActive;
    }

    // Getters for each field
    public int getAppointmentId() { return appointmentId; }
    public int getDoctorId() { return doctorId; }
    public int getPatientId() { return patientId; }
    public String getPatientName() { return patientName; }
    public String getParamedicName() { return paramedicName; }
    public String getDoctorName() { return doctorName; }
    public String getAppointmentDate() { return appointmentDate; }
    public String getAppointmentTime() { return appointmentTime; }
    public String getAppointmentStatus() { return appointmentStatus; }
    public String getAppointmentNotificationType() { return appointmentNotificationType; }
    public String getMeetingId() { return meetingId; }
    public String getMeetingUrl() { return meetingUrl; }
    public String getPasscode() { return passcode; }
    public String getCreatedAt() { return createdAt; }
    public boolean isActive() { return isActive; }
}
