package com.attendance.server;

import com.attendance.utils.ConfigLoader;
import com.attendance.utils.TokenEncryption;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class QRTokenManager {
    private static String currentToken = "";
    private static long tokenGeneratedAt = 0;
    private static final long TOKEN_VALIDITY = ConfigLoader.getInt("qr.refresh.interval");
    
    // Track multiple valid tokens (last 10) - so recent QRs still work
    private static final Map<String, Long> validTokens = new ConcurrentHashMap<>();
    private static final Set<String> usedTokens = ConcurrentHashMap.newKeySet();
    
    // Flag to control if QR should be displayed
    private static volatile boolean qrEnabled = true;
    
    public static synchronized String generateNewToken() {
        long timestamp = Instant.now().toEpochMilli();
        String rawToken = timestamp + "|" + (int)(Math.random() * 100000);
        currentToken = TokenEncryption.encrypt(rawToken);
        tokenGeneratedAt = timestamp;
        
        // Add to valid tokens with timestamp
        validTokens.put(currentToken, timestamp);
        
        // Cleanup old tokens (keep only last 10 and not older than 10 minutes)
        cleanupOldTokens();
        
        System.out.println("New QR Token Generated: " + currentToken.substring(0, 10) + "...");
        return currentToken;
    }
    
    private static void cleanupOldTokens() {
        long now = Instant.now().toEpochMilli();
        long maxAge = 10 * 60 * 1000; // 10 minutes
        
        // Remove tokens older than 10 minutes
        validTokens.entrySet().removeIf(entry -> (now - entry.getValue()) > maxAge);
        
        // If still more than 10 tokens, keep only recent 10
        if (validTokens.size() > 10) {
            // Sort by timestamp and keep only 10 most recent
            validTokens.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .skip(10)
                .forEach(e -> validTokens.remove(e.getKey()));
        }
    }
    
    public static synchronized boolean validateToken(String token) {
        if (token == null) {
            System.out.println("Null token received");
            return false;
        }
        
        // Check if token was already used
        if (usedTokens.contains(token)) {
            System.out.println("Token already used");
            return false;
        }
        
        // Check if token is in valid tokens list
        Long generatedAt = validTokens.get(token);
        if (generatedAt == null) {
            System.out.println("Invalid token - not in valid tokens list");
            return false;
        }
        
        // Time-based validation (backup)
        long currentTime = Instant.now().toEpochMilli();
        boolean isValid = (currentTime - generatedAt) <= TOKEN_VALIDITY;
        
        if (!isValid) {
            System.out.println("Token expired (time-based)");
            validTokens.remove(token); // Remove expired token
        }
        
        return isValid;
    }
    
    // Mark token as used after successful attendance
    public static synchronized void markTokenAsUsed(String token) {
        if (token != null) {
            usedTokens.add(token);
            validTokens.remove(token);
            System.out.println("Token marked as used (attendance marked successfully)");
            
            // Generate new token for next student
            generateNewToken();
        }
    }
    
    public static String getCurrentToken() {
        if (currentToken.isEmpty() || !validTokens.containsKey(currentToken)) {
            generateNewToken();
        }
        return currentToken;
    }
    
    // QR Enable/Disable controls
    public static boolean isQREnabled() {
        return qrEnabled;
    }
    
    public static void setQREnabled(boolean enabled) {
        qrEnabled = enabled;
        System.out.println("QR Display " + (enabled ? "ENABLED" : "DISABLED"));
    }
    
    public static void toggleQR() {
        qrEnabled = !qrEnabled;
        System.out.println("QR Display " + (qrEnabled ? "ENABLED" : "DISABLED"));
    }
}
