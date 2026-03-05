package com.attendance.utils;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class ConfigLoader {
    private static final Properties properties = new Properties();

    static {
        try {
            properties.load(new FileInputStream("data/config.properties"));
            System.out.println("Config loaded successfully");
        } catch (IOException e) {
            System.err.println("Using default config");
            properties.setProperty("server.port", "9000");
            properties.setProperty("qr.refresh.interval", "5000");
            properties.setProperty("encryption.secret", "8755340335");
            properties.setProperty("late.time.threshold", "10:30");
            properties.setProperty("admin.credentials.file", "data/admin_credentials.csv");
            properties.setProperty("admin.session.hours", "8");
        }
    }

    public static String get(String key) {
        return properties.getProperty(key);
    }

    public static int getInt(String key) {
        return Integer.parseInt(properties.getProperty(key));
    }
}
