//package com.lztek.api.demo;
//
//import android.content.Intent;
//import android.graphics.Typeface;
//import android.os.AsyncTask;
//import android.os.Bundle;
//import android.util.Log;
//import android.view.Gravity;
//import android.view.LayoutInflater;
//import android.view.View;
//import android.widget.Button;
//import android.widget.ImageView;
//import android.widget.LinearLayout;
//import android.widget.ProgressBar;
//import android.widget.ScrollView;
//import android.widget.TableLayout;
//import android.widget.TextView;
//import android.widget.Toast;
//import androidx.appcompat.app.AlertDialog;
//import androidx.appcompat.app.AppCompatActivity;
//import org.json.JSONArray;
//import org.json.JSONObject;
//import java.io.BufferedReader;
//import java.io.InputStreamReader;
//import java.net.HttpURLConnection;
//import java.net.URL;
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.Comparator;
//import java.util.List;
//import java.util.function.Consumer;
//
//public class AppointmentListActivity extends AppCompatActivity {
//
//    private TableLayout dataRowsContainer;
//    private List<Appointment> appointmentList = new ArrayList<>();
//    private int currentPage = 1;
//    private int itemsPerPage = 10;
//    private int totalPages = 1;
//    private LinearLayout pageNumbersLayout;
//    private ProgressBar loadingProgressBar;
//
//
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_appointment_list);
//
//        dataRowsContainer = findViewById(R.id.data_rows_container);
//        pageNumbersLayout = findViewById(R.id.page_numbers_layout);
//        loadingProgressBar = findViewById(R.id.loading_progress_bar);
//
//        // Set up sort buttons
//        findViewById(R.id.sort_sr_no).setOnClickListener(v -> sortAppointments("sr_no"));
//        findViewById(R.id.sort_patient).setOnClickListener(v -> sortAppointments("patient"));
//        findViewById(R.id.sort_doctor).setOnClickListener(v -> sortAppointments("doctor"));
//        findViewById(R.id.sort_date_time).setOnClickListener(v -> sortAppointments("date"));
//        findViewById(R.id.sort_status).setOnClickListener(v -> sortAppointments("status"));
//        findViewById(R.id.sort_booked_on).setOnClickListener(v -> sortAppointments("booked_on"));
//
//        // Set up pagination buttons
//        findViewById(R.id.btn_previous).setOnClickListener(v -> loadPreviousPage());
//        findViewById(R.id.btn_next).setOnClickListener(v -> loadNextPage());
//
//        // Fetch initial appointments
//        new FetchAppointmentsTask().execute();
//
////
//        getWindow().getDecorView().setSystemUiVisibility(
//                View.SYSTEM_UI_FLAG_LOW_PROFILE
//                        | View.SYSTEM_UI_FLAG_FULLSCREEN
//                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
//                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
//                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
//                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
//        );
//    }
//
//    private void showDetailsPopup(AppointmentDetails appointmentDetails) {
//        Log.d("AppointmentListActivity", "Showing details popup for appointment ID: " + appointmentDetails.getAppointmentId());
//        AlertDialog.Builder builder = new AlertDialog.Builder(this);
//        builder.setTitle("Appointment Details");
//
//        ScrollView scrollView = new ScrollView(this);
//        LinearLayout layout = new LinearLayout(this);
//        layout.setOrientation(LinearLayout.VERTICAL);
//        layout.setPadding(24, 24, 24, 24);
//
//        TextView appointmentInfo = new TextView(this);
//        appointmentInfo.setText("Appointment Information");
//        appointmentInfo.setTextSize(18);
//        appointmentInfo.setTypeface(null, Typeface.BOLD);
//        layout.addView(appointmentInfo);
//
//        addDetailRow(layout, "Appointment ID:", String.valueOf(appointmentDetails.getAppointmentId()));
//        addDetailRow(layout, "Date:", appointmentDetails.getAppointmentDate());
//        addDetailRow(layout, "Time:", appointmentDetails.getAppointmentTime());
//
//        ImageView patientImage = new ImageView(this);
//        patientImage.setImageResource(R.drawable.ic_patient);
//        patientImage.setLayoutParams(new LinearLayout.LayoutParams(150, 150));
//        patientImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
//        layout.addView(patientImage);
//
//        TextView patientInfo = new TextView(this);
//        patientInfo.setText("Patient Information");
//        patientInfo.setTextSize(18);
//        patientInfo.setTypeface(null, Typeface.BOLD);
//        layout.addView(patientInfo);
//
//        addDetailRow(layout, "Name:", appointmentDetails.getPatientFirstName() + " " + appointmentDetails.getPatientLastName());
//        addDetailRow(layout, "Gender:", appointmentDetails.getGender());
//        addDetailRow(layout, "DOB:", appointmentDetails.getDob());
//        addDetailRow(layout, "ABHA ID:", appointmentDetails.getAbhaId());
//        addDetailRow(layout, "Email:", appointmentDetails.getEmail());
//        addDetailRow(layout, "Phone:", appointmentDetails.getPhoneNumber());
//
//        if (appointmentDetails.getPatient() != null) {
//            addDetailRow(layout, "Address:", appointmentDetails.getPatient().getAddress());
//        }
//
//        ImageView doctorImage = new ImageView(this);
//        doctorImage.setImageResource(R.drawable.ic_doctor);
//        doctorImage.setLayoutParams(new LinearLayout.LayoutParams(150, 150));
//        doctorImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
//        layout.addView(doctorImage);
//
//        TextView doctorInfo = new TextView(this);
//        doctorInfo.setText("Doctor Information");
//        doctorInfo.setTextSize(18);
//        doctorInfo.setTypeface(null, Typeface.BOLD);
//        layout.addView(doctorInfo);
//
//        if (appointmentDetails.getDoctor() != null) {
//            addDetailRow(layout, "Name:", appointmentDetails.getDoctor().getFirstName() + " " + appointmentDetails.getDoctor().getLastName());
//            addDetailRow(layout, "Specialization:", appointmentDetails.getDoctor().getSpecialization());
//            addDetailRow(layout, "Email:", appointmentDetails.getDoctor().getEmail());
//            addDetailRow(layout, "Phone:", appointmentDetails.getDoctor().getPhoneNumber());
//        } else {
//            addDetailRow(layout, "Doctor Info:", "Not Available");
//        }
//
//        scrollView.addView(layout);
//        builder.setView(scrollView);
//        builder.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());
//        builder.create().show();
//    }
//
//    private void addDetailRow(LinearLayout layout, String label, String value) {
//        LinearLayout row = new LinearLayout(this);
//        row.setOrientation(LinearLayout.HORIZONTAL);
//        row.setPadding(8, 8, 8, 8);
//
//        TextView labelView = new TextView(this);
//        labelView.setText(label);
//        labelView.setTypeface(null, Typeface.BOLD);
//        labelView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
//        row.addView(labelView);
//
//        TextView valueView = new TextView(this);
//        valueView.setText(value);
//        valueView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2));
//        row.addView(valueView);
//
//        layout.addView(row);
//    }
//
//    private void setCurrentAppointmentDetails(AppointmentDetails details) {
//        GlobalVars.setCurrentAppointmentDetails(details);
//        Toast.makeText(this, "Appointment details ready to start", Toast.LENGTH_SHORT).show();
//        Intent intent = new Intent(this, ConsultationActivity.class);
//        startActivity(intent);
//    }
//
//    private void addAppointmentRow(Appointment appointment) {
//        LayoutInflater inflater = LayoutInflater.from(this);
//        View rowView = inflater.inflate(R.layout.item_appointment, null);
//
//        TextView srNo = rowView.findViewById(R.id.sr_no);
//        TextView patientName = rowView.findViewById(R.id.patient_name);
//        TextView doctorName = rowView.findViewById(R.id.doctor_name);
//        TextView appointmentDateTime = rowView.findViewById(R.id.appointment_date_time);
//        TextView appointmentStatus = rowView.findViewById(R.id.appointment_status);
//        TextView bookedOn = rowView.findViewById(R.id.booked_on);
//        Button detailsButton = rowView.findViewById(R.id.button_details);
//        Button startButton = rowView.findViewById(R.id.button_start);
//
//        int startIndex = (currentPage - 1) * itemsPerPage;
//        int rowNumber = startIndex + dataRowsContainer.getChildCount() + 1;
//        srNo.setText(String.valueOf(rowNumber));
//
//        patientName.setText(appointment.getPatientName());
//        doctorName.setText(appointment.getDoctorName());
//        appointmentDateTime.setText(appointment.getAppointmentDate() + " " + appointment.getAppointmentTime());
//        appointmentStatus.setText(appointment.getAppointmentStatus());
//        bookedOn.setText(appointment.getCreatedAt());
//
//        detailsButton.setOnClickListener(v -> {
//            Log.d("AppointmentListActivity", "Details button clicked for appointment ID: " + appointment.getAppointmentId());
//            new FetchAppointmentDetailsTask(appointment.getAppointmentId(), details -> showDetailsPopup(details)).execute();
//        });
//        startButton.setOnClickListener(v -> {
//            Log.d("AppointmentListActivity", "Start button clicked for appointment ID: " + appointment.getAppointmentId());
//            new FetchAppointmentDetailsTask(appointment.getAppointmentId(), details -> setCurrentAppointmentDetails(details)).execute();
//        });
//
//        dataRowsContainer.addView(rowView);
//    }
//
//    private void loadPage(int page) {
//        currentPage = page;
//        dataRowsContainer.removeAllViews();
//        int start = (currentPage - 1) * itemsPerPage;
//        int end = Math.min(start + itemsPerPage, appointmentList.size());
//        for (int i = start; i < end; i++) {
//            addAppointmentRow(appointmentList.get(i));
//        }
//        updatePaginationButtons();
//    }
//
//    private void loadPreviousPage() {
//        if (currentPage > 1) {
//            loadPage(currentPage - 1);
//        }
//    }
//
//    private void loadNextPage() {
//        if (currentPage < totalPages) {
//            loadPage(currentPage + 1);
//        }
//    }
//
//    private void updatePaginationButtons() {
//        pageNumbersLayout.removeAllViews();
//        totalPages = (int) Math.ceil((double) appointmentList.size() / itemsPerPage);
//        for (int i = 1; i <= totalPages; i++) {
//            Button pageButton = new Button(this);
//            pageButton.setText(String.valueOf(i));
//            pageButton.setTextColor(currentPage == i ? 0xFF000000 : 0xFF2196F3);
//            pageButton.setBackgroundTintList(null);
//            final int pageNum = i;
//            pageButton.setOnClickListener(v -> loadPage(pageNum));
//            pageNumbersLayout.addView(pageButton);
//        }
//        // Enable/Disable Previous and Next buttons
//        Button prevButton = findViewById(R.id.btn_previous);
//        Button nextButton = findViewById(R.id.btn_next);
//        prevButton.setEnabled(currentPage > 1);
//        nextButton.setEnabled(currentPage < totalPages);
//    }
//
//    private void sortAppointments(String column) {
//        Comparator<Appointment> comparator;
//        switch (column) {
//            case "sr_no":
//                // Sort by index (not a real field, so we'll use list index as a proxy)
//                comparator = (a, b) -> Integer.compare(appointmentList.indexOf(a), appointmentList.indexOf(b));
//                break;
//            case "patient":
//                comparator = (a, b) -> a.getPatientName().compareToIgnoreCase(b.getPatientName());
//                break;
//            case "doctor":
//                comparator = (a, b) -> a.getDoctorName().compareToIgnoreCase(b.getDoctorName());
//                break;
//            case "date":
//                comparator = (a, b) -> a.getAppointmentDate().compareTo(b.getAppointmentDate());
//                break;
//            case "status":
//                comparator = (a, b) -> a.getAppointmentStatus().compareToIgnoreCase(b.getAppointmentStatus());
//                break;
//            case "booked_on":
//                comparator = (a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt());
//                break;
//            default:
//                return;
//        }
//        Collections.sort(appointmentList, comparator);
//        loadPage(currentPage);
//    }
//
//    private class FetchAppointmentsTask extends AsyncTask<Void, Void, String> {
//        @Override
//        protected void onPreExecute() {
//            super.onPreExecute();
//            loadingProgressBar.setVisibility(View.VISIBLE);
//        }
//
//        @Override
//        protected String doInBackground(Void... voids) {
//            try {
//                String paramedicId = String.valueOf(GlobalVars.getParamedicId());
//                String apiUrl = Constants.APPOINTMENT_LIST.replace("{id}", paramedicId) + "?sortBy=date&status=Upcoming&limit=10";
//                URL url = new URL(apiUrl);
//                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
//                conn.setRequestMethod("GET");
//                conn.setRequestProperty("Authorization", "Bearer " + GlobalVars.getAccessToken());
//                conn.setRequestProperty("App-Tenant", GlobalVars.getClientId());
//
//                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
//                StringBuilder response = new StringBuilder();
//                String line;
//                while ((line = reader.readLine()) != null) {
//                    response.append(line);
//                }
//                reader.close();
//                return response.toString();
//            } catch (Exception e) {
//                Log.e("AppointmentListActivity", "Error fetching appointments", e);
//                return null;
//            }
//        }
//
//        @Override
//        protected void onPostExecute(String response) {
//            loadingProgressBar.setVisibility(View.GONE);
//            if (response != null) {
//                parseAndLoadAppointments(response);
//            } else {
//                Toast.makeText(AppointmentListActivity.this, "Failed to fetch appointments", Toast.LENGTH_SHORT).show();
//            }
//        }
//    }
//
//    private void parseAndLoadAppointments(String jsonResponse) {
//        try {
//            JSONObject jsonObject = new JSONObject(jsonResponse);
//            if (jsonObject.optBoolean("success", false)) {
//                JSONArray dataArray = jsonObject.optJSONArray("data");
//                if (dataArray != null) {
//                    appointmentList.clear();
//                    for (int i = 0; i < dataArray.length(); i++) {
//                        JSONObject jsonAppointment = dataArray.optJSONObject(i);
//                        if (jsonAppointment != null) {
//                            int appointmentId = jsonAppointment.optInt("appointment_Id", -1);
//                            int doctorId = jsonAppointment.optInt("doctor_Id", -1);
//                            int patientId = jsonAppointment.optInt("patient_Id", -1);
//                            String patientName = jsonAppointment.optString("patientName", "Unknown");
//                            String paramedicName = jsonAppointment.optString("paramedicName", "Unknown");
//                            String doctorName = jsonAppointment.optString("doctorName", "Unknown");
//                            String appointmentDate = String.valueOf(jsonAppointment.optInt("appointment_date", 0));
//                            String appointmentTime = jsonAppointment.optString("appointment_time", "N/A");
//                            String appointmentStatus = jsonAppointment.optString("appointment_status", "Pending");
//                            String notificationType = jsonAppointment.optString("appointment_notification_type", "N/A");
//                            String meetingId = jsonAppointment.optString("meeting_Id", "N/A");
//                            String meetingUrl = jsonAppointment.optString("meeting_url", "N/A");
//                            String passcode = jsonAppointment.optString("passcode", "N/A");
//                            String createdAt = jsonAppointment.optString("createdAt", "N/A");
//                            boolean isActive = jsonAppointment.optBoolean("is_active", true);
//
//                            Appointment appointment = new Appointment(
//                                    appointmentId, doctorId, patientId, patientName, paramedicName,
//                                    doctorName, appointmentDate, appointmentTime, appointmentStatus,
//                                    notificationType, meetingId, meetingUrl, passcode, createdAt, isActive
//                            );
//                            appointmentList.add(appointment);
//                        }
//                    }
//                    loadPage(1); // Load the first page
//                }
//            } else {
//                String message = jsonObject.optString("message", "An error occurred");
//                Toast.makeText(this, "Error: " + message, Toast.LENGTH_SHORT).show();
//            }
//        } catch (Exception e) {
//            Log.e("AppointmentListActivity", "Error parsing JSON response", e);
//            Toast.makeText(this, "Failed to parse appointments", Toast.LENGTH_SHORT).show();
//        }
//    }
//
//    public class FetchAppointmentDetailsTask extends AsyncTask<Void, Void, AppointmentDetails> {
//        private int appointmentId;
//        private Consumer<AppointmentDetails> callback;
//
//        public FetchAppointmentDetailsTask(int appointmentId, Consumer<AppointmentDetails> callback) {
//            this.appointmentId = appointmentId;
//            this.callback = callback;
//        }
//
//        @Override
//        protected void onPreExecute() {
//            super.onPreExecute();
//            loadingProgressBar.setVisibility(View.VISIBLE);
//        }
//
//        @Override
//        protected AppointmentDetails doInBackground(Void... voids) {
//            try {
//                String apiUrl = Constants.APPOINTMENT_DETAILS.replace("{id}", String.valueOf(appointmentId));
//                Log.d("FetchAppointmentDetailsTask", "API URL: " + apiUrl);
//                URL url = new URL(apiUrl);
//                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
//                conn.setRequestMethod("GET");
//                conn.setRequestProperty("Authorization", "Bearer " + GlobalVars.getAccessToken());
//                conn.setRequestProperty("App-Tenant", GlobalVars.getClientId());
//
//                int responseCode = conn.getResponseCode();
//                Log.d("FetchAppointmentDetailsTask", "Response Code: " + responseCode);
//
//                BufferedReader reader;
//                if (responseCode >= 400) {
//                    reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
//                } else {
//                    reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
//                }
//
//                StringBuilder response = new StringBuilder();
//                String line;
//                while ((line = reader.readLine()) != null) {
//                    response.append(line);
//                }
//                reader.close();
//                Log.d("FetchAppointmentDetailsTask", "Raw JSON response: " + response.toString());
//
//                JSONObject jsonResponse = new JSONObject(response.toString());
//                return parseAppointmentDetails(jsonResponse);
//
//            } catch (Exception e) {
//                Log.e("FetchAppointmentDetailsTask", "Error fetching appointment details", e);
//                return null;
//            }
//        }
//
//        @Override
//        protected void onPostExecute(AppointmentDetails appointmentDetails) {
//            loadingProgressBar.setVisibility(View.GONE);
//            if (appointmentDetails != null) {
//                Log.d("FetchAppointmentDetailsTask", "Parsed AppointmentDetails: " + appointmentDetails.toString());
//                if (callback != null) {
//                    callback.accept(appointmentDetails);
//                }
//            } else {
//                Log.e("FetchAppointmentDetailsTask", "AppointmentDetails is null");
//                Toast.makeText(AppointmentListActivity.this, "Failed to load appointment details", Toast.LENGTH_SHORT).show();
//            }
//        }
//    }
//
//    private AppointmentDetails parseAppointmentDetails(JSONObject jsonResponse) {
//        try {
//            JSONObject data = jsonResponse.getJSONObject("data");
//            AppointmentDetails details = new AppointmentDetails();
//
//            details.setAppointmentId(data.optInt("appointment_Id", -1));
//            details.setAppointmentDate(data.optString("appointment_date", ""));
//            details.setAppointmentTime(data.optString("appointment_time", ""));
//            details.setAppointmentNotificationType(data.optInt("appointment_notification_type", 0));
//            details.setPasscode(data.optString("passcode", ""));
//            details.setAppointmentStatus(data.optInt("appointment_status", 0));
//            details.setMedicalHistory(data.optString("medical_history", ""));
//            details.setMedicalRecords(data.optString("medical_records", ""));
//            details.setMeetingId(data.optString("meeting_Id", ""));
//            details.setMeetingUrl(data.optString("meeting_url", ""));
//            details.setPatientId(data.optInt("patient_Id", -1));
//            details.setFamilyMemberId(data.optInt("family_member_id", -1));
//            details.setActive(data.optBoolean("is_active", true));
//            details.setCreatedAt(data.optString("createdAt", ""));
//            details.setConfirmedBy(data.optInt("confirmedBy", -1));
//            details.setPatientFirstName(data.optString("patientFirstName", ""));
//            details.setPatientLastName(data.optString("patientLastName", ""));
//            details.setGender(data.optString("gender", ""));
//            details.setDob(data.optString("DOB", ""));
//            details.setAbhaId(data.optString("Abha_ID", ""));
//            details.setEmail(data.optString("email", ""));
//            details.setPhoneNumber(data.optString("phone_number", ""));
//            details.setNotificationMedium(data.optString("NotificationMeidum", ""));
//            details.setAppointmentStatusName(data.optString("appointmentStatusName", ""));
//
//            if (data.has("Doctor")) {
//                JSONObject doctorJson = data.getJSONObject("Doctor");
//                AppointmentDetails.Doctor doctor = new AppointmentDetails.Doctor();
//                doctor.setDoctorId(doctorJson.optInt("doctor_Id", -1));
//                doctor.setEmail(doctorJson.optString("email", ""));
//                doctor.setFirstName(doctorJson.optString("first_name", ""));
//                doctor.setLastName(doctorJson.optString("last_name", ""));
//                doctor.setPhoneNumber(doctorJson.optString("phone_number", ""));
//                doctor.setSpecialization(doctorJson.optString("specialization", ""));
//                doctor.setYearsOfExperience(doctorJson.optString("years_of_experience", ""));
//                doctor.setPhysicalWorkingHours(doctorJson.optString("physical_working_hours", ""));
//                doctor.setLiveConsultationHours(doctorJson.optString("live_consultation", ""));
//                details.setDoctor(doctor);
//            }
//
//            if (data.has("Patient")) {
//                JSONObject patientJson = data.getJSONObject("Patient");
//                AppointmentDetails.Patient patient = new AppointmentDetails.Patient();
//                patient.setPhoneNumber(patientJson.optString("phone_number", ""));
//                patient.setAddress(patientJson.optString("address", ""));
//                details.setPatient(patient);
//            }
//
//            if (data.has("Paramedic")) {
//                JSONObject paramedicJson = data.getJSONObject("Paramedic");
//                AppointmentDetails.Paramedic paramedic = new AppointmentDetails.Paramedic();
//                paramedic.setAddress(paramedicJson.optString("address", ""));
//                details.setParamedic(paramedic);
//            }
//
//            if (data.has("Reviews")) {
//                JSONArray reviewsArray = data.getJSONArray("Reviews");
//                List<AppointmentDetails.Review> reviews = new ArrayList<>();
//                for (int i = 0; i < reviewsArray.length(); i++) {
//                    JSONObject reviewJson = reviewsArray.getJSONObject(i);
//                    AppointmentDetails.Review review = new AppointmentDetails.Review();
//                    review.setReviewId(reviewJson.optInt("reviewId", -1));
//                    review.setReviewText(reviewJson.optString("reviewText", ""));
//                    review.setRating(reviewJson.optInt("rating", 0));
//                    reviews.add(review);
//                }
//                details.setReviews(reviews);
//            }
//
//            return details;
//        } catch (Exception e) {
//            Log.e("FetchAppointmentDetailsTask", "Error parsing appointment details JSON", e);
//            return null;
//        }
//    }
//
//}
package com.lztek.api.demo;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public class AppointmentListActivity extends AppCompatActivity {

    private TableLayout dataRowsContainer;
    private List<Appointment> appointmentList = new ArrayList<>();
    private int currentPage = 1;
    private int itemsPerPage = 10;
    private int totalPages = 1;
    private LinearLayout pageNumbersLayout;
    private ProgressBar loadingProgressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_appointment_list);

        dataRowsContainer = findViewById(R.id.data_rows_container);
        pageNumbersLayout = findViewById(R.id.page_numbers_layout);
        loadingProgressBar = findViewById(R.id.loading_progress_bar);

        // Set up sort buttons
        findViewById(R.id.sort_sr_no).setOnClickListener(v -> sortAppointments("sr_no"));
        findViewById(R.id.sort_patient).setOnClickListener(v -> sortAppointments("patient"));
        findViewById(R.id.sort_doctor).setOnClickListener(v -> sortAppointments("doctor"));
        findViewById(R.id.sort_date_time).setOnClickListener(v -> sortAppointments("date"));
        findViewById(R.id.sort_status).setOnClickListener(v -> sortAppointments("status"));
        findViewById(R.id.sort_booked_on).setOnClickListener(v -> sortAppointments("booked_on"));

        // Set up pagination buttons
        findViewById(R.id.btn_previous).setOnClickListener(v -> loadPreviousPage());
        findViewById(R.id.btn_next).setOnClickListener(v -> loadNextPage());

        // Fetch initial appointments
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

    private void showDetailsPopup(AppointmentDetails appointmentDetails) {
        Log.d("AppointmentListActivity", "Showing details popup for appointment ID: " + appointmentDetails.getAppointmentId());
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Appointment Details");

        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 24, 24, 24);

        TextView appointmentInfo = new TextView(this);
        appointmentInfo.setText("Appointment Information");
        appointmentInfo.setTextSize(18);
        appointmentInfo.setTypeface(null, Typeface.BOLD);
        layout.addView(appointmentInfo);

        addDetailRow(layout, "Appointment ID:", String.valueOf(appointmentDetails.getAppointmentId()));
        addDetailRow(layout, "Date:", appointmentDetails.getAppointmentDate());
        addDetailRow(layout, "Time:", appointmentDetails.getAppointmentTime());

        ImageView patientImage = new ImageView(this);
        patientImage.setImageResource(R.drawable.ic_patient);
        patientImage.setLayoutParams(new LinearLayout.LayoutParams(150, 150));
        patientImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        layout.addView(patientImage);

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

        if (appointmentDetails.getPatient() != null) {
            addDetailRow(layout, "Address:", appointmentDetails.getPatient().getAddress());
        }

        ImageView doctorImage = new ImageView(this);
        doctorImage.setImageResource(R.drawable.ic_doctor);
        doctorImage.setLayoutParams(new LinearLayout.LayoutParams(150, 150));
        doctorImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        layout.addView(doctorImage);

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

        scrollView.addView(layout);
        builder.setView(scrollView);
        builder.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());
        builder.create().show();
    }

    private void addDetailRow(LinearLayout layout, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(8, 8, 8, 8);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTypeface(null, Typeface.BOLD);
        labelView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(labelView);

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2));
        row.addView(valueView);

        layout.addView(row);
    }

    private void addAppointmentRow(Appointment appointment) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View rowView = inflater.inflate(R.layout.item_appointment, null);

        TextView srNo = rowView.findViewById(R.id.sr_no);
        TextView patientName = rowView.findViewById(R.id.patient_name);
        TextView doctorName = rowView.findViewById(R.id.doctor_name);
        TextView appointmentDateTime = rowView.findViewById(R.id.appointment_date_time);
        TextView appointmentStatus = rowView.findViewById(R.id.appointment_status);
        TextView bookedOn = rowView.findViewById(R.id.booked_on);
        Button detailsButton = rowView.findViewById(R.id.button_details);
        Button startButton = rowView.findViewById(R.id.button_start);

        int startIndex = (currentPage - 1) * itemsPerPage;
        int rowNumber = startIndex + dataRowsContainer.getChildCount() + 1;
        srNo.setText(String.valueOf(rowNumber));

        patientName.setText(appointment.getPatientName());
        doctorName.setText(appointment.getDoctorName());
        appointmentDateTime.setText(appointment.getAppointmentDate() + " " + appointment.getAppointmentTime());
        appointmentStatus.setText(appointment.getAppointmentStatus());
        bookedOn.setText(appointment.getCreatedAt());

        detailsButton.setOnClickListener(v -> {
            Log.d("AppointmentListActivity", "Details button clicked for appointment ID: " + appointment.getAppointmentId());
            new FetchAppointmentDetailsTask(appointment.getAppointmentId(), details -> showDetailsPopup(details)).execute();
        });

        startButton.setOnClickListener(v -> {
            Log.d("AppointmentListActivity", "Start button clicked for appointment ID: " + appointment.getAppointmentId());
            new StartMeetingTask(appointment.getAppointmentId(), success -> {
                if (success) {
                    Toast.makeText(AppointmentListActivity.this, "Meeting started successfully", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(AppointmentListActivity.this, ConsultationActivity.class);
                    startActivity(intent);
                } else {
                    Toast.makeText(AppointmentListActivity.this, "Failed to start meeting", Toast.LENGTH_SHORT).show();
                }
            }).execute();
        });

        dataRowsContainer.addView(rowView);
    }

    private void loadPage(int page) {
        currentPage = page;
        dataRowsContainer.removeAllViews();
        int start = (currentPage - 1) * itemsPerPage;
        int end = Math.min(start + itemsPerPage, appointmentList.size());
        for (int i = start; i < end; i++) {
            addAppointmentRow(appointmentList.get(i));
        }
        updatePaginationButtons();
    }

    private void loadPreviousPage() {
        if (currentPage > 1) {
            loadPage(currentPage - 1);
        }
    }

    private void loadNextPage() {
        if (currentPage < totalPages) {
            loadPage(currentPage + 1);
        }
    }

    private void updatePaginationButtons() {
        pageNumbersLayout.removeAllViews();
        totalPages = (int) Math.ceil((double) appointmentList.size() / itemsPerPage);
        for (int i = 1; i <= totalPages; i++) {
            Button pageButton = new Button(this);
            pageButton.setText(String.valueOf(i));
            pageButton.setTextColor(currentPage == i ? 0xFF000000 : 0xFF2196F3);
            pageButton.setBackgroundTintList(null);
            final int pageNum = i;
            pageButton.setOnClickListener(v -> loadPage(pageNum));
            pageNumbersLayout.addView(pageButton);
        }
        Button prevButton = findViewById(R.id.btn_previous);
        Button nextButton = findViewById(R.id.btn_next);
        prevButton.setEnabled(currentPage > 1);
        nextButton.setEnabled(currentPage < totalPages);
    }

    private void sortAppointments(String column) {
        Comparator<Appointment> comparator;
        switch (column) {
            case "sr_no":
                comparator = (a, b) -> Integer.compare(appointmentList.indexOf(a), appointmentList.indexOf(b));
                break;
            case "patient":
                comparator = (a, b) -> a.getPatientName().compareToIgnoreCase(b.getPatientName());
                break;
            case "doctor":
                comparator = (a, b) -> a.getDoctorName().compareToIgnoreCase(b.getDoctorName());
                break;
            case "date":
                comparator = (a, b) -> a.getAppointmentDate().compareTo(b.getAppointmentDate());
                break;
            case "status":
                comparator = (a, b) -> a.getAppointmentStatus().compareToIgnoreCase(b.getAppointmentStatus());
                break;
            case "booked_on":
                comparator = (a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt());
                break;
            default:
                return;
        }
        Collections.sort(appointmentList, comparator);
        loadPage(currentPage);
    }

    private class FetchAppointmentsTask extends AsyncTask<Void, Void, String> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            loadingProgressBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected String doInBackground(Void... voids) {
            try {
                String paramedicId = String.valueOf(GlobalVars.getParamedicId());
                String apiUrl = Constants.APPOINTMENT_LIST.replace("{id}", paramedicId) + "?sortBy=date&status=Upcoming&limit=10";
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
                return response.toString();
            } catch (Exception e) {
                Log.e("AppointmentListActivity", "Error fetching appointments", e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(String response) {
            loadingProgressBar.setVisibility(View.GONE);
            if (response != null) {
                parseAndLoadAppointments(response);
            } else {
                Toast.makeText(AppointmentListActivity.this, "Failed to fetch appointments", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void parseAndLoadAppointments(String jsonResponse) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            if (jsonObject.optBoolean("success", false)) {
                JSONArray dataArray = jsonObject.optJSONArray("data");
                if (dataArray != null) {
                    appointmentList.clear();
                    for (int i = 0; i < dataArray.length(); i++) {
                        JSONObject jsonAppointment = dataArray.optJSONObject(i);
                        if (jsonAppointment != null) {
                            int appointmentId = jsonAppointment.optInt("appointment_Id", -1);
                            int doctorId = jsonAppointment.optInt("doctor_Id", -1);
                            int patientId = jsonAppointment.optInt("patient_Id", -1);
                            String patientName = jsonAppointment.optString("patientName", "Unknown");
                            String paramedicName = jsonAppointment.optString("paramedicName", "Unknown");
                            String doctorName = jsonAppointment.optString("doctorName", "Unknown");
                            String appointmentDate = String.valueOf(jsonAppointment.optInt("appointment_date", 0));
                            String appointmentTime = jsonAppointment.optString("appointment_time", "N/A");
                            String appointmentStatus = jsonAppointment.optString("appointment_status", "Pending");
                            String notificationType = jsonAppointment.optString("appointment_notification_type", "N/A");
                            String meetingId = jsonAppointment.optString("meeting_Id", "N/A");
                            String meetingUrl = jsonAppointment.optString("meeting_url", "N/A");
                            String passcode = jsonAppointment.optString("passcode", "N/A");
                            String createdAt = jsonAppointment.optString("createdAt", "N/A");
                            boolean isActive = jsonAppointment.optBoolean("is_active", true);

                            Appointment appointment = new Appointment(
                                    appointmentId, doctorId, patientId, patientName, paramedicName,
                                    doctorName, appointmentDate, appointmentTime, appointmentStatus,
                                    notificationType, meetingId, meetingUrl, passcode, createdAt, isActive
                            );
                            appointmentList.add(appointment);
                        }
                    }
                    loadPage(1);
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
        protected void onPreExecute() {
            super.onPreExecute();
            loadingProgressBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected AppointmentDetails doInBackground(Void... voids) {
            try {
                String apiUrl = Constants.APPOINTMENT_DETAILS.replace("{id}", String.valueOf(appointmentId));
                Log.d("FetchAppointmentDetailsTask", "API URL: " + apiUrl);
                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + GlobalVars.getAccessToken());
                conn.setRequestProperty("App-Tenant", GlobalVars.getClientId());

                int responseCode = conn.getResponseCode();
                Log.d("FetchAppointmentDetailsTask", "Response Code: " + responseCode);

                BufferedReader reader;
                if (responseCode >= 400) {
                    reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                } else {
                    reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                }

                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                Log.d("FetchAppointmentDetailsTask", "Raw JSON response: " + response.toString());

                JSONObject jsonResponse = new JSONObject(response.toString());
                return parseAppointmentDetails(jsonResponse);

            } catch (Exception e) {
                Log.e("FetchAppointmentDetailsTask", "Error fetching appointment details", e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(AppointmentDetails appointmentDetails) {
            loadingProgressBar.setVisibility(View.GONE);
            if (appointmentDetails != null) {
                Log.d("FetchAppointmentDetailsTask", "Parsed AppointmentDetails: " + appointmentDetails.toString());
                if (callback != null) {
                    callback.accept(appointmentDetails);
                }
            } else {
                Log.e("FetchAppointmentDetailsTask", "AppointmentDetails is null");
                Toast.makeText(AppointmentListActivity.this, "Failed to load appointment details", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private AppointmentDetails parseAppointmentDetails(JSONObject jsonResponse) {
        try {
            JSONObject data = jsonResponse.getJSONObject("data");
            AppointmentDetails details = new AppointmentDetails();

            details.setAppointmentId(data.optInt("appointment_Id", -1));
            details.setAppointmentDate(data.optString("appointment_date", ""));
            details.setAppointmentTime(data.optString("appointment_time", ""));
            details.setAppointmentNotificationType(data.optInt("appointment_notification_type", 0));
            details.setPasscode(data.optString("passcode", ""));
            details.setAppointmentStatus(data.optInt("appointment_status", 0));
            details.setMedicalHistory(data.optString("medical_history", ""));
            details.setMedicalRecords(data.optString("medical_records", ""));
            details.setMeetingId(data.optString("meeting_Id", ""));
            details.setMeetingUrl(data.optString("meeting_url", ""));
            details.setPatientId(data.optInt("patient_Id", -1));
            details.setFamilyMemberId(data.optInt("family_member_id", -1));
            details.setActive(data.optBoolean("is_active", true));
            details.setCreatedAt(data.optString("createdAt", ""));
            details.setConfirmedBy(data.optInt("confirmedBy", -1));
            details.setPatientFirstName(data.optString("patientFirstName", ""));
            details.setPatientLastName(data.optString("patientLastName", ""));
            details.setGender(data.optString("gender", ""));
            details.setDob(data.optString("DOB", ""));
            details.setAbhaId(data.optString("Abha_ID", ""));
            details.setEmail(data.optString("email", ""));
            details.setPhoneNumber(data.optString("phone_number", ""));
            details.setNotificationMedium(data.optString("NotificationMeidum", ""));
            details.setAppointmentStatusName(data.optString("appointmentStatusName", ""));

            if (data.has("Doctor")) {
                JSONObject doctorJson = data.getJSONObject("Doctor");
                AppointmentDetails.Doctor doctor = new AppointmentDetails.Doctor();
                doctor.setDoctorId(doctorJson.optInt("doctor_Id", -1));
                doctor.setEmail(doctorJson.optString("email", ""));
                doctor.setFirstName(doctorJson.optString("first_name", ""));
                doctor.setLastName(doctorJson.optString("last_name", ""));
                doctor.setPhoneNumber(doctorJson.optString("phone_number", ""));
                doctor.setSpecialization(doctorJson.optString("specialization", ""));
                doctor.setYearsOfExperience(doctorJson.optString("years_of_experience", ""));
                doctor.setPhysicalWorkingHours(doctorJson.optString("physical_working_hours", ""));
                doctor.setLiveConsultationHours(doctorJson.optString("live_consultation", ""));
                details.setDoctor(doctor);
            }

            if (data.has("Patient")) {
                JSONObject patientJson = data.getJSONObject("Patient");
                AppointmentDetails.Patient patient = new AppointmentDetails.Patient();
                patient.setPhoneNumber(patientJson.optString("phone_number", ""));
                patient.setAddress(patientJson.optString("address", ""));
                details.setPatient(patient);
            }

            if (data.has("Paramedic")) {
                JSONObject paramedicJson = data.getJSONObject("Paramedic");
                AppointmentDetails.Paramedic paramedic = new AppointmentDetails.Paramedic();
                paramedic.setAddress(paramedicJson.optString("address", ""));
                details.setParamedic(paramedic);
            }

            if (data.has("Reviews")) {
                JSONArray reviewsArray = data.getJSONArray("Reviews");
                List<AppointmentDetails.Review> reviews = new ArrayList<>();
                for (int i = 0; i < reviewsArray.length(); i++) {
                    JSONObject reviewJson = reviewsArray.getJSONObject(i);
                    AppointmentDetails.Review review = new AppointmentDetails.Review();
                    review.setReviewId(reviewJson.optInt("reviewId", -1));
                    review.setReviewText(reviewJson.optString("reviewText", ""));
                    review.setRating(reviewJson.optInt("rating", 0));
                    reviews.add(review);
                }
                details.setReviews(reviews);
            }

            return details;
        } catch (Exception e) {
            Log.e("FetchAppointmentDetailsTask", "Error parsing appointment details JSON", e);
            return null;
        }
    }

//

    private class StartMeetingTask extends AsyncTask<Void, Void, Boolean> {
        private int appointmentId;
        private Consumer<Boolean> callback;
        private int retryCount = 0; // Retry 2 times if fails

        public StartMeetingTask(int appointmentId, Consumer<Boolean> callback) {
            this.appointmentId = appointmentId;
            this.callback = callback;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (!isNetworkAvailable()) {
                Log.e("StartMeetingTask", "No network available");
                return;
            }
            loadingProgressBar.setVisibility(View.VISIBLE); // Show progress bar
            Log.d("StartMeetingTask", "Task started for appointmentId: " + appointmentId);
        }

        private boolean isNetworkAvailable() {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnected();
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            HttpURLConnection conn = null;
            int attempt = 0;
            while (attempt <= retryCount) {
                try {
                    String apiUrl = Constants.STARTMEETING + "?appointmentId=" + appointmentId;
                    Log.d("StartMeetingTask", "API URL: " + apiUrl + " (Attempt " + (attempt + 1) + ")");
                    URL url = new URL(apiUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("PUT"); // Trying GET with query parameter
                    conn.setConnectTimeout(60000); // 60 seconds timeout
                    conn.setReadTimeout(60000); // 60 seconds timeout
                    String accessToken = GlobalVars.getAccessToken();
                    String clientId = GlobalVars.getClientId();
                    Log.d("StartMeetingTask", "Access Token: " + (accessToken != null ? accessToken.substring(0, 10) + "..." : "Null"));
                    Log.d("StartMeetingTask", "Client ID: " + (clientId != null ? clientId : "Null"));
                    if (accessToken == null || accessToken.isEmpty()) return false;
                    if (clientId == null || clientId.isEmpty()) return false;
                    conn.setRequestProperty("Authorization", "Bearer " + accessToken);
                    conn.setRequestProperty("App-Tenant", clientId);

                    Log.d("StartMeetingTask", "Set Headers: Authorization=Bearer ..., App-Tenant=" + clientId);

                    int responseCode = conn.getResponseCode();
                    Log.d("StartMeetingTask", "Response Code: " + responseCode);

                    BufferedReader reader;
                    if (responseCode >= 400) {
                        reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                    } else {
                        reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    }

                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    Log.d("StartMeetingTask", "Raw Response: " + response.toString());

                    if (responseCode >= 200 && responseCode < 300) {
                        JSONObject jsonResponse = new JSONObject(response.toString());
                        boolean success = jsonResponse.optBoolean("success", false);
                        if (success) {
                            JSONObject data = jsonResponse.optJSONObject("data");
                            if (data != null) {
                                GlobalVars.setMeetingApiKey(data.optString("apiKey", ""));
                                GlobalVars.setMeetingSessionId(data.optString("sessionId", ""));
                                GlobalVars.setMeetingAudioSessionId(data.optString("audioSessionId", ""));
                                GlobalVars.setMeetingToken(data.optString("token", ""));
                                GlobalVars.setMeetingAudioToken(data.optString("audioToken", ""));
                                GlobalVars.setMeetingUrl(data.optString("meetingUrl", ""));
                            }
                        }
                        return success;
                    } else {
                        Log.e("StartMeetingTask", "Server error: " + responseCode + ", Response: " + response.toString());
                    }
                } catch (Exception e) {
                    Log.e("StartMeetingTask", "Error in attempt " + (attempt + 1), e);
                    if (attempt == retryCount) return false;
                } finally {
                    if (conn != null) conn.disconnect();
                }
                attempt++;
                try { Thread.sleep(2000); } catch (InterruptedException e) { Log.e("StartMeetingTask", "Retry delay interrupted", e); }
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            loadingProgressBar.setVisibility(View.GONE); // Hide progress bar
            Log.d("StartMeetingTask", "Task completed with success: " + success);
            if (success) {
                Log.d("StartMeetingTask", "Meeting started successfully: " + GlobalVars.getMeetingUrl());
                if (callback != null) {
                    callback.accept(true);
                }
                Intent intent = new Intent(AppointmentListActivity.this, OnlineSessionActivity.class);
                startActivity(intent);
            } else {
                Log.e("StartMeetingTask", "Failed to start meeting");
                Toast.makeText(AppointmentListActivity.this, "Failed to start meeting", Toast.LENGTH_SHORT).show();
            }
        }
    }


}