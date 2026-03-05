package com.attendance.server;

import java.time.LocalDate;
import java.time.LocalTime;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import com.attendance.models.AttendanceRecord;

public class FileManager {

    // MARK ATTENDANCE
public static synchronized String markAttendance(String rollNo, String name, String studentClass) {
    try {

        Connection conn = DatabaseManager.connect();

        String sql = "INSERT INTO attendance(student_id,class_id,date,time,status,name) VALUES(?,?,?,?,?,?)";

        PreparedStatement stmt = conn.prepareStatement(sql);

        stmt.setString(1, rollNo);
        stmt.setString(2, studentClass);
        stmt.setDate(3, java.sql.Date.valueOf(java.time.LocalDate.now()));
        stmt.setTime(4, java.sql.Time.valueOf(java.time.LocalTime.now()));
        stmt.setString(5, "PRESENT");
        stmt.setString(6, name);

        stmt.executeUpdate();

        stmt.close();
        conn.close();

        return "SUCCESS";

    } catch (Exception e) {
        e.printStackTrace();
        return "ERROR";
    }
}

    // TOTAL STUDENTS
    public static int getTotalStudents() {

        int total = 0;

        try {

            Connection conn = DatabaseManager.connect();

            String sql = "SELECT COUNT(*) FROM students";

            PreparedStatement stmt = conn.prepareStatement(sql);

            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                total = rs.getInt(1);
            }

            rs.close();
            stmt.close();
            conn.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return total;
    }

    // TODAY PRESENT COUNT
    public static int getTodayCount() {

        int total = 0;

        try {

            Connection conn = DatabaseManager.connect();

            String sql = "SELECT COUNT(*) FROM attendance WHERE date = CURDATE()";

            PreparedStatement stmt = conn.prepareStatement(sql);

            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                total = rs.getInt(1);
            }

            rs.close();
            stmt.close();
            conn.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return total;
    }

    // TODAY RECORDS
    public static List<AttendanceRecord> getTodayRecords() {

        List<AttendanceRecord> records = new ArrayList<>();

        try {

            Connection conn = DatabaseManager.connect();

            String sql = "SELECT student_id,status FROM attendance WHERE date = CURDATE()";

            PreparedStatement stmt = conn.prepareStatement(sql);

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {

                String rollNo = rs.getString("student_id");
                String status = rs.getString("status");

                records.add(new AttendanceRecord(rollNo, "", status));

            }

            rs.close();
            stmt.close();
            conn.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return records;
    }

}