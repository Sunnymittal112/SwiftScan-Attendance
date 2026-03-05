package com.attendance.models;

import java.time.LocalDateTime;

public class AttendanceRecord {

    private String rollNo;
    private String name;
    private String status;
    private LocalDateTime timestamp;

    public AttendanceRecord(String rollNo, String name, String status) {
        this.rollNo = rollNo;
        this.name = name;
        this.status = status;
        this.timestamp = LocalDateTime.now();
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