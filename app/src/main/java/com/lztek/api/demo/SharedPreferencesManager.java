package com.lztek.api.demo;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;

public class SharedPreferencesManager {

    private static final String PREFS_NAME = "GlobalVarsPrefs";

    // Save all GlobalVars data to SharedPreferences
    public static void saveGlobalVarsToPrefs(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        Gson gson = new Gson();

        // Store primitive fields
        editor.putString("accessToken", GlobalVars.getAccessToken());
        editor.putString("refreshToken", GlobalVars.getRefreshToken());
        editor.putInt("paramedicId", GlobalVars.getParamedicId());
        editor.putInt("userId", GlobalVars.getUserId());
        editor.putString("firstName", GlobalVars.getFirstName());
        editor.putString("lastName", GlobalVars.getLastName());
        editor.putString("email", GlobalVars.getEmail());
        editor.putString("address", GlobalVars.getAddress());
        editor.putString("dateOfBirth", GlobalVars.getDateOfBirth());
        editor.putString("gender", GlobalVars.getGender());
        editor.putString("phoneNumber", GlobalVars.getPhoneNumber());
        editor.putString("training", GlobalVars.getTraining());
        editor.putString("previousPositions", GlobalVars.getPreviousPositions());
        editor.putString("education", GlobalVars.getEducation());
        editor.putString("workingHours", GlobalVars.getWorkingHours());
        editor.putString("language", GlobalVars.getLanguage());
        editor.putString("expertise", GlobalVars.getExpertise());
        editor.putString("notes", GlobalVars.getNotes());
        editor.putString("yearsOfExperience", GlobalVars.getYearsOfExperience());
        editor.putString("password", GlobalVars.getPassword());
        editor.putBoolean("isActive", GlobalVars.isActive());
        editor.putString("averageRating", String.valueOf(GlobalVars.getAverageRating()));
        editor.putString("profilePicture", GlobalVars.getProfilePicture());
        editor.putString("profilePhotoUrl", GlobalVars.getProfilePhotoUrl());

        // Store device information
        editor.putString("hardwareDeviceId", GlobalVars.getHardwareDeviceId());
        editor.putString("deviceId", GlobalVars.getDeviceId());

        // Store API Response Data (Device)
        editor.putInt("devicesId", GlobalVars.getDevicesId());
        editor.putString("factoryDeviceId", GlobalVars.getFactoryDeviceId());
        editor.putString("devicesName", GlobalVars.getDevicesName());
        editor.putString("devicesUrl", GlobalVars.getDevicesUrl());
        editor.putInt("createdBy", GlobalVars.getCreatedBy());
        editor.putString("createdDate", GlobalVars.getCreatedDate());
        editor.putBoolean("deviceIsActive", GlobalVars.isDeviceIsActive());
        editor.putInt("customerId", GlobalVars.getCustomerId());
        editor.putString("modifiedDate", GlobalVars.getModifiedDate());
        editor.putString("deviceStatusName", GlobalVars.getDeviceStatusName());
        editor.putString("clientId", GlobalVars.getClientId());

        // Store API Response Data (Customer)
        editor.putString("customer_companyName", GlobalVars.getCustomerCompanyName());
        editor.putString("customer_companyLogo", GlobalVars.getCustomerCompanyLogo());
        editor.putString("customer_companyAddress", GlobalVars.getCustomerCompanyAddress());
        editor.putString("customer_companyContact", GlobalVars.getCustomerCompanyContact());
        editor.putString("customer_contactPersonName", GlobalVars.getCustomerContactPersonName());
        editor.putString("customer_contactEmail", GlobalVars.getCustomerContactEmail());
        editor.putString("customer_contactPhone", GlobalVars.getCustomerContactPhone());
        editor.putInt("customer_accountStatus", GlobalVars.getCustomerAccountStatus());
        editor.putString("customer_accountStatusName", GlobalVars.getCustomerAccountStatusName());
        editor.putInt("customer_accountManager", GlobalVars.getCustomerAccountManager());
        editor.putString("customer_accountManagerName", GlobalVars.getCustomerAccountManagerName());
        editor.putString("customer_blobUrl", GlobalVars.getCustomerBlobUrl());

        // Commit the changes
        editor.apply();
    }

    // Load all GlobalVars data from SharedPreferences
    public static void loadGlobalVarsFromPrefs(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        GlobalVars.setAccessToken(prefs.getString("accessToken", null));
        GlobalVars.setRefreshToken(prefs.getString("refreshToken", null));
        GlobalVars.setParamedicId(prefs.getInt("paramedicId", 0));
        GlobalVars.setUserId(prefs.getInt("userId", 0));
        GlobalVars.setFirstName(prefs.getString("firstName", null));
        GlobalVars.setLastName(prefs.getString("lastName", null));
        GlobalVars.setEmail(prefs.getString("email", null));
        GlobalVars.setAddress(prefs.getString("address", null));
        GlobalVars.setDateOfBirth(prefs.getString("dateOfBirth", null));
        GlobalVars.setGender(prefs.getString("gender", null));
        GlobalVars.setPhoneNumber(prefs.getString("phoneNumber", null));
        GlobalVars.setTraining(prefs.getString("training", null));
        GlobalVars.setPreviousPositions(prefs.getString("previousPositions", null));
        GlobalVars.setEducation(prefs.getString("education", null));
        GlobalVars.setWorkingHours(prefs.getString("workingHours", null));
        GlobalVars.setLanguage(prefs.getString("language", null));
        GlobalVars.setExpertise(prefs.getString("expertise", null));
        GlobalVars.setNotes(prefs.getString("notes", null));
        GlobalVars.setYearsOfExperience(prefs.getString("yearsOfExperience", null));
        GlobalVars.setPassword(prefs.getString("password", null));
        GlobalVars.setIsActive(prefs.getBoolean("isActive", false));
        GlobalVars.setAverageRating(Double.valueOf(prefs.getString("averageRating", "0")));
        GlobalVars.setProfilePicture(prefs.getString("profilePicture", null));
        GlobalVars.setProfilePhotoUrl(prefs.getString("profilePhotoUrl", null));

        GlobalVars.setHardwareDeviceId(prefs.getString("hardwareDeviceId", null));
        GlobalVars.setDeviceId(prefs.getString("deviceId", null));

        GlobalVars.setDevicesId(prefs.getInt("devicesId", 0));
        GlobalVars.setFactoryDeviceId(prefs.getString("factoryDeviceId", null));
        GlobalVars.setDevicesName(prefs.getString("devicesName", null));
        GlobalVars.setDevicesUrl(prefs.getString("devicesUrl", null));
        GlobalVars.setCreatedBy(prefs.getInt("createdBy", 0));
        GlobalVars.setCreatedDate(prefs.getString("createdDate", null));
        GlobalVars.setDeviceIsActive(prefs.getBoolean("deviceIsActive", false));
        GlobalVars.setCustomerId(prefs.getInt("customerId", 0));
        GlobalVars.setModifiedDate(prefs.getString("modifiedDate", null));
        GlobalVars.setDeviceStatusName(prefs.getString("deviceStatusName", null));
        GlobalVars.setClientId(prefs.getString("clientId", null));

        GlobalVars.setCustomerCompanyName(prefs.getString("customer_companyName", null));
        GlobalVars.setCustomerCompanyLogo(prefs.getString("customer_companyLogo", null));
        GlobalVars.setCustomerCompanyAddress(prefs.getString("customer_companyAddress", null));
        GlobalVars.setCustomerCompanyContact(prefs.getString("customer_companyContact", null));
        GlobalVars.setCustomerContactPersonName(prefs.getString("customer_contactPersonName", null));
        GlobalVars.setCustomerContactEmail(prefs.getString("customer_contactEmail", null));
        GlobalVars.setCustomerContactPhone(prefs.getString("customer_contactPhone", null));
        GlobalVars.setCustomerAccountStatus(prefs.getInt("customer_accountStatus", 0));
        GlobalVars.setCustomerAccountStatusName(prefs.getString("customer_accountStatusName", null));
        GlobalVars.setCustomerAccountManager(prefs.getInt("customer_accountManager", 0));
        GlobalVars.setCustomerAccountManagerName(prefs.getString("customer_accountManagerName", null));
        GlobalVars.setCustomerBlobUrl(prefs.getString("customer_blobUrl", null));
    }

    // Update a single value in SharedPreferences
    public static void updatePreference(Context context, String key, Object value) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        if (value instanceof String) {
            editor.putString(key, (String) value);
        } else if (value instanceof Integer) {
            editor.putInt(key, (Integer) value);
        } else if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else if (value instanceof Float) {
            editor.putFloat(key, (Float) value);
        } else if (value instanceof Long) {
            editor.putLong(key, (Long) value);
        } else {
            throw new IllegalArgumentException("Unsupported data type");
        }

        editor.apply();
    }

    public static String getPreference(Context context, String key, String defaultValue) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(key, defaultValue);
    }

    public static void removePreference(Context context, String key) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(key); // Remove the specific key
        editor.apply(); // Apply the changes asynchronously
    }

    public static void clearAllPreferences(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear(); // Clear all stored preferences
        editor.apply(); // Apply the changes asynchronously
    }

}

