package com.lztek.api.demo;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GlobalVars {

    private static GlobalVars instance;

    private static boolean isCam1On = false;
    private static boolean isCam2On = false;
    private static boolean isFrontRecording = false; // Added
    private static boolean isUsbRecording = false; // Added
    private boolean isInternalCameraConnected = false;
    private boolean isUSBCameraConnected = false;
    private boolean isCameraPreviewActive = false;

    private boolean keyboardConnected;

    // Authentication tokens
    private static String accessToken;
    private static String refreshToken;

    private static final String TAG = "GlobalVars";
    public static boolean isVitalOn = false;

    // New variables for Berry sensors
    private boolean isSpO2Connected = false;
    private boolean isECGConnected = false;
    private boolean isNIBPConnected = false;
    private boolean isTempConnected = false;

    // Profile data
    private static int paramedicId;
    private static int userId;
    private static String firstName;
    private static String lastName;
    private static String email;
    private static String address;
    private static String dateOfBirth;
    private static String gender;
    private static String phoneNumber;
    private static String training;
    private static String previousPositions;
    private static String education;
    private static String workingHours;
    private static String language;
    private static String expertise;
    private static String notes;
    private static String yearsOfExperience;
    private static String password;
    private static boolean isActive;
    private static Double averageRating;
    private static String profilePicture;
    private static String profilePhotoUrl;
    private static AppointmentDetails currentAppointmentDetails;

    // Device information
    private static String hardwareDeviceId;
    private static String deviceId;

    // API Response Data - Device
    private static int devicesId;
    private static String factoryDeviceId;
    private static String devicesName;
    private static String devicesUrl;
    private static int createdBy;
    private static String createdDate;
    private static boolean deviceIsActive;
    private static int customerId;
    private static Integer modifiedBy;
    private static String modifiedDate;
    private static int deviceStatus;
    private static String deviceStatusName;
    private static String clientId;

    // API Response Data - Customer (with customer_ prefix)
    private static String customer_companyName;
    private static String customer_companyLogo;
    private static String customer_companyAddress;
    private static String customer_companyContact;
    private static String customer_contactPersonName;
    private static String customer_contactEmail;
    private static String customer_contactPhone;
    private static int customer_accountStatus;
    private static String customer_accountStatusName;
    private static int customer_accountManager;
    private static String customer_accountManagerName;
    private static String customer_blobUrl;

    public static final List<Byte> audioBuffer = Collections.synchronizedList(new ArrayList<>());

    // Authentication token getters and setters
    public static String getAccessToken() {
        return accessToken;
    }

    public static void setAccessToken(String token) {
        accessToken = token;
    }

    public static String getRefreshToken() {
        return refreshToken;
    }

    public static void setRefreshToken(String token) {
        refreshToken = token;
    }

    // Profile data getters and setters
    public static int getParamedicId() {
        return paramedicId;
    }

    public static void setParamedicId(int id) {
        paramedicId = id;
    }

    public static int getUserId() {
        return userId;
    }

    public static void setUserId(int id) {
        userId = id;
    }

    public static String getFirstName() {
        return firstName;
    }

    public static void setFirstName(String name) {
        firstName = name;
    }

    public static String getLastName() {
        return lastName;
    }

    public static void setLastName(String name) {
        lastName = name;
    }

    public static String getEmail() {
        return email;
    }

    public static void setEmail(String mail) {
        email = mail;
    }

    public static String getAddress() {
        return address;
    }

    public static void setAddress(String addr) {
        address = addr;
    }

    public static String getDateOfBirth() {
        return dateOfBirth;
    }

    public static void setDateOfBirth(String dob) {
        dateOfBirth = dob;
    }

    public static String getGender() {
        return gender;
    }

    public static void setGender(String gen) {
        gender = gen;
    }

    public static String getPhoneNumber() {
        return phoneNumber;
    }

    public static void setPhoneNumber(String phone) {
        phoneNumber = phone;
    }

    public static String getTraining() {
        return training;
    }

    public static void setTraining(String train) {
        training = train;
    }

    public static String getPreviousPositions() {
        return previousPositions;
    }

    public static void setPreviousPositions(String positions) {
        previousPositions = positions;
    }

    public static String getEducation() {
        return education;
    }

    public static void setEducation(String edu) {
        education = edu;
    }

    public static String getWorkingHours() {
        return workingHours;
    }

    public static void setWorkingHours(String hours) {
        workingHours = hours;
    }

    public static String getLanguage() {
        return language;
    }

    public static void setLanguage(String lang) {
        language = lang;
    }

    public static String getExpertise() {
        return expertise;
    }

    public static void setExpertise(String exp) {
        expertise = exp;
    }

    public static String getNotes() {
        return notes;
    }

    public static void setNotes(String note) {
        notes = note;
    }

    public static String getYearsOfExperience() {
        return yearsOfExperience;
    }

    public static void setYearsOfExperience(String years) {
        yearsOfExperience = years;
    }

    public static String getPassword() {
        return password;
    }

    public static void setPassword(String pass) {
        password = pass;
    }

    public static boolean isActive() {
        return isActive;
    }

    public static void setIsActive(boolean active) {
        isActive = active;
    }

    public static Double getAverageRating() {
        return averageRating;
    }

    public static void setAverageRating(Double rating) {
        averageRating = rating;
    }

    public static String getProfilePicture() {
        return profilePicture;
    }

    public static void setProfilePicture(String picture) {
        profilePicture = picture;
    }

    public static String getProfilePhotoUrl() {
        return profilePhotoUrl;
    }

    public static void setProfilePhotoUrl(String url) {
        profilePhotoUrl = url;
    }

    public static AppointmentDetails getCurrentAppointmentDetails() {
        return currentAppointmentDetails;
    }

    public static void setCurrentAppointmentDetails(AppointmentDetails details) {
        currentAppointmentDetails = details;
    }

    // Getters and Setters for Device Information
    public static String getHardwareDeviceId() {
        return hardwareDeviceId;
    }

    public static void setHardwareDeviceId(String id) {
        hardwareDeviceId = id;
    }

    public static String getDeviceId() {
        return deviceId;
    }

    public static void setDeviceId(String id) {
        deviceId = id;
    }

    // Getters and Setters for API Response Data - Device
    public static int getDevicesId() {
        return devicesId;
    }

    public static void setDevicesId(int id) {
        devicesId = id;
    }

    public static String getFactoryDeviceId() {
        return factoryDeviceId;
    }

    public static void setFactoryDeviceId(String id) {
        factoryDeviceId = id;
    }

    public static String getDevicesName() {
        return devicesName;
    }

    public static void setDevicesName(String name) {
        devicesName = name;
    }

    public static String getDevicesUrl() {
        return devicesUrl;
    }

    public static void setDevicesUrl(String url) {
        devicesUrl = url;
    }

    public static int getCreatedBy() {
        return createdBy;
    }

    public static void setCreatedBy(int id) {
        createdBy = id;
    }

    public static String getCreatedDate() {
        return createdDate;
    }

    public static void setCreatedDate(String date) {
        createdDate = date;
    }

    public static boolean isDeviceIsActive() {
        return deviceIsActive;
    }

    public static void setDeviceIsActive(boolean active) {
        deviceIsActive = active;
    }

    public static int getCustomerId() {
        return customerId;
    }

    public static void setCustomerId(int id) {
        customerId = id;
    }

    public static Integer getModifiedBy() {
        return modifiedBy;
    }

    public static void setModifiedBy(Integer id) {
        modifiedBy = id;
    }

    public static String getModifiedDate() {
        return modifiedDate;
    }

    public static void setModifiedDate(String date) {
        modifiedDate = date;
    }

    public static int getDeviceStatus() {
        return deviceStatus;
    }

    public static void setDeviceStatus(int status) {
        deviceStatus = status;
    }

    public static String getDeviceStatusName() {
        return deviceStatusName;
    }

    public static void setDeviceStatusName(String name) {
        deviceStatusName = name;
    }

    public static String getClientId() {
        return clientId;
    }

    public static void setClientId(String id) {
        clientId = id;
    }

    // Getters and Setters for API Response Data - Customer (with customer_ prefix)
    public static String getCustomerCompanyName() {
        return customer_companyName;
    }

    public static void setCustomerCompanyName(String name) {
        customer_companyName = name;
    }

    public static String getCustomerCompanyLogo() {
        return customer_companyLogo;
    }

    public static void setCustomerCompanyLogo(String logo) {
        customer_companyLogo = logo;
    }

    public static String getCustomerCompanyAddress() {
        return customer_companyAddress;
    }

    public static void setCustomerCompanyAddress(String address) {
        customer_companyAddress = address;
    }

    public static String getCustomerCompanyContact() {
        return customer_companyContact;
    }

    public static void setCustomerCompanyContact(String contact) {
        customer_companyContact = contact;
    }

    public static String getCustomerContactPersonName() {
        return customer_contactPersonName;
    }

    public static void setCustomerContactPersonName(String name) {
        customer_contactPersonName = name;
    }

    public static String getCustomerContactEmail() {
        return customer_contactEmail;
    }

    public static void setCustomerContactEmail(String email) {
        customer_contactEmail = email;
    }

    public static String getCustomerContactPhone() {
        return customer_contactPhone;
    }

    public static void setCustomerContactPhone(String phone) {
        customer_contactPhone = phone;
    }

    public static int getCustomerAccountStatus() {
        return customer_accountStatus;
    }

    public static void setCustomerAccountStatus(int status) {
        customer_accountStatus = status;
    }

    public static String getCustomerAccountStatusName() {
        return customer_accountStatusName;
    }

    public static void setCustomerAccountStatusName(String name) {
        customer_accountStatusName = name;
    }

    public static int getCustomerAccountManager() {
        return customer_accountManager;
    }

    public static void setCustomerAccountManager(int manager) {
        customer_accountManager = manager;
    }

    public static String getCustomerAccountManagerName() {
        return customer_accountManagerName;
    }

    public static void setCustomerAccountManagerName(String name) {
        customer_accountManagerName = name;
    }

    public static String getCustomerBlobUrl() {
        return customer_blobUrl;
    }

    public static void setCustomerBlobUrl(String url) {
        customer_blobUrl = url;
    }

    public static void updateDeviceDetails(JSONObject device) {
        try {
            // Device details
            devicesId = device.optInt("devices_id", 0);
            factoryDeviceId = device.optString("factorydevice_id", "");
            devicesName = device.optString("devices_name", "Unknown");
            devicesUrl = device.optString("devices_url", "");
            createdBy = device.optInt("created_by", 0);
            createdDate = device.optString("created_date", "");
            deviceIsActive = device.optBoolean("is_active", false);
            customerId = device.optInt("customer_Id", 0);
            modifiedBy = device.has("modified_by") ? (device.isNull("modified_by") ? null : device.optInt("modified_by")) : null;
            modifiedDate = device.optString("modified_date", "");
            deviceStatus = device.optInt("device_status", 0);
            deviceStatusName = device.optString("deviceStatusName", "Inactive");
            clientId = device.optString("clientId", "");

            // Customer details
            JSONObject customer = device.optJSONObject("Customer");
            if (customer != null) {
                customer_companyName = customer.optString("companyName", "Unknown");
                customer_companyLogo = customer.optString("companyLogo", "");
                customer_companyAddress = customer.optString("companyAddress", "");
                customer_companyContact = customer.optString("companyContact", "");
                customer_contactPersonName = customer.optString("contactPersonName", "Unknown");
                customer_contactEmail = customer.optString("contactEmail", "");
                customer_contactPhone = customer.optString("contactPhone", "");
                customer_accountStatus = customer.optInt("accountStatus", 0);
                customer_accountStatusName = customer.optString("accountStatusName", "Inactive");
                customer_accountManager = customer.optInt("accountManager", 0);
                customer_accountManagerName = customer.optString("accountManagerName", "Unknown");
                customer_blobUrl = customer.optString("blobUrl", "");
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("GlobalVars", "Error updating device details: " + e.getMessage());
        }
    }

    public static GlobalVars getInstance() {
        if (instance == null) {
            instance = new GlobalVars();
        }
        return instance;
    }

    public boolean isInternalCameraConnected() {
        return isInternalCameraConnected;
    }

    public void setInternalCameraConnected(boolean connected) {
        this.isInternalCameraConnected = connected;
    }

    public boolean isUSBCameraConnected() {
        return isUSBCameraConnected;
    }

    public void setUSBCameraConnected(boolean connected) {
        this.isUSBCameraConnected = connected;
    }

    public boolean isCameraPreviewActive() {
        return isCameraPreviewActive;
    }

    public void setCameraPreviewActive(boolean active) {
        this.isCameraPreviewActive = active;
    }

    public void setKeyboardConnected(boolean connected) {
        this.keyboardConnected = connected;
    }

    public boolean isKeyboardConnected() {
        return keyboardConnected;
    }

    // Getters and setters for Berry sensors
    public boolean isSpO2Connected() {
        return isSpO2Connected;
    }

    public void setSpO2Connected(boolean connected) {
        this.isSpO2Connected = connected;
    }

    public boolean isECGConnected() {
        return isECGConnected;
    }

    public void setECGConnected(boolean connected) {
        this.isECGConnected = connected;
    }

    public boolean isNIBPConnected() {
        return isNIBPConnected;
    }

    public void setNIBPConnected(boolean connected) {
        this.isNIBPConnected = connected;
    }

    public boolean isTempConnected() {
        return isTempConnected;
    }

    public void setTempConnected(boolean connected) {
        this.isTempConnected = connected;
    }

    public static void setVitalOn(boolean value) {
        isVitalOn = value;
        Log.d(TAG, "isVitalOn set to: " + isVitalOn);
    }

    public static void setCam1On(boolean value) {
        isCam1On = value;
    }

    public static boolean isCam1On() {
        return isCam1On;
    }

    public static void setCam2On(boolean value) {
        isCam2On = value;
    }

    public static boolean isCam2On() {
        return isCam2On;
    }

    public static boolean isFrontRecording() {
        return isFrontRecording;
    }

    public static void setFrontRecording(boolean recording) {
        isFrontRecording = recording;
    }

    public static boolean isUsbRecording() {
        return isUsbRecording;
    }

    public static void setUsbRecording(boolean recording) {
        isUsbRecording = recording;
    }
}


//package com.lztek.api.demo;
//
//import android.content.Context;
//import android.content.SharedPreferences;
//import android.util.Log;
//
//import org.json.JSONObject;
//
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.List;
//
//public class GlobalVars {
//
//    private static GlobalVars instance;
//
//    private static boolean isCam1On = false;
//    private static boolean isCam2On = false;
//    private boolean isInternalCameraConnected = false;
//    private boolean isUSBCameraConnected = false;
//    private boolean isCameraPreviewActive = false;
//
//    private boolean keyboardConnected;// New flag
//
//
//    // Authentication tokens
//    private static String accessToken;
//    private static String refreshToken;
//
//    //
//    private static final String TAG = "GlobalVars";
//    public static boolean isVitalOn = false;
//
//
//    // New variables for Berry sensors
//    private boolean isSpO2Connected = false;
//    private boolean isECGConnected = false;
//    private boolean isNIBPConnected = false;
//    private boolean isTempConnected = false;
//
//    // Profile data
//    private static int paramedicId;
//    private static int userId;
//    private static String firstName;
//    private static String lastName;
//    private static String email;
//    private static String address;
//    private static String dateOfBirth;
//    private static String gender;
//    private static String phoneNumber;
//    private static String training;
//    private static String previousPositions;
//    private static String education;
//    private static String workingHours;
//    private static String language;
//    private static String expertise;
//    private static String notes;
//    private static String yearsOfExperience;
//    private static String password;
//    private static boolean isActive;
//    private static Double averageRating;
//    private static String profilePicture;
//    private static String profilePhotoUrl;
//    private static AppointmentDetails currentAppointmentDetails;
//
//
//    // Device information
//    private static String hardwareDeviceId;
//    private static String deviceId;
//
//    // API Response Data - Device
//    private static int devicesId;
//    private static String factoryDeviceId;
//    private static String devicesName;
//    private static String devicesUrl;
//    private static int createdBy;
//    private static String createdDate;
//    private static boolean deviceIsActive;
//    private static int customerId;
//    private static Integer modifiedBy;
//    private static String modifiedDate;
//    private static int deviceStatus;
//    private static String deviceStatusName;
//    private static String clientId;
//
//    // API Response Data - Customer (with customer_ prefix)
//    private static String customer_companyName;
//    private static String customer_companyLogo;
//    private static String customer_companyAddress;
//    private static String customer_companyContact;
//    private static String customer_contactPersonName;
//    private static String customer_contactEmail;
//    private static String customer_contactPhone;
//    private static int customer_accountStatus;
//    private static String customer_accountStatusName;
//    private static int customer_accountManager;
//    private static String customer_accountManagerName;
//    private static String customer_blobUrl;
//
//    public static final List<Byte> audioBuffer = Collections.synchronizedList(new ArrayList<>());
//
//    // Authentication token getters and setters
//    public static String getAccessToken() {
//        return accessToken;
//    }
//
//    public static void setAccessToken(String token) {
//        accessToken = token;
//    }
//
//    public static String getRefreshToken() {
//        return refreshToken;
//    }
//
//    public static void setRefreshToken(String token) {
//        refreshToken = token;
//    }
//
//    // Profile data getters and setters
//    public static int getParamedicId() {
//        return paramedicId;
//    }
//
//    public static void setParamedicId(int id) {
//        paramedicId = id;
//    }
//
//    public static int getUserId() {
//        return userId;
//    }
//
//    public static void setUserId(int id) {
//        userId = id;
//    }
//
//    public static String getFirstName() {
//        return firstName;
//    }
//
//    public static void setFirstName(String name) {
//        firstName = name;
//    }
//
//    public static String getLastName() {
//        return lastName;
//    }
//
//    public static void setLastName(String name) {
//        lastName = name;
//    }
//
//    public static String getEmail() {
//        return email;
//    }
//
//    public static void setEmail(String mail) {
//        email = mail;
//    }
//
//    public static String getAddress() {
//        return address;
//    }
//
//    public static void setAddress(String addr) {
//        address = addr;
//    }
//
//    public static String getDateOfBirth() {
//        return dateOfBirth;
//    }
//
//    public static void setDateOfBirth(String dob) {
//        dateOfBirth = dob;
//    }
//
//    public static String getGender() {
//        return gender;
//    }
//
//    public static void setGender(String gen) {
//        gender = gen;
//    }
//
//    public static String getPhoneNumber() {
//        return phoneNumber;
//    }
//
//    public static void setPhoneNumber(String phone) {
//        phoneNumber = phone;
//    }
//
//    public static String getTraining() {
//        return training;
//    }
//
//    public static void setTraining(String train) {
//        training = train;
//    }
//
//    public static String getPreviousPositions() {
//        return previousPositions;
//    }
//
//    public static void setPreviousPositions(String positions) {
//        previousPositions = positions;
//    }
//
//    public static String getEducation() {
//        return education;
//    }
//
//    public static void setEducation(String edu) {
//        education = edu;
//    }
//
//    public static String getWorkingHours() {
//        return workingHours;
//    }
//
//    public static void setWorkingHours(String hours) {
//        workingHours = hours;
//    }
//
//    public static String getLanguage() {
//        return language;
//    }
//
//    public static void setLanguage(String lang) {
//        language = lang;
//    }
//
//    public static String getExpertise() {
//        return expertise;
//    }
//
//    public static void setExpertise(String exp) {
//        expertise = exp;
//    }
//
//    public static String getNotes() {
//        return notes;
//    }
//
//    public static void setNotes(String note) {
//        notes = note;
//    }
//
//    public static String getYearsOfExperience() {
//        return yearsOfExperience;
//    }
//
//    public static void setYearsOfExperience(String years) {
//        yearsOfExperience = years;
//    }
//
//    public static String getPassword() {
//        return password;
//    }
//
//    public static void setPassword(String pass) {
//        password = pass;
//    }
//
//    public static boolean isActive() {
//        return isActive;
//    }
//
//    public static void setIsActive(boolean active) {
//        isActive = active;
//    }
//
//    public static Double getAverageRating() {
//        return averageRating;
//    }
//
//    public static void setAverageRating(Double rating) {
//        averageRating = rating;
//    }
//
//    public static String getProfilePicture() {
//        return profilePicture;
//    }
//
//    public static void setProfilePicture(String picture) {
//        profilePicture = picture;
//    }
//
//    public static String getProfilePhotoUrl() {
//        return profilePhotoUrl;
//    }
//
//    public static void setProfilePhotoUrl(String url) {
//        profilePhotoUrl = url;
//    }
//
//    public static AppointmentDetails getCurrentAppointmentDetails() {
//        return currentAppointmentDetails;
//    }
//
//    public static void setCurrentAppointmentDetails(AppointmentDetails details) {
//        currentAppointmentDetails = details;
//    }
//
//    // Getters and Setters for Device Information
//    public static String getHardwareDeviceId() {
//        return hardwareDeviceId;
//    }
//
//    public static void setHardwareDeviceId(String id) {
//        hardwareDeviceId = id;
//    }
//
//    public static String getDeviceId() {
//        return deviceId;
//    }
//
//    public static void setDeviceId(String id) {
//        deviceId = id;
//    }
//
//    // Getters and Setters for API Response Data - Device
//    public static int getDevicesId() {
//        return devicesId;
//    }
//
//    public static void setDevicesId(int id) {
//        devicesId = id;
//    }
//
//    public static String getFactoryDeviceId() {
//        return factoryDeviceId;
//    }
//
//    public static void setFactoryDeviceId(String id) {
//        factoryDeviceId = id;
//    }
//
//    public static String getDevicesName() {
//        return devicesName;
//    }
//
//    public static void setDevicesName(String name) {
//        devicesName = name;
//    }
//
//    public static String getDevicesUrl() {
//        return devicesUrl;
//    }
//
//    public static void setDevicesUrl(String url) {
//        devicesUrl = url;
//    }
//
//    public static int getCreatedBy() {
//        return createdBy;
//    }
//
//    public static void setCreatedBy(int id) {
//        createdBy = id;
//    }
//
//    public static String getCreatedDate() {
//        return createdDate;
//    }
//
//    public static void setCreatedDate(String date) {
//        createdDate = date;
//    }
//
//    public static boolean isDeviceIsActive() {
//        return deviceIsActive;
//    }
//
//    public static void setDeviceIsActive(boolean active) {
//        deviceIsActive = active;
//    }
//
//    public static int getCustomerId() {
//        return customerId;
//    }
//
//    public static void setCustomerId(int id) {
//        customerId = id;
//    }
//
//    public static Integer getModifiedBy() {
//        return modifiedBy;
//    }
//
//    public static void setModifiedBy(Integer id) {
//        modifiedBy = id;
//    }
//
//    public static String getModifiedDate() {
//        return modifiedDate;
//    }
//
//    public static void setModifiedDate(String date) {
//        modifiedDate = date;
//    }
//
//    public static int getDeviceStatus() {
//        return deviceStatus;
//    }
//
//    public static void setDeviceStatus(int status) {
//        deviceStatus = status;
//    }
//
//    public static String getDeviceStatusName() {
//        return deviceStatusName;
//    }
//
//    public static void setDeviceStatusName(String name) {
//        deviceStatusName = name;
//    }
//
//    public static String getClientId() {
//        return clientId;
//    }
//
//    public static void setClientId(String id) {
//        clientId = id;
//    }
//
//    // Getters and Setters for API Response Data - Customer (with customer_ prefix)
//    public static String getCustomerCompanyName() {
//        return customer_companyName;
//    }
//
//    public static void setCustomerCompanyName(String name) {
//        customer_companyName = name;
//    }
//
//    public static String getCustomerCompanyLogo() {
//        return customer_companyLogo;
//    }
//
//    public static void setCustomerCompanyLogo(String logo) {
//        customer_companyLogo = logo;
//    }
//
//    public static String getCustomerCompanyAddress() {
//        return customer_companyAddress;
//    }
//
//    public static void setCustomerCompanyAddress(String address) {
//        customer_companyAddress = address;
//    }
//
//    public static String getCustomerCompanyContact() {
//        return customer_companyContact;
//    }
//
//    public static void setCustomerCompanyContact(String contact) {
//        customer_companyContact = contact;
//    }
//
//    public static String getCustomerContactPersonName() {
//        return customer_contactPersonName;
//    }
//
//    public static void setCustomerContactPersonName(String name) {
//        customer_contactPersonName = name;
//    }
//
//    public static String getCustomerContactEmail() {
//        return customer_contactEmail;
//    }
//
//    public static void setCustomerContactEmail(String email) {
//        customer_contactEmail = email;
//    }
//
//    public static String getCustomerContactPhone() {
//        return customer_contactPhone;
//    }
//
//    public static void setCustomerContactPhone(String phone) {
//        customer_contactPhone = phone;
//    }
//
//    public static int getCustomerAccountStatus() {
//        return customer_accountStatus;
//    }
//
//    public static void setCustomerAccountStatus(int status) {
//        customer_accountStatus = status;
//    }
//
//    public static String getCustomerAccountStatusName() {
//        return customer_accountStatusName;
//    }
//
//    public static void setCustomerAccountStatusName(String name) {
//        customer_accountStatusName = name;
//    }
//
//    public static int getCustomerAccountManager() {
//        return customer_accountManager;
//    }
//
//    public static void setCustomerAccountManager(int manager) {
//        customer_accountManager = manager;
//    }
//
//    public static String getCustomerAccountManagerName() {
//        return customer_accountManagerName;
//    }
//
//    public static void setCustomerAccountManagerName(String name) {
//        customer_accountManagerName = name;
//    }
//
//    public static String getCustomerBlobUrl() {
//        return customer_blobUrl;
//    }
//
//    public static void setCustomerBlobUrl(String url) {
//        customer_blobUrl = url;
//    }
//
//    public static void updateDeviceDetails(JSONObject device) {
//        try {
//            // Device details
//            devicesId = device.optInt("devices_id", 0);
//            factoryDeviceId = device.optString("factorydevice_id", "");
//            devicesName = device.optString("devices_name", "Unknown");
//            devicesUrl = device.optString("devices_url", "");
//            createdBy = device.optInt("created_by", 0);
//            createdDate = device.optString("created_date", "");
//            deviceIsActive = device.optBoolean("is_active", false);
//            customerId = device.optInt("customer_Id", 0);
//            modifiedBy = device.has("modified_by") ? (device.isNull("modified_by") ? null : device.optInt("modified_by")) : null;
//            modifiedDate = device.optString("modified_date", "");
//            deviceStatus = device.optInt("device_status", 0);
//            deviceStatusName = device.optString("deviceStatusName", "Inactive");
//            clientId = device.optString("clientId", "");
//
//            // Customer details
//            JSONObject customer = device.optJSONObject("Customer");
//            if (customer != null) {
//                customer_companyName = customer.optString("companyName", "Unknown");
//                customer_companyLogo = customer.optString("companyLogo", "");
//                customer_companyAddress = customer.optString("companyAddress", "");
//                customer_companyContact = customer.optString("companyContact", "");
//                customer_contactPersonName = customer.optString("contactPersonName", "Unknown");
//                customer_contactEmail = customer.optString("contactEmail", "");
//                customer_contactPhone = customer.optString("contactPhone", "");
//                customer_accountStatus = customer.optInt("accountStatus", 0);
//                customer_accountStatusName = customer.optString("accountStatusName", "Inactive");
//                customer_accountManager = customer.optInt("accountManager", 0);
//                customer_accountManagerName = customer.optString("accountManagerName", "Unknown");
//                customer_blobUrl = customer.optString("blobUrl", "");
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//            Log.e("GlobalVars", "Error updating device details: " + e.getMessage());
//        }
//    }
//
//    public static GlobalVars getInstance() {
//        if (instance == null) {
//            instance = new GlobalVars();
//        }
//        return instance;
//    }
//
//    public boolean isInternalCameraConnected() {
//        return isInternalCameraConnected;
//    }
//
//    public void setInternalCameraConnected(boolean connected) {
//        this.isInternalCameraConnected = connected;
//    }
//
//    public boolean isUSBCameraConnected() {
//        return isUSBCameraConnected;
//    }
//
//    public void setUSBCameraConnected(boolean connected) {
//        this.isUSBCameraConnected = connected;
//    }
//
//    public boolean isCameraPreviewActive() {
//        return isCameraPreviewActive;
//    }
//
//    public void setCameraPreviewActive(boolean active) {
//        this.isCameraPreviewActive = active;
//    }
//
//    public void setKeyboardConnected(boolean connected) {
//        this.keyboardConnected = connected;
//    }
//
//    public boolean isKeyboardConnected() {
//        return keyboardConnected;
//    }
//
//    // Getters and setters for Berry sensors
//    public boolean isSpO2Connected() {
//        return isSpO2Connected;
//    }
//
//    public void setSpO2Connected(boolean connected) {
//        this.isSpO2Connected = connected;
//    }
//
//    public boolean isECGConnected() {
//        return isECGConnected;
//    }
//
//    public void setECGConnected(boolean connected) {
//        this.isECGConnected = connected;
//    }
//
//    public boolean isNIBPConnected() {
//        return isNIBPConnected;
//    }
//
//    public void setNIBPConnected(boolean connected) {
//        this.isNIBPConnected = connected;
//    }
//
//    public boolean isTempConnected() {
//        return isTempConnected;
//    }
//
//    public void setTempConnected(boolean connected) {
//        this.isTempConnected = connected;
//    }
//
//    //
//    public static void setVitalOn(boolean value) {
//        isVitalOn = value;
//        Log.d(TAG, "isVitalOn set to: " + isVitalOn);
//    }
//
//
//    public static void setCam1On(boolean value) {
//        isCam1On = value;
//    }
//
//    public static boolean isCam1On() {
//        return isCam1On;
//    }
//
//    public static void setCam2On(boolean value) {
//        isCam2On = value;
//    }
//
//    public static boolean isCam2On() {
//        return isCam2On;
//    }
//
//}
