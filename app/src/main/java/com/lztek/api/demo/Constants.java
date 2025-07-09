package com.lztek.api.demo;

public class Constants {

    // Base API URLs
    public static final String BASE_URL_DEV = "https://hmsapidev.itxcareportal.com/"; // Docker_Dev
    public static final String BASE_URL_QA = "https://hmsapiqa.itxcareportal.com/";  // Docker_Qa

    // API Endpoints
    public static final String LOGIN_ENDPOINT = BASE_URL_QA + "login";
    public static final String GET_ALL_DEVICES = BASE_URL_QA + "devices/getalldevices";
    public static final String GET_DEVICE_INSTANCE = BASE_URL_QA + "get/deviceinstance/{deviceId}";
    public static final String UPDATE_DEVICE_INSTANCE = BASE_URL_QA + "update/device/{device_Id}";
    public static final String PARAMEDIC_MY_PROFILE = BASE_URL_QA + "account/paramedic/myprofile";
    public static final String UPDATE_PROFILE = BASE_URL_QA + "account/paramedic/updateprofile";

    public static final String APPOINTMENT_LIST = BASE_URL_QA + "paramedic/appointment/list/{id}";

    public static final String APPOINTMENT_DETAILS = BASE_URL_QA + "paramedic/appointmentbyid/{id}";
    public static final String BOOK_APPOINTMENT = BASE_URL_QA + "patient/appointment/booking";
    public static final String CONFIRM_APPOINTMENT = BASE_URL_QA + "appointment/confirmed/{appointmentId}";
    public static final String END_APPOINTMENT = BASE_URL_QA + "paramedic/appointment/end";
    public static final String DEVICE_BY_HARDWARE_ID = BASE_URL_QA + "devicebyhardwareid";

    public static  final  String STARTMEETING = BASE_URL_QA + "paramedic/appointment/start";

    // API Query Parameters
    public static final String PAGE_QUERY = "page";
    public static final String PAGE_SIZE_QUERY = "pageSize";
    public static final String SORT_BY_QUERY = "sortBy";
    public static final String SORT_TYPE_QUERY = "sortType";
    public static final String STATUS_QUERY = "status";
    public static final String START_DATE_QUERY = "startDate";
    public static final String END_DATE_QUERY = "endDate";

    // Tokens and Keys
    public static final String API_KEY = "META_ORANGE_COMMUNICATION_SECRET"; // Example API key
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String APP_TENANT_HEADER = "App-Tenant";

    // Default Values
    public static final String DEFAULT_PAGE_SIZE = "10";
    public static final String DEFAULT_SORT_BY = "date";
    public static final String DEFAULT_SORT_TYPE = "DESC";

    // App Info
    public static final String APP_VERSION = "1.0.0";
}
