package com.lztek.api.demo;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

import org.jetbrains.annotations.NotNull;

@Entity(tableName = "sessions")
public class Session {
    @PrimaryKey(autoGenerate = true)
    @NotNull
    public int id;

    @ColumnInfo(name = "session_name")
    public String sessionName;

    @ColumnInfo(name = "patient_name")
    public String patientName;

    @ColumnInfo(name = "location_name")
    public String locationName;

    @ColumnInfo(name = "date_time")
    public String dateTime;

    @ColumnInfo(name = "status")
    public String status;

    public Session(String sessionName, String patientName, String locationName, String dateTime, String status) {
        this.sessionName = sessionName;
        this.patientName = patientName;
        this.locationName = locationName;
        this.dateTime = dateTime;
        this.status = status;
    }

    public Session() {}
}