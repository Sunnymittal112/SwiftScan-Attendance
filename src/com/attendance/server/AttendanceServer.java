package com.attendance.server;

import com.attendance.models.AttendanceRecord;
import com.attendance.utils.ConfigLoader;
import com.attendance.utils.QRCodeGenerator;
import fi.iki.elonen.NanoHTTPD;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class AttendanceServer extends NanoHTTPD {
    private static final int PORT = ConfigLoader.getInt("server.port");
    private static final Set<String> AUTH_REQUIRED_ROUTES = new HashSet<>(Arrays.asList(
            "/", "/qr/start", "/qr/stop", "/qr/toggle", "/qr/refresh", "/dashboard", "/api/stats"
    ));
    private static final String serverIP = "YOUR IP HERE"; 
    

    public AttendanceServer() throws IOException {
        super(PORT);
        AdminAuthManager.loadCredentials();
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);

        System.out.println("\n" + repeat("=", 60));
        System.out.println("SMART QR ATTENDANCE SYSTEM");
        System.out.println(repeat("=", 60));
        System.out.println("Server IP: " + serverIP);
        System.out.println("Port: " + PORT);
        System.out.println("QR Display: http://" + serverIP + ":" + PORT + "/qr");
        System.out.println("Dashboard: http://" + serverIP + ":" + PORT + "/dashboard");
        System.out.println("Student Scanner: http://" + serverIP + ":" + PORT + "/student");
        System.out.println("Admin Login: http://" + serverIP + ":" + PORT + "/login");
        System.out.println(repeat("=", 60) + "\n");
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Map<String, String> params = new HashMap<>();

        try {
            session.parseBody(params);
        } catch (Exception ignored) {
        }
        params.putAll(session.getParms());

        if (Method.OPTIONS.equals(session.getMethod())) {
            Response options = newFixedLengthResponse(Response.Status.OK, "text/plain", "OK");
            return withCorsHeaders(options);
        }
        Response response;
        String username = getAuthenticatedUser(session);

        if (AUTH_REQUIRED_ROUTES.contains(uri) && username == null) {
            if (uri.startsWith("/qr/") || "/api/stats".equals(uri)) {
                response = newFixedLengthResponse(Response.Status.UNAUTHORIZED, "application/json",
                        "{\"error\":\"unauthorized\",\"message\":\"Login required\"}");
                return withCorsHeaders(response);
            }
            response = redirectToLogin(uri);
            return withCorsHeaders(response);
        }

        switch (uri) {
            case "/":
                response = newFixedLengthResponse(Response.Status.OK, "text/html", getHomePage(username));
                break;
            case "/login":
                response = handleLogin(session, params);
                break;
            case "/logout":
                response = handleLogout(session);
                break;
            case "/qr":
                response = newFixedLengthResponse(Response.Status.OK, "text/html", getQRPage(username));
                break;
            case "/qr/toggle":
                QRTokenManager.toggleQR();
                response = newFixedLengthResponse(Response.Status.OK, "application/json", 
                    "{\"enabled\":" + QRTokenManager.isQREnabled() + "}");
                break;
            case "/qr/start":
                QRTokenManager.setQREnabled(true);
                response = newFixedLengthResponse(Response.Status.OK, "application/json", 
                    "{\"enabled\":true}");
                break;
            case "/qr/stop":
                QRTokenManager.setQREnabled(false);
                response = newFixedLengthResponse(Response.Status.OK, "application/json", 
                    "{\"enabled\":false}");
                break;
            case "/qr/refresh":
                QRTokenManager.generateNewToken();
                response = newFixedLengthResponse(Response.Status.OK, "application/json", 
                    "{\"refreshed\":true}");
                break;
            case "/student":
                response = newFixedLengthResponse(Response.Status.OK, "text/html", getStudentScannerPage());
                break;
            case "/mark":
                response = handleMark(session, params);
                break;
            case "/dashboard":
                response = newFixedLengthResponse(Response.Status.OK, "text/html", getDashboard(username));
                break;
            case "/api/stats":
                response = newFixedLengthResponse(Response.Status.OK, "application/json", getStats());
                break;
            default:
                response = newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "404 Not Found");
        }

        return withCorsHeaders(response);
    }

    private Response withCorsHeaders(Response response) {
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.addHeader("Access-Control-Allow-Headers", "Content-Type");
        return response;
    }

    private Response handleMark(IHTTPSession session, Map<String, String> params) {
        /*if (!isLanClient(session)) {
            return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/html",
                    getErrorPage("LAN Access Only", "This attendance server accepts LAN/private network requests only."));
        }*/
       
        String token = safe(params.get("token"));
        if (token.isEmpty()) {
            return newFixedLengthResponse(Response.Status.OK, "text/html",
                    getErrorPage("Invalid Request", "Missing attendance token."));
        }

        if (!QRTokenManager.validateToken(token)) {
            return newFixedLengthResponse(Response.Status.OK, "text/html",
                    getErrorPage("QR Code Expired", "This QR code has already been used or timed out. Please scan the latest QR code from projector."));
        }

        String headerFingerprint = getServerFingerprint(session);
        String requestFingerprint = safe(params.get("deviceFingerprint"));
        String effectiveFingerprint = requestFingerprint.isEmpty() ? headerFingerprint : requestFingerprint;

        String rollNo = safe(params.get("rollno"));
        String name = safe(params.get("name"));
        String studentClass = safe(params.get("class"));

        // Phase 1: token scan opens secure form
        if (rollNo.isEmpty() && name.isEmpty() && studentClass.isEmpty()) {
            if (DeviceManager.hasSubmittedToday(effectiveFingerprint)) {
                return newFixedLengthResponse(Response.Status.OK, "text/html",
                        getErrorPage("Already Submitted", "You Already Submitted Your Attandance."));
            }
            return newFixedLengthResponse(Response.Status.OK, "text/html", getStudentFormPage(token));
        }

        // Phase 2: form submit
        if (rollNo.isEmpty()) {
            return newFixedLengthResponse(Response.Status.OK, "text/html",
                    getErrorPage("Invalid Roll Number", "Please enter a valid roll number."));
        }

        if (DeviceManager.hasSubmittedToday(effectiveFingerprint)) {
            return newFixedLengthResponse(Response.Status.OK, "text/html",
                    getErrorPage("Device Blocked", "This phone already submitted attendance for today."));
        }

        String result = FileManager.markAttendance(rollNo.trim(), name.trim(), studentClass.trim());
        switch (result) {
            case "SUCCESS":
                QRTokenManager.markTokenAsUsed(token);
                DeviceManager.registerSubmission(effectiveFingerprint, rollNo.trim());
                return newFixedLengthResponse(Response.Status.OK, "text/html", getSuccessPage(name, rollNo));
            case "DUPLICATE":
                return newFixedLengthResponse(Response.Status.OK, "text/html",
                        getErrorPage("Already Marked", "Attendance for this roll number is already recorded today."));
            default:
                return newFixedLengthResponse(Response.Status.OK, "text/html",
                        getErrorPage("Server Error", "Could not save attendance. Please try again."));
        }
    }

    private String getQRPage(String username) {
        boolean isAuthenticated = username != null;
        if (!QRTokenManager.isQREnabled()) {
            return getQRStoppedPage(isAuthenticated, username);
        }
        
        String token = QRTokenManager.getCurrentToken();
        return QRCodeGenerator.generateQRHTMLWithControls("http://" + serverIP + ":" + PORT, token, isAuthenticated, username);
    }
    
    private String getQRStoppedPage(boolean isAuthenticated, String username) {
        String authLink = isAuthenticated
                ? "<a class='auth-btn' href='/logout'>Logout (" + escapeHtml(username) + ")</a>"
                : "<a class='auth-btn' href='/login?next=/qr'>Login</a>";
        String actionButton = isAuthenticated
                ? "<button class='btn' onclick='startQR()'>START QR SCANNER</button>"
                : "<a class='btn' href='/login?next=/qr'>LOGIN TO START QR</a>";

        return "<!DOCTYPE html><html><head>"
                + "<meta charset='UTF-8'>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1.0'>"
                + "<title>QR Attendance - Stopped</title>"
                + "<style>"
                + "body { display:flex; justify-content:center; align-items:center; "
                + "height:100vh; background:linear-gradient(135deg, #1f2937 0%, #4b5563 100%); "
                + "margin:0; flex-direction:column; font-family:Arial; color:#fff; text-align:center; }"
                + ".home { position:fixed; top:16px; left:16px; }"
                + ".home-btn { background:#1d4ed8; color:#fff; text-decoration:none; padding:10px 14px; border-radius:8px; font-weight:bold; }"
                + ".auth { position:fixed; top:16px; right:16px; }"
                + ".auth-btn { background:#0ea5e9; color:#fff; text-decoration:none; padding:10px 14px; border-radius:8px; font-weight:bold; }"
                + ".stopped-box { background:#dc2626; padding:40px 60px; border-radius:20px; "
                + "box-shadow:0 20px 60px rgba(0,0,0,0.5); }"
                + "h1 { font-size:48px; margin-bottom:20px; }"
                + "p { font-size:24px; margin-bottom:30px; }"
                + ".btn { display:inline-block; padding:15px 40px; background:#16a34a; color:#fff; "
                + "text-decoration:none; border-radius:10px; font-size:20px; font-weight:bold; "
                + "border:none; cursor:pointer; }"
                + ".btn:hover { background:#15803d; }"
                + "</style></head><body>"
                + "<div class='home'><a class='home-btn' href='/'>Home</a></div>"
                + "<div class='auth'>" + authLink + "</div>"
                + "<div class='stopped-box'>"
                + "<h1>QR SCANNER STOPPED</h1>"
                + "<p>Attendance marking is currently closed</p>"
                + actionButton
                + "</div>"
                + "<script>"
                + "function startQR() {"
                + "  fetch('/qr/start').then(function(res){"
                + "    if(res.status===401){window.location='/login?next=/qr';return;}"
                + "    location.reload();"
                + "  });"
                + "}"
                + "</script>"
                + "</body></html>";
    }

    private String getStudentScannerPage() {
        Path scannerPath = java.nio.file.Paths.get("web", "student", "index.html");
        try {
            return new String(Files.readAllBytes(scannerPath), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return getErrorPage("Missing Student Page", "web/student/index.html not found.");
        }
    }

    private String getHomePage(String username) {
        String qrStatus = QRTokenManager.isQREnabled() ? "ENABLED" : "DISABLED";
        String qrColor = QRTokenManager.isQREnabled() ? "#16a34a" : "#dc2626";
        String authArea = username == null
                ? "<a class='login-link' href='/login?next=/qr'>Admin Login</a>"
                : "<span class='login-link'>Logged in: " + escapeHtml(username) + " | <a href='/logout'>Logout</a></span>";
        
        return "<!DOCTYPE html><html><head>"
                + "<meta charset='UTF-8'>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1.0'>"
                + "<title>Smart QR Attendance</title>"
                + "<style>"
                + "*{margin:0;padding:0;box-sizing:border-box;}"
                + "body{font-family:Segoe UI,Arial;background:linear-gradient(135deg,#0f172a,#1e293b);min-height:100vh;display:flex;justify-content:center;align-items:center;padding:20px;}"
                + ".card{background:#fff;width:min(840px,100%);padding:32px;border-radius:18px;box-shadow:0 15px 40px rgba(0,0,0,.35);}"
                + "h1{font-size:34px;color:#111827;margin-bottom:18px;}"
                + ".btn{display:inline-block;margin:8px 10px 8px 0;padding:14px 22px;background:#2563eb;color:#fff;text-decoration:none;border-radius:10px;font-weight:700;cursor:pointer;border:none;}"
                + ".btn.stop{background:#dc2626;}"
                + ".btn.stop:hover{background:#b91c1c;}"
                + ".btn.start{background:#16a34a;}"
                + ".btn.start:hover{background:#15803d;}"
                + ".btn.refresh{background:#f59e0b;}"
                + ".btn.refresh:hover{background:#d97706;}"
                + ".status{display:inline-block;padding:8px 16px;border-radius:8px;font-weight:bold;margin-bottom:15px;color:#fff;background:" + qrColor + ";}"
                + "p{color:#374151;margin-top:12px;font-size:16px;}"
                + ".login{margin-bottom:10px;}"
                + ".login-link{font-size:15px;font-weight:600;color:#1d4ed8;}"
                + ".login-link a{color:#dc2626;text-decoration:none;font-weight:700;}"
                + "</style></head><body>"
                + "<div class='card'>"
                + "<div class='login'>" + authArea + "</div>"
                + "<h1>Smart QR Attendance System</h1>"
                + "<div class='status'>QR Status: " + qrStatus + "</div>"
                + "<br>"
                + "<a class='btn' href='/qr'>Show QR</a>"
                + "<a class='btn' href='/dashboard'>Dashboard</a>"
                + "<a class='btn' href='/student'>Student Scanner</a>"
                + "<button class='btn refresh' onclick='refreshQR()'>NEW QR</button>"
                + "<button class='btn " + (QRTokenManager.isQREnabled() ? "stop" : "start") + "' onclick='toggleQR()'>" + (QRTokenManager.isQREnabled() ? "STOP QR" : "START QR") + "</button>"
                + "<p><strong>Server:</strong> http://" + serverIP + ":" + PORT + "</p>"
                + "<p><strong>Total Students:</strong> " + FileManager.getTotalStudents() + "</p>"
                + "<p><strong>Today's Present:</strong> " + FileManager.getTodayCount() + "</p>"
                + "<p><strong>Blocked Devices Today:</strong> " + DeviceManager.getDeviceCountToday() + "</p>"
                + "<p><strong>Date:</strong> " + LocalDate.now() + "</p>"
                + "</div>"
                + "<script>"
                + "function toggleQR(){"
                + "fetch('/qr/toggle').then(function(res){"
                + "if(res.status===401){window.location='/login?next=/';return;}location.reload();"
                + "});}"
                + "function refreshQR(){"
                + "fetch('/qr/refresh').then(function(res){"
                + "if(res.status===401){window.location='/login?next=/';return;}"
                + "alert('New QR generated!');location.reload();"
                + "});}"
                + "</script>"
                + "</body></html>";
    }

    private String getStudentFormPage(String token) {
        return "<!DOCTYPE html><html><head>"
                + "<meta charset='UTF-8'>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1.0'>"
                + "<title>Mark Attendance</title>"
                + "<style>"
                + "body{font-family:Arial;background:linear-gradient(135deg,#0ea5e9,#2563eb);min-height:100vh;display:flex;justify-content:center;align-items:center;padding:20px;margin:0;}"
                + ".box{background:#fff;padding:26px;border-radius:14px;max-width:460px;width:100%;box-shadow:0 12px 30px rgba(0,0,0,.25);}"
                + "h1{margin-bottom:16px;font-size:26px;color:#111827;}"
                + ".warning{background:#fef3c7;border:1px solid #f59e0b;color:#92400e;padding:12px;border-radius:8px;margin-bottom:16px;font-size:14px;}"
                + "label{display:block;font-weight:700;margin:12px 0 6px;}"
                + "input{width:100%;padding:12px;border:1px solid #d1d5db;border-radius:10px;font-size:16px;}"
                + "button{margin-top:16px;width:100%;padding:14px;border:0;border-radius:10px;background:#16a34a;color:#fff;font-weight:700;font-size:17px;cursor:pointer;}"
                + "small{display:block;color:#6b7280;margin-top:12px;}"
                + "</style>"
                + "</head><body><div class='box'>"
                + "<h1>Attendance Form</h1>"
                + "<div class='warning'>Submit quickly! This QR code will become invalid after use.</div>"
                + "<form method='get' action='/mark'>"
                + "<input type='hidden' name='token' value='" + token + "'>"
                + "<input type='hidden' id='deviceFingerprint' name='deviceFingerprint'>"
                + "<label>Roll Number</label><input name='rollno' required placeholder='101'>"
                + "<label>Full Name</label><input name='name' required placeholder='Rahul Kumar'>"
                + "<label>Class</label><input name='class' required placeholder='10-A'>"
                + "<button type='submit'>Submit Attendance</button>"
                + "</form>"
                + "<small>Security: Single-use QR token (expires immediately after marking).</small>"
                + "</div>"
                + "<script>"
                + "(async function(){"
                + "const material=[navigator.userAgent,navigator.language,navigator.platform,screen.width+'x'+screen.height,new Date().getTimezoneOffset()].join('|');"
                + "const enc=new TextEncoder().encode(material);"
                + "const hash=await crypto.subtle.digest('SHA-256',enc);"
                + "const arr=Array.from(new Uint8Array(hash));"
                + "const fp=arr.map(b=>b.toString(16).padStart(2,'0')).join('');"
                + "document.getElementById('deviceFingerprint').value=fp;"
                + "})();"
                + "</script>"
                + "</body></html>";
    }

    private String getSuccessPage(String name, String rollNo) {
        String displayName = name == null || name.trim().isEmpty() ? rollNo : name;
        return "<!DOCTYPE html><html><head>"
                + "<meta charset='UTF-8'>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1.0'>"
                + "<title>Success</title>"
                + "<style>"
                + "body{background:#16a34a;color:#fff;font-family:Arial;display:flex;justify-content:center;align-items:center;height:100vh;flex-direction:column;text-align:center;margin:0;}"
                + "h1{font-size:40px;}p{font-size:18px;}"
                + "</style></head><body>"
                + "<h1>Attendance Marked</h1>"
                + "<p>Welcome, <strong>" + escapeHtml(displayName) + "</strong></p>"
                + "<p>QR Code is now invalid for reuse</p>"
                + "<p>Next student should scan the new QR</p>"
                + "<p>This window will close automatically.</p>"
                + "<script>"
                + "localStorage.setItem('attendance_submitted_date', new Date().toISOString().slice(0,10));"
                + "setTimeout(function(){window.close();window.location='about:blank';},2500);"
                + "</script>"
                + "</body></html>";
    }

    private String getErrorPage(String title, String message) {
        return "<!DOCTYPE html><html><head>"
                + "<meta charset='UTF-8'>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1.0'>"
                + "<title>Error</title>"
                + "<style>"
                + "body{background:#b91c1c;color:#fff;font-family:Arial;display:flex;justify-content:center;align-items:center;height:100vh;flex-direction:column;text-align:center;padding:20px;margin:0;}"
                + "h1{font-size:36px;margin-bottom:14px;}p{font-size:18px;max-width:560px;}"
                + "button{margin-top:22px;padding:12px 22px;border:0;border-radius:8px;cursor:pointer;font-weight:700;}"
                + "</style></head><body>"
                + "<h1>" + escapeHtml(title) + "</h1>"
                + "<p>" + escapeHtml(message) + "</p>"
                + "<button onclick='history.back()'>Go Back</button>"
                + "</body></html>";
    }

    private String getDashboard(String username) {
        List<AttendanceRecord> records = FileManager.getTodayRecords();
        StringBuilder tableRows = new StringBuilder();

        int presentCount = 0;
        int lateCount = 0;
        for (AttendanceRecord record : records) {
            if ("PRESENT".equals(record.getStatus())) {
                presentCount++;
            } else if ("LATE".equals(record.getStatus())) {
                lateCount++;
            }

            String statusColor = "LATE".equals(record.getStatus()) ? "#f59e0b" : "#16a34a";
            tableRows.append("<tr>")
                    .append("<td>").append(escapeHtml(record.getRollNo())).append("</td>")
                    .append("<td>").append(escapeHtml(record.getName())).append("</td>")
                    .append("<td>").append(record.getTimestamp().toString().substring(11, 16)).append("</td>")
                    .append("<td style='font-weight:700;color:").append(statusColor).append(";'>")
                    .append(escapeHtml(record.getStatus())).append("</td>")
                    .append("</tr>");
        }

        String qrStatus = QRTokenManager.isQREnabled() ? "ON" : "OFF";
        String qrColor = QRTokenManager.isQREnabled() ? "#16a34a" : "#dc2626";

        return "<!DOCTYPE html><html><head>"
                + "<meta charset='UTF-8'>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1.0'>"
                + "<meta http-equiv='refresh' content='5'>"
                + "<title>Dashboard</title>"
                + "<style>"
                + "body{font-family:Arial;background:#f3f4f6;padding:18px;margin:0;}"
                + ".header{background:#1d4ed8;color:#fff;padding:18px;border-radius:12px;margin-bottom:16px;}"
                + ".auth{margin-top:8px;font-size:14px;}"
                + ".auth a{color:#fef3c7;text-decoration:none;font-weight:bold;}"
                + ".qr-status{display:inline-block;padding:6px 12px;border-radius:6px;background:" + qrColor + ";color:#fff;font-weight:bold;margin-left:10px;}"
                + ".stats{display:grid;grid-template-columns:repeat(auto-fit,minmax(170px,1fr));gap:12px;margin-bottom:16px;}"
                + ".card{background:#fff;border-radius:10px;padding:14px;box-shadow:0 4px 16px rgba(0,0,0,.08);}"
                + "table{width:100%;background:#fff;border-radius:10px;overflow:hidden;border-collapse:collapse;}"
                + "th{background:#2563eb;color:#fff;padding:11px;text-align:left;}"
                + "td{padding:11px;border-bottom:1px solid #e5e7eb;}"
                + "</style></head><body>"
                + "<div class='header'><h1>Today's Attendance Dashboard <span class='qr-status'>QR: " + qrStatus + "</span></h1><p>Auto-refresh every 5 seconds.</p>"
                + "<div class='auth'>Logged in as <strong>" + escapeHtml(username) + "</strong> | <a href='/logout'>Logout</a></div></div>"
                + "<div class='stats'>"
                + "<div class='card'><strong>Total Students:</strong> " + FileManager.getTotalStudents() + "</div>"
                + "<div class='card'><strong>Present:</strong> " + presentCount + "</div>"
                + "<div class='card'><strong>Late:</strong> " + lateCount + "</div>"
                + "<div class='card'><strong>Absent:</strong> " + (FileManager.getTotalStudents() - FileManager.getTodayCount()) + "</div>"
                + "<div class='card'><strong>Blocked Devices:</strong> " + DeviceManager.getDeviceCountToday() + "</div>"
                + "</div>"
                + "<table><thead><tr><th>Roll</th><th>Name</th><th>Time</th><th>Status</th></tr></thead><tbody>"
                + tableRows
                + "</tbody></table></body></html>";
    }

    private Response handleLogin(IHTTPSession session, Map<String, String> params) {
        String next = sanitizeNextPath(params.get("next"));
        if (!AdminAuthManager.hasAnyCredentials()) {
            return newFixedLengthResponse(Response.Status.OK, "text/html",
                    getLoginPage("No admin credentials found. Add entries in data/admin_credentials.csv", next, safe(params.get("username"))));
        }

        if (!Method.POST.equals(session.getMethod())) {
            return newFixedLengthResponse(Response.Status.OK, "text/html", getLoginPage("", next, safe(params.get("username"))));
        }

        String username = safe(params.get("username"));
        String password = safe(params.get("password"));
        String sessionId = AdminAuthManager.createSession(username, password);
        if (sessionId == null) {
            return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/html",
                    getLoginPage("Invalid username or password.", next, username));
        }

        Response response = redirect(next);
        response.addHeader("Set-Cookie", "admin_session=" + sessionId + "; Path=/; HttpOnly; SameSite=Lax");
        return response;
    }

    private Response handleLogout(IHTTPSession session) {
        String sessionId = getCookieValue(session, "admin_session");
        AdminAuthManager.invalidateSession(sessionId);
        Response response = redirect("/login");
        response.addHeader("Set-Cookie", "admin_session=deleted; Path=/; Max-Age=0; HttpOnly; SameSite=Lax");
        return response;
    }

    private String getAuthenticatedUser(IHTTPSession session) {
        String sessionId = getCookieValue(session, "admin_session");
        return AdminAuthManager.getUsernameForSession(sessionId);
    }

    private Response redirectToLogin(String nextPath) {
        String encoded;
        try {
            encoded = URLEncoder.encode(sanitizeNextPath(nextPath), StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {
            encoded = "%2F";
        }
        return redirect("/login?next=" + encoded);
    }

    private Response redirect(String location) {
        Response response = newFixedLengthResponse(Response.Status.REDIRECT, "text/plain", "Redirecting...");
        response.addHeader("Location", location);
        return response;
    }

    private String getCookieValue(IHTTPSession session, String key) {
        String cookieHeader = safe(session.getHeaders().get("cookie"));
        if (cookieHeader.isEmpty()) {
            return "";
        }
        String[] parts = cookieHeader.split(";");
        for (String part : parts) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length == 2 && key.equals(pair[0].trim())) {
                return pair[1].trim();
            }
        }
        return "";
    }

    private String sanitizeNextPath(String next) {
        String value = safe(next);
        if (value.isEmpty()) {
            return "/dashboard";
        }
        if (!value.startsWith("/") || value.startsWith("//")) {
            return "/dashboard";
        }
        if (value.contains("\n") || value.contains("\r")) {
            return "/dashboard";
        }
        return value;
    }

    private String getLoginPage(String error, String next, String username) {
        String errorBox = safe(error).isEmpty() ? "" : "<div class='error'>" + escapeHtml(error) + "</div>";
        return "<!DOCTYPE html><html><head>"
                + "<meta charset='UTF-8'>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1.0'>"
                + "<title>Admin Login</title>"
                + "<style>"
                + "body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:linear-gradient(135deg,#0f172a,#1d4ed8);font-family:Arial;padding:16px;}"
                + ".home{position:fixed;top:16px;left:16px;}"
                + ".home a{background:#0ea5e9;color:#fff;text-decoration:none;font-weight:700;padding:10px 14px;border-radius:8px;display:inline-block;}"
                + ".card{width:min(420px,100%);background:#fff;border-radius:14px;padding:24px;box-shadow:0 20px 50px rgba(0,0,0,.35);}"
                + "h1{margin:0 0 10px;font-size:28px;color:#111827;}"
                + "p{margin:0 0 18px;color:#4b5563;}"
                + "label{display:block;font-weight:700;margin:12px 0 6px;color:#111827;}"
                + "input{width:100%;padding:12px;border:1px solid #d1d5db;border-radius:10px;font-size:16px;}"
                + "button{margin-top:16px;width:100%;padding:13px;border:0;border-radius:10px;background:#1d4ed8;color:#fff;font-size:16px;font-weight:700;cursor:pointer;}"
                + ".error{background:#fee2e2;border:1px solid #ef4444;color:#991b1b;padding:10px;border-radius:8px;margin-bottom:12px;}"
                + ".hint{margin-top:12px;font-size:13px;color:#6b7280;}"
                + "</style></head><body>"
                + "<div class='home'><a href='/'>Home</a></div>"
                + "<form class='card' method='post' action='/login'>"
                + "<h1>Admin Login</h1>"
                + "<p>Use credentials from data/admin_credentials.csv</p>"
                + errorBox
                + "<input type='hidden' name='next' value='" + escapeHtml(next) + "'>"
                + "<label>Username</label>"
                + "<input name='username' required value='" + escapeHtml(username) + "' placeholder='teacher1'>"
                + "<label>Password</label>"
                + "<input type='password' name='password' required placeholder='password'>"
                + "<button type='submit'>Login</button>"
                + "<div class='hint'>Authorized users can control QR and view dashboard.</div>"
                + "</form></body></html>";
    }

    private String getStats() {
        return "{\"total\":" + FileManager.getTotalStudents()
                + ",\"present\":" + FileManager.getTodayCount()
                + ",\"absent\":" + (FileManager.getTotalStudents() - FileManager.getTodayCount())
                + ",\"blockedDevices\":" + DeviceManager.getDeviceCountToday()
                + ",\"qrEnabled\":" + QRTokenManager.isQREnabled() + "}";
    }

    private String getServerFingerprint(IHTTPSession session) {
        Map<String, String> headers = session.getHeaders();
        String ip = safe(headers.get("x-forwarded-for"));
        if (ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        if (ip.isEmpty()) {
            ip = safe(headers.get("remote-addr"));
        }
        String ua = safe(headers.get("user-agent"));
        String lang = safe(headers.get("accept-language"));
        return Integer.toHexString((ip + "|" + ua + "|" + lang).hashCode());
    }

    private boolean isLanClient(IHTTPSession session) {
        String ip = safe(session.getHeaders().get("x-forwarded-for"));
        if (ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        if (ip.isEmpty()) {
            ip = safe(session.getHeaders().get("remote-addr"));
        }
        if (ip.isEmpty()) {
            return true;
        }

        ip = ip.toLowerCase(Locale.ROOT);
        if ("::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "138.252.100.198".equals(ip)) {
            return true;
        }
        if (ip.startsWith("10.") || ip.startsWith("192.168.")) {
            return true;
        }

        if (ip.startsWith("172.")) {
            String[] p = ip.split("\\.");
            if (p.length > 1) {
                try {
                    int second = Integer.parseInt(p[1]);
                    return second >= 16 && second <= 31;
                } catch (NumberFormatException ignored) {
                }
            }
        }

        return false;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String repeat(String value, int times) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < times; i++) {
            builder.append(value);
        }
        return builder.toString();
    }

    public static void main(String[] args) {
        try {
            QRTokenManager.generateNewToken();
            System.out.println("QR Token will remain valid until attendance is marked.");
            new AttendanceServer();
        } catch (IOException e) {
            System.err.println("Server failed to start: " + e.getMessage());
        }
    }
}