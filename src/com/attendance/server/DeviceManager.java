package com.attendance.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class DeviceManager {
    private static final String DEVICE_DIR = "data/devices/";
    private static final Map<String, String> todayDevices = new HashMap<>();

    static {
        loadTodayDevices();
    }

    private static String getTodayFile() {
        return DEVICE_DIR + LocalDate.now() + ".csv";
    }

    private static void loadTodayDevices() {
        File file = new File(getTodayFile());
        if (!file.exists()) {
            return;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",", 2);
                if (parts.length == 2) {
                    todayDevices.put(parts[0].trim(), parts[1].trim());
                }
            }
        } catch (IOException e) {
            System.err.println("?? Error loading device registry: " + e.getMessage());
        }
    }

    public static synchronized boolean hasSubmittedToday(String fingerprint) {
        return fingerprint != null && todayDevices.containsKey(fingerprint);
    }

    public static synchronized void registerSubmission(String fingerprint, String rollNo) {
        if (fingerprint == null || fingerprint.isEmpty()) {
            return;
        }

        new File(DEVICE_DIR).mkdirs();
        todayDevices.put(fingerprint, rollNo == null ? "" : rollNo);

        try (FileWriter fw = new FileWriter(getTodayFile(), true)) {
            fw.write(fingerprint + "," + (rollNo == null ? "" : rollNo) + "\n");
        } catch (IOException e) {
            System.err.println("?? Error saving device fingerprint: " + e.getMessage());
        }
    }

    public static synchronized int getDeviceCountToday() {
        return todayDevices.size();
    }
}