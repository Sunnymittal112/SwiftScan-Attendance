package com.attendance.models;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class AttendanceRecord {
    private final String rollNo;
    private final String name;
    private final LocalDateTime timestamp;
    private final String status;

    public AttendanceRecord(String rollNo, String name, String status) {
        this(rollNo, name, LocalDateTime.now(), status);
    }

    public AttendanceRecord(String rollNo, String name, LocalDateTime timestamp, String status) {
        this.rollNo = rollNo;
        this.name = name;
        this.timestamp = timestamp;
        this.status = status;
    }

    public String toCSV() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return rollNo + "," + sanitize(name) + "," + timestamp.format(formatter) + "," + status;
    }

    private String sanitize(String value) {
        return value == null ? "" : value.replace(",", " ").trim();
    }

    public String getRollNo() {
        return rollNo;
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}