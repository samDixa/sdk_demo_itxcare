package com.lztek.api.demo;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class AppointmentDetails {

    // Main appointment fields
    @SerializedName("appointment_Id")
    private int appointmentId;

    @SerializedName("appointment_date")
    private String appointmentDate;

    @SerializedName("appointment_time")
    private String appointmentTime;

    @SerializedName("appointment_notification_type")
    private int appointmentNotificationType;

    @SerializedName("passcode")
    private String passcode;

    @SerializedName("appointment_status")
    private int appointmentStatus;

    @SerializedName("medical_history")
    private String medicalHistory;

    @SerializedName("medical_records")
    private String medicalRecords;

    @SerializedName("meeting_Id")
    private String meetingId;

    @SerializedName("meeting_url")
    private String meetingUrl;

    @SerializedName("patient_Id")
    private int patientId;

    @SerializedName("family_member_id")
    private Integer familyMemberId;

    @SerializedName("is_active")
    private boolean isActive;

    @SerializedName("createdAt")
    private String createdAt;

    @SerializedName("confirmedBy")
    private int confirmedBy;

    @SerializedName("patientFirstName")
    private String patientFirstName;

    @SerializedName("patientLastName")
    private String patientLastName;

    @SerializedName("gender")
    private String gender;

    @SerializedName("DOB")
    private String dob;

    @SerializedName("Abha_ID")
    private String abhaId;

    @SerializedName("email")
    private String email;

    @SerializedName("phone_number")
    private String phoneNumber;

    @SerializedName("NotificationMeidum")
    private String notificationMedium;

    @SerializedName("appointmentStatusName")
    private String appointmentStatusName;

    // Nested objects
    @SerializedName("Doctor")
    private Doctor doctor;

    @SerializedName("Patient")
    private Patient patient;

    @SerializedName("Paramedic")
    private Paramedic paramedic;

    @SerializedName("Reviews")
    private List<Review> reviews;

    // Getters and setters for each field
    // Example getter and setter
    public int getAppointmentId() { return appointmentId; }
    public void setAppointmentId(int appointmentId) { this.appointmentId = appointmentId; }

    public String getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(String appointmentDate) { this.appointmentDate = appointmentDate; }

    public String getAppointmentTime() { return appointmentTime; }
    public void setAppointmentTime(String appointmentTime) { this.appointmentTime = appointmentTime; }

    public int getAppointmentNotificationType() { return appointmentNotificationType; }
    public void setAppointmentNotificationType(int appointmentNotificationType) { this.appointmentNotificationType = appointmentNotificationType; }

    public String getPasscode() { return passcode; }
    public void setPasscode(String passcode) { this.passcode = passcode; }

    public int getAppointmentStatus() { return appointmentStatus; }
    public void setAppointmentStatus(int appointmentStatus) { this.appointmentStatus = appointmentStatus; }

    public String getMedicalHistory() { return medicalHistory; }
    public void setMedicalHistory(String medicalHistory) { this.medicalHistory = medicalHistory; }

    public String getMedicalRecords() { return medicalRecords; }
    public void setMedicalRecords(String medicalRecords) { this.medicalRecords = medicalRecords; }

    public String getMeetingId() { return meetingId; }
    public void setMeetingId(String meetingId) { this.meetingId = meetingId; }

    public String getMeetingUrl() { return meetingUrl; }
    public void setMeetingUrl(String meetingUrl) { this.meetingUrl = meetingUrl; }

    public int getPatientId() { return patientId; }
    public void setPatientId(int patientId) { this.patientId = patientId; }

    public Integer getFamilyMemberId() { return familyMemberId; }
    public void setFamilyMemberId(Integer familyMemberId) { this.familyMemberId = familyMemberId; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public int getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(int confirmedBy) { this.confirmedBy = confirmedBy; }

    public String getPatientFirstName() { return patientFirstName; }
    public void setPatientFirstName(String patientFirstName) { this.patientFirstName = patientFirstName; }

    public String getPatientLastName() { return patientLastName; }
    public void setPatientLastName(String patientLastName) { this.patientLastName = patientLastName; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getDob() { return dob; }
    public void setDob(String dob) { this.dob = dob; }

    public String getAbhaId() { return abhaId; }
    public void setAbhaId(String abhaId) { this.abhaId = abhaId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getNotificationMedium() { return notificationMedium; }
    public void setNotificationMedium(String notificationMedium) { this.notificationMedium = notificationMedium; }

    public String getAppointmentStatusName() { return appointmentStatusName; }
    public void setAppointmentStatusName(String appointmentStatusName) { this.appointmentStatusName = appointmentStatusName; }

    public Doctor getDoctor() { return doctor; }
    public void setDoctor(Doctor doctor) { this.doctor = doctor; }

    public Patient getPatient() { return patient; }
    public void setPatient(Patient patient) { this.patient = patient; }

    public Paramedic getParamedic() { return paramedic; }
    public void setParamedic(Paramedic paramedic) { this.paramedic = paramedic; }

    public List<Review> getReviews() { return reviews; }
    public void setReviews(List<Review> reviews) { this.reviews = reviews; }

    // Nested Doctor class
        public static class Doctor {
        @SerializedName("doctor_Id")
        private int doctorId;

        @SerializedName("email")
        private String email;

        @SerializedName("first_name")
        private String firstName;

        @SerializedName("last_name")
        private String lastName;

        @SerializedName("phone_number")
        private String phoneNumber;

        @SerializedName("specialization")
        private String specialization;

        @SerializedName("years_of_experience")
        private String yearsOfExperience;

        @SerializedName("physical_working_hours")
        private String physicalWorkingHours;

        @SerializedName("live_consultation")
        private String liveConsultationHours;

        // Getters and setters for Doctor fields
        public int getDoctorId() { return doctorId; }
        public void setDoctorId(int doctorId) { this.doctorId = doctorId; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }

        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }

        public String getPhoneNumber() { return phoneNumber; }
        public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

        public String getSpecialization() { return specialization; }
        public void setSpecialization(String specialization) { this.specialization = specialization; }

        public String getYearsOfExperience() { return yearsOfExperience; }
        public void setYearsOfExperience(String yearsOfExperience) { this.yearsOfExperience = yearsOfExperience; }

        public String getPhysicalWorkingHours() { return physicalWorkingHours; }
        public void setPhysicalWorkingHours(String physicalWorkingHours) { this.physicalWorkingHours = physicalWorkingHours; }

        public String getLiveConsultationHours() { return liveConsultationHours; }
        public void setLiveConsultationHours(String liveConsultationHours) { this.liveConsultationHours = liveConsultationHours; }

    }

    // Nested Patient class
    public static class Patient {
        @SerializedName("phone_number")
        private String phoneNumber;

        @SerializedName("address")
        private String address;

        public String getPhoneNumber() { return phoneNumber; }
        public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
    }

    // Nested Paramedic class
    public static class Paramedic {
        @SerializedName("address")
        private String address;

        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
    }

    // Optional Review class for handling reviews
    public static class Review {
        private int reviewId;
        private String reviewText;
        private int rating;

        public int getReviewId() { return reviewId; }
        public void setReviewId(int reviewId) { this.reviewId = reviewId; }

        public String getReviewText() { return reviewText; }
        public void setReviewText(String reviewText) { this.reviewText = reviewText; }

        public int getRating() { return rating; }
        public void setRating(int rating) { this.rating = rating; }
    }
}
