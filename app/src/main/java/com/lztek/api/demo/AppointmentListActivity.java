package com.lztek.api.demo;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.GridLayoutManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.google.gson.Gson;

import androidx.recyclerview.widget.GridLayoutManager;
public class AppointmentListActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private AppointmentAdapter adapter;
    private List<Appointment> appointmentList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set up the main layout
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        mainLayout.setPadding(16, 16, 16, 16);

        TextView appointmentInfo = new TextView(this);
        appointmentInfo.setText("Scheduled Appointments");
        appointmentInfo.setTextSize(18);
        appointmentInfo.setTypeface(null, Typeface.BOLD);
        mainLayout.addView(appointmentInfo);

        // Initialize the RecyclerView
        recyclerView = new RecyclerView(this);
        recyclerView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
//        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        recyclerView.setAdapter(adapter);
        mainLayout.addView(recyclerView);

        setContentView(mainLayout);

        // Initialize appointment list and adapter
        adapter = new AppointmentAdapter(appointmentList,
                appointment -> new FetchAppointmentDetailsTask(appointment.getAppointmentId(), details -> showDetailsPopup(details))
                        .execute(),

                appointment -> new FetchAppointmentDetailsTask(appointment.getAppointmentId(), details -> setCurrentAppointmentDetails(details))
                        .execute()
        );

        recyclerView.setAdapter(adapter);

        // Start fetching appointments from the API
        new FetchAppointmentsTask().execute();

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LOW_PROFILE
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        );
    }

    public interface OnAppointmentDetailsFetchedListener {
        void onFetched(AppointmentDetails appointmentDetails);
    }

    private void showDetailsPopup(AppointmentDetails appointmentDetails) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Appointment Details");

        // Create a ScrollView to hold the content
        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 24, 24, 24);

        // Appointment Details
        TextView appointmentInfo = new TextView(this);
        appointmentInfo.setText("Appointment Information");
        appointmentInfo.setTextSize(18);
        appointmentInfo.setTypeface(null, Typeface.BOLD);
        layout.addView(appointmentInfo);

        addDetailRow(layout, "Appointment ID:", String.valueOf(appointmentDetails.getAppointmentId()));
        addDetailRow(layout, "Date:", appointmentDetails.getAppointmentDate());
        addDetailRow(layout, "Time:", appointmentDetails.getAppointmentTime());

        // Patient Image Placeholder
        ImageView patientImage = new ImageView(this);
        patientImage.setImageResource(R.drawable.ic_patient); // Replace with actual placeholder drawable
        patientImage.setLayoutParams(new LinearLayout.LayoutParams(150, 150));
        patientImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        layout.addView(patientImage);

        // Patient Details
        TextView patientInfo = new TextView(this);
        patientInfo.setText("Patient Information");
        patientInfo.setTextSize(18);
        patientInfo.setTypeface(null, Typeface.BOLD);
        layout.addView(patientInfo);

        addDetailRow(layout, "Name:", appointmentDetails.getPatientFirstName() + " " + appointmentDetails.getPatientLastName());
        addDetailRow(layout, "Gender:", appointmentDetails.getGender());
        addDetailRow(layout, "DOB:", appointmentDetails.getDob());
        addDetailRow(layout, "ABHA ID:", appointmentDetails.getAbhaId());
        addDetailRow(layout, "Email:", appointmentDetails.getEmail());
        addDetailRow(layout, "Phone:", appointmentDetails.getPhoneNumber());

        // Patient Address
        if (appointmentDetails.getPatient() != null) {
            addDetailRow(layout, "Address:", appointmentDetails.getPatient().getAddress());
        }

        // Doctor Image Placeholder
        ImageView doctorImage = new ImageView(this);
        doctorImage.setImageResource(R.drawable.ic_doctor); // Replace with actual placeholder drawable
        doctorImage.setLayoutParams(new LinearLayout.LayoutParams(150, 150));
        doctorImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        layout.addView(doctorImage);

        // Doctor Details with null checks
        TextView doctorInfo = new TextView(this);
        doctorInfo.setText("Doctor Information");
        doctorInfo.setTextSize(18);
        doctorInfo.setTypeface(null, Typeface.BOLD);
        layout.addView(doctorInfo);

        if (appointmentDetails.getDoctor() != null) {
            addDetailRow(layout, "Name:", appointmentDetails.getDoctor().getFirstName() + " " + appointmentDetails.getDoctor().getLastName());
            addDetailRow(layout, "Specialization:", appointmentDetails.getDoctor().getSpecialization());
            addDetailRow(layout, "Email:", appointmentDetails.getDoctor().getEmail());
            addDetailRow(layout, "Phone:", appointmentDetails.getDoctor().getPhoneNumber());
        } else {
            addDetailRow(layout, "Doctor Info:", "Not Available");
        }

        // Add the layout to ScrollView and then to the builder
        scrollView.addView(layout);
        builder.setView(scrollView);

        // OK button to dismiss the dialog
        builder.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());
        builder.create().show();
    }

    // Helper method to add details as rows
    private void addDetailRow(LinearLayout layout, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(8, 8, 8, 8);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTypeface(null, Typeface.BOLD);
        labelView.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(labelView);

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 2));
        row.addView(valueView);

        layout.addView(row);

    }


    // Method to handle start button action
    private void setCurrentAppointmentDetails(AppointmentDetails details) {
        GlobalVars.setCurrentAppointmentDetails(details);
        Toast.makeText(this, "Appointment details ready to start", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, ConsultationActivity.class);
        startActivity(intent);
    }

    // AsyncTask to fetch appointments from the API
    private class FetchAppointmentsTask extends AsyncTask<Void, Void, String> {

        @Override
        protected String doInBackground(Void... voids) {
            try {
                String apiUrl = Constants.APPOINTMENT_LIST.replace("{id}", "10") + "?sortBy=date&status=Upcoming";
                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                // Set headers
                conn.setRequestProperty("Authorization", "Bearer " + GlobalVars.getAccessToken()); // Using global access token
                conn.setRequestProperty("App-Tenant", GlobalVars.getClientId());

                // Read the response
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                reader.close();
                return response.toString();

            } catch (Exception e) {
                Log.e("AppointmentListActivity", "Error fetching appointments", e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(String response) {
            if (response != null) {
                parseAndLoadAppointments(response);
            } else {
                Toast.makeText(AppointmentListActivity.this, "Failed to fetch appointments", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Method to parse the API response and populate the RecyclerView
    private void parseAndLoadAppointments(String jsonResponse) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);

            // Check if the response is successful
            if (jsonObject.optBoolean("success", false)) {
                JSONArray dataArray = jsonObject.optJSONArray("data");

                if (dataArray != null) {
                    for (int i = 0; i < dataArray.length(); i++) {
                        JSONObject jsonAppointment = dataArray.optJSONObject(i);

                        if (jsonAppointment != null) {
                            // Safely parse fields with default values if they are missing or null
                            int appointmentId = jsonAppointment.optInt("appointment_Id", -1);
                            int doctorId = jsonAppointment.optInt("doctor_Id", -1);
                            int patientId = jsonAppointment.optInt("patient_Id", -1);
                            String patientName = jsonAppointment.optString("patientName", "Unknown");
                            String paramedicName = jsonAppointment.optString("paramedicName", "Unknown");
                            String doctorName = jsonAppointment.optString("doctorName", "Unknown");
                            String appointmentDate = String.valueOf(jsonAppointment.optInt("appointment_date", 0)); // Convert date to String
                            String appointmentTime = jsonAppointment.optString("appointment_time", "N/A");
                            String appointmentStatus = jsonAppointment.optString("appointment_status", "Pending");
                            String notificationType = jsonAppointment.optString("appointment_notification_type", "N/A");
                            String meetingId = jsonAppointment.optString("meeting_Id", "N/A");
                            String meetingUrl = jsonAppointment.optString("meeting_url", "N/A");
                            String passcode = jsonAppointment.optString("passcode", "N/A");
                            String createdAt = jsonAppointment.optString("createdAt", "N/A");
                            boolean isActive = jsonAppointment.optBoolean("is_active", true);

                            // Create an Appointment object with parsed data
                            Appointment appointment = new Appointment(
                                    appointmentId,
                                    doctorId,
                                    patientId,
                                    patientName,
                                    paramedicName,
                                    doctorName,
                                    appointmentDate,
                                    appointmentTime,
                                    appointmentStatus,
                                    notificationType,
                                    meetingId,
                                    meetingUrl,
                                    passcode,
                                    createdAt,
                                    isActive
                            );

                            // Add to the appointment list
                            appointmentList.add(appointment);
                        }
                    }

                    // Notify the adapter that data has changed
                    adapter.notifyDataSetChanged();
                }

            } else {
                String message = jsonObject.optString("message", "An error occurred");
                Toast.makeText(this, "Error: " + message, Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {
            Log.e("AppointmentListActivity", "Error parsing JSON response", e);
            Toast.makeText(this, "Failed to parse appointments", Toast.LENGTH_SHORT).show();
        }
    }


    public class FetchAppointmentDetailsTask extends AsyncTask<Void, Void, AppointmentDetails> {

        private int appointmentId;
        private Consumer<AppointmentDetails> callback;

        public FetchAppointmentDetailsTask(int appointmentId, Consumer<AppointmentDetails> callback) {
            this.appointmentId = appointmentId;
            this.callback = callback;
        }

        @Override
        protected AppointmentDetails doInBackground(Void... voids) {
            try {
                String apiUrl = Constants.APPOINTMENT_DETAILS.replace("{id}", String.valueOf(appointmentId)); // Replace with your API URL
                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + GlobalVars.getAccessToken());
                conn.setRequestProperty("App-Tenant", GlobalVars.getClientId());

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                // Log the raw JSON response
//                Log.d("FetchAppointmentDetailsTask", "Raw JSON response: " + response.toString());

                try {
                    JSONObject jsonResponse = new JSONObject(response.toString());
                    return parseAppointmentDetails(jsonResponse);
                } catch (JSONException e) {
                    Log.e("FetchAppointmentDetailsTask", "Error parsing JSON", e);
                    return null;
                }


            } catch (Exception e) {
                Log.e("FetchAppointmentDetailsTask", "Error fetching appointment details", e);
                return null;
            }
        }

        private AppointmentDetails parseAppointmentDetails(JSONObject jsonObject) throws JSONException {
            AppointmentDetails details = new AppointmentDetails();

            // Default values for fields that can be null
            int defaultAppointmentId = -1;
            String defaultDate = "N/A";
            String defaultTime = "N/A";
            int defaultStatus = 0;
            int defaultNotificationType = 0;
            String defaultName = "Unknown";
            String defaultPhone = "Not provided";
            String defaultEmail = "Not provided";
            String defaultGender = "Not specified";
            String defaultAbhaId = "N/A";
            String defaultDob = "N/A";
            String defaultAppointmentStatusName = "Pending";
            String defaultAddress = "N/A";
            String defaultSpecialization = "General";

            // Step 1: Check if "data" is present
            if (!jsonObject.has("data") || jsonObject.isNull("data")) {
                Log.e("parseAppointmentDetails", "'data' field is missing in the JSON response.");
                return null;
            }

            // Parse "data" object
            JSONObject dataObject = jsonObject.getJSONObject("data");

            // Step 2: Parse main fields with default handling
            details.setAppointmentId(dataObject.optInt("appointment_Id", defaultAppointmentId));
            details.setAppointmentDate(dataObject.optString("appointment_date", defaultDate));
            details.setAppointmentTime(dataObject.optString("appointment_time", defaultTime));
            details.setAppointmentNotificationType(dataObject.optInt("appointment_notification_type", defaultNotificationType));
            details.setPasscode(dataObject.optString("passcode", ""));
            details.setAppointmentStatus(dataObject.optInt("appointment_status", defaultStatus));
            details.setMedicalHistory(dataObject.optString("medical_history", ""));
            details.setMedicalRecords(dataObject.optString("medical_records", null));
            details.setMeetingId(dataObject.optString("meeting_Id", ""));
            details.setMeetingUrl(dataObject.optString("meeting_url", ""));
            details.setPatientId(dataObject.optInt("patient_Id", defaultAppointmentId));
            details.setFamilyMemberId(dataObject.optInt("family_member_id", -1));
            details.setActive(dataObject.optBoolean("is_active", true));
            details.setCreatedAt(dataObject.optString("createdAt", defaultDate));
            details.setConfirmedBy(dataObject.optInt("confirmedBy", 0));
            details.setPatientFirstName(dataObject.optString("patientFirstName", defaultName));
            details.setPatientLastName(dataObject.optString("patientLastName", defaultName));
            details.setGender(dataObject.optString("gender", defaultGender));
            details.setDob(dataObject.optString("DOB", defaultDob));
            details.setAbhaId(dataObject.optString("Abha_ID", defaultAbhaId));
            details.setEmail(dataObject.optString("email", defaultEmail));
            details.setPhoneNumber(dataObject.optString("phone_number", defaultPhone));
            details.setNotificationMedium(dataObject.optString("NotificationMeidum", "None"));
            details.setAppointmentStatusName(dataObject.optString("appointmentStatusName", defaultAppointmentStatusName));

            // Step 3: Parse nested Doctor object with defaults
            if (dataObject.has("Doctor") && !dataObject.isNull("Doctor")) {
                JSONObject doctorJson = dataObject.getJSONObject("Doctor");
                AppointmentDetails.Doctor doctor = new AppointmentDetails.Doctor();

                doctor.setDoctorId(doctorJson.optInt("doctor_Id", defaultAppointmentId));
                doctor.setEmail(doctorJson.optString("email", defaultEmail));
                doctor.setFirstName(doctorJson.optString("first_name", defaultName));
                doctor.setLastName(doctorJson.optString("last_name", defaultName));
                doctor.setPhoneNumber(doctorJson.optString("phone_number", defaultPhone));
                doctor.setSpecialization(doctorJson.optString("specialization", defaultSpecialization));
                doctor.setYearsOfExperience(doctorJson.optString("years_of_experience", "0"));
                doctor.setPhysicalWorkingHours(doctorJson.optString("physical_working_hours", "{}"));
                doctor.setLiveConsultationHours(doctorJson.optString("live_consultation", "{}"));

                details.setDoctor(doctor);
            }

            // Step 4: Parse nested Patient object with defaults
            if (dataObject.has("Patient") && !dataObject.isNull("Patient")) {
                JSONObject patientJson = dataObject.getJSONObject("Patient");
                AppointmentDetails.Patient patient = new AppointmentDetails.Patient();

                patient.setPhoneNumber(patientJson.optString("phone_number", defaultPhone));
                patient.setAddress(patientJson.optString("address", defaultAddress));

                details.setPatient(patient);
            }

            // Step 5: Parse nested Paramedic object with defaults
            if (dataObject.has("Paramedic") && !dataObject.isNull("Paramedic")) {
                JSONObject paramedicJson = dataObject.getJSONObject("Paramedic");
                AppointmentDetails.Paramedic paramedic = new AppointmentDetails.Paramedic();

                paramedic.setAddress(paramedicJson.optString("address", defaultAddress));

                details.setParamedic(paramedic);
            }

            // Step 6: Parse Reviews array if present
            if (dataObject.has("Reviews") && !dataObject.isNull("Reviews")) {
                JSONArray reviewsArray = dataObject.getJSONArray("Reviews");
                List<AppointmentDetails.Review> reviews = new ArrayList<>();

                for (int i = 0; i < reviewsArray.length(); i++) {
                    JSONObject reviewJson = reviewsArray.getJSONObject(i);
                    AppointmentDetails.Review review = new AppointmentDetails.Review();

                    review.setReviewId(reviewJson.optInt("reviewId", 0));
                    review.setReviewText(reviewJson.optString("reviewText", "No review provided"));
                    review.setRating(reviewJson.optInt("rating", 0));

                    reviews.add(review);
                }

                details.setReviews(reviews);
            }

            return details;
        }

        @Override
        protected void onPostExecute(AppointmentDetails appointmentDetails) {
            if (appointmentDetails != null) {
                Log.d("FetchAppointmentDetailsTask", "Parsed AppointmentDetails: " + appointmentDetails.toString());
                // Continue with showing details in popup or handling as needed
                if (callback != null) {
                    callback.accept(appointmentDetails);
                }
            } else {
                Log.d("FetchAppointmentDetailsTask", "AppointmentDetails parsing failed.");
            }
        }
    }
}
