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
            properties.setProperty("server.port", "8080");
            properties.setProperty("qr.refresh.interval", "5000");
            properties.setProperty("encryption.secret", "MySecretKey12345");
            properties.setProperty("late.time.threshold", "09:10");
        }
    }

    public static String get(String key) {
        return properties.getProperty(key);
    }

    public static int getInt(String key) {
        return Integer.parseInt(properties.getProperty(key));
    }
}