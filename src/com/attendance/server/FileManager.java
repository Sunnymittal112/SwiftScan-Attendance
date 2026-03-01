package com.attendance.server;

import com.attendance.models.AttendanceRecord;
import com.attendance.models.Student;
import com.attendance.utils.ConfigLoader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FileManager {
    private static final String STUDENTS_FILE = "data/students.csv";
    private static final String ATTENDANCE_DIR = "data/attendance/";

    private static final Map<String, Student> studentsMap = new HashMap<>();
    private static final Set<String> todayAttendance = new HashSet<>();
    private static final List<AttendanceRecord> todayRecords = new ArrayList<>();

    static {
        loadStudents();
        loadTodayAttendance();
    }

    private static void loadStudents() {
        try (BufferedReader br = new BufferedReader(new FileReader(STUDENTS_FILE))) {
            String line;
            br.readLine(); // header
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 3) {
                    Student student = new Student(parts[0].trim(), parts[1].trim(), parts[2].trim());
                    studentsMap.put(parts[0].trim(), student);
                }
            }
            System.out.println("Loaded " + studentsMap.size() + " students");
        } catch (IOException e) {
            System.err.println("Error loading students: " + e.getMessage());
        }
    }

    private static void loadTodayAttendance() {
        String filename = ATTENDANCE_DIR + LocalDate.now() + ".csv";
        File file = new File(filename);
        if (!file.exists()) {
            System.out.println("Starting fresh attendance for " + LocalDate.now());
            return;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 4) {
                    String rollNo = parts[0].trim();
                    String name = parts[1].trim();
                    String timestamp = parts[2].trim();
                    String status = parts[3].trim();

                    todayAttendance.add(rollNo);
                    try {
                        LocalDateTime dt = LocalDateTime.parse(timestamp, formatter);
                        todayRecords.add(new AttendanceRecord(rollNo, name, dt, status));
                    } catch (Exception ignored) {
                    }
                }
            }
            System.out.println("Found " + todayAttendance.size() + " existing records for today");
        } catch (IOException e) {
            System.err.println("Error loading today's attendance: " + e.getMessage());
        }
    }

    public static synchronized String markAttendance(String rollNo, String name, String studentClass) {
        if (todayAttendance.contains(rollNo)) {
            return "DUPLICATE";
        }

        Student student = studentsMap.get(rollNo);
        if (student == null) {
            student = new Student(rollNo, name, studentClass);
        }

        LocalTime now = LocalTime.now();
        String[] timeParts = ConfigLoader.get("late.time.threshold").split(":");
        LocalTime threshold = LocalTime.of(Integer.parseInt(timeParts[0]), Integer.parseInt(timeParts[1]));
        String status = now.isAfter(threshold) ? "LATE" : "PRESENT";

        String finalName = (student.getName() == null || student.getName().trim().isEmpty()) ? name : student.getName();
        AttendanceRecord record = new AttendanceRecord(rollNo, finalName, status);

        String filename = ATTENDANCE_DIR + LocalDate.now() + ".csv";
        try {
            new File(ATTENDANCE_DIR).mkdirs();
            try (FileWriter fw = new FileWriter(filename, true)) {
                fw.write(record.toCSV() + "\n");
            }

            todayAttendance.add(rollNo);
            todayRecords.add(record);
            return "SUCCESS";
        } catch (IOException e) {
            System.err.println("File write error: " + e.getMessage());
            return "ERROR";
        }
    }

    public static int getTodayCount() {
        return todayAttendance.size();
    }

    public static int getTotalStudents() {
        return studentsMap.size();
    }

    public static List<AttendanceRecord> getTodayRecords() {
        return new ArrayList<>(todayRecords);
    }
}