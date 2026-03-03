package com.attendance.server;

import com.attendance.utils.ConfigLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AdminAuthManager {
    private static final String DEFAULT_CREDENTIAL_FILE = "data/admin_credentials.csv";
    private static final long DEFAULT_SESSION_HOURS = 8L;
    private static final Map<String, String> CREDENTIALS = new ConcurrentHashMap<>();
    private static final Map<String, SessionInfo> SESSIONS = new ConcurrentHashMap<>();

    private AdminAuthManager() {
    }

    public static synchronized void loadCredentials() {
        CREDENTIALS.clear();
        Path credentialsFile = Paths.get(getCredentialFilePath());
        if (!Files.exists(credentialsFile)) {
            System.err.println("Admin credentials file not found: " + credentialsFile.toAbsolutePath());
            return;
        }

        try {
            List<String> lines = Files.readAllLines(credentialsFile, StandardCharsets.UTF_8);
            for (String rawLine : lines) {
                String line = rawLine == null ? "" : rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split(",", 2);
                if (parts.length < 2) {
                    continue;
                }

                String username = parts[0].trim();
                String password = parts[1].trim();
                if (username.isEmpty() || password.isEmpty()) {
                    continue;
                }
                if ("name".equalsIgnoreCase(username) || "username".equalsIgnoreCase(username)) {
                    continue;
                }
                CREDENTIALS.put(username.toLowerCase(Locale.ROOT), password);
            }
            System.out.println("Loaded " + CREDENTIALS.size() + " admin credential(s)");
        } catch (IOException e) {
            System.err.println("Failed to read admin credentials: " + e.getMessage());
        }
    }

    public static boolean hasAnyCredentials() {
        return !CREDENTIALS.isEmpty();
    }

    public static String createSession(String username, String password) {
        String normalizedUser = safe(username).toLowerCase(Locale.ROOT);
        String expectedPassword = CREDENTIALS.get(normalizedUser);
        if (expectedPassword == null || !expectedPassword.equals(password == null ? "" : password)) {
            return null;
        }

        String sessionId = UUID.randomUUID().toString().replace("-", "");
        long expiresAt = Instant.now().toEpochMilli() + (getSessionHours() * 60L * 60L * 1000L);
        SESSIONS.put(sessionId, new SessionInfo(normalizedUser, expiresAt));
        return sessionId;
    }

    public static String getUsernameForSession(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return null;
        }

        SessionInfo info = SESSIONS.get(sessionId);
        if (info == null) {
            return null;
        }
        if (info.expiresAt < Instant.now().toEpochMilli()) {
            SESSIONS.remove(sessionId);
            return null;
        }
        return info.username;
    }

    public static void invalidateSession(String sessionId) {
        if (sessionId != null) {
            SESSIONS.remove(sessionId);
        }
    }

    private static String getCredentialFilePath() {
        String configured = ConfigLoader.get("admin.credentials.file");
        if (configured == null || configured.trim().isEmpty()) {
            return DEFAULT_CREDENTIAL_FILE;
        }
        return configured.trim();
    }

    private static long getSessionHours() {
        String raw = ConfigLoader.get("admin.session.hours");
        if (raw == null || raw.trim().isEmpty()) {
            return DEFAULT_SESSION_HOURS;
        }
        try {
            long value = Long.parseLong(raw.trim());
            return value <= 0 ? DEFAULT_SESSION_HOURS : value;
        } catch (NumberFormatException ignored) {
            return DEFAULT_SESSION_HOURS;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static class SessionInfo {
        private final String username;
        private final long expiresAt;

        private SessionInfo(String username, long expiresAt) {
            this.username = username;
            this.expiresAt = expiresAt;
        }
    }
}
