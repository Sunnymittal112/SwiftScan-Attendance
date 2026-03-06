package com.attendance.server;

import java.sql.Connection;
import java.sql.DriverManager;

public class DatabaseManager {

    public static Connection connect() {
        try {
            String url = "jdbc:mariadb://localhost:3306/swiftscan";
            String user = "USERNAME";
            String password = "USERNAME_PASSWORD";

            return DriverManager.getConnection(url, user, password);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
