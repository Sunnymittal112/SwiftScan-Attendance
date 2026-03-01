package com.attendance.server;

import com.attendance.models.AttendanceRecord;
import com.attendance.utils.ConfigLoader;
import com.attendance.utils.QRCodeGenerator;
import fi.iki.elonen.NanoHTTPD;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AttendanceServer extends NanoHTTPD {
    private static final int PORT = ConfigLoader.getInt("server.port");
    private static String serverIP;

    public AttendanceServer() throws IOException {
        super(PORT);
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        serverIP = InetAddress.getLocalHost().getHostAddress();

        System.out.println("\n" + "=".repeat(60));
        System.out.println("SMART QR ATTENDANCE SYSTEM");
        System.out.println("=".repeat(60));
        System.out.println("Server IP: " + serverIP);
        System.out.println("Port: " + PORT);
        System.out.println("QR Display: http://" + serverIP + ":" + PORT + "/qr");
        System.out.println("Dashboard: http://" + serverIP + ":" + PORT + "/dashboard");
        System.out.println("Student Scanner: http://" + serverIP + ":" + PORT + "/student");
        System.out.println("=".repeat(60) + "\n");
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
        switch (uri) {
            case "/":
                response = newFixedLengthResponse(Response.Status.OK, "text/html", getHomePage());
                break;
            case "/qr":
                response = newFixedLengthResponse(Response.Status.OK, "text/html", getQRPage());
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
                response = newFixedLengthResponse(Response.Status.OK, "text/html", getDashboard());
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
        if (!isLanClient(session)) {
            return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/html",
                    getErrorPage("LAN Access Only", "This attendance server accepts LAN/private network requests only."));
        }

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
                        getErrorPage("Already Submitted", "This device has already submitted attendance today."));
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
                // Mark token as used after successful attendance - this invalidates the QR
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

    private String getQRPage() {
        // If QR is disabled, show stopped page
        if (!QRTokenManager.isQREnabled()) {
            return getQRStoppedPage();
        }
        
        String token = QRTokenManager.getCurrentToken();
        return QRCodeGenerator.generateQRHTMLWithControls("http://" + serverIP + ":" + PORT, token, true);
    }
    
    private String getQRStoppedPage() {
        return "<!DOCTYPE html><html><head>"
                + "<meta charset='UTF-8'>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1.0'>"
                + "<title>QR Attendance - Stopped</title>"
                + "<style>"
                + "body { display:flex; justify-content:center; align-items:center; "
                + "height:100vh; background:linear-gradient(135deg, #1f2937 0%, #4b5563 100%); "
                + "margin:0; flex-direction:column; font-family:Arial; color:#fff; text-align:center; }"
                + ".stopped-box { background:#dc2626; padding:40px 60px; border-radius:20px; "
                + "box-shadow:0 20px 60px rgba(0,0,0,0.5); }"
                + "h1 { font-size:48px; margin-bottom:20px; }"
                + "p { font-size:24px; margin-bottom:30px; }"
                + ".btn { display:inline-block; padding:15px 40px; background:#16a34a; color:#fff; "
                + "text-decoration:none; border-radius:10px; font-size:20px; font-weight:bold; "
                + "border:none; cursor:pointer; }"
                + ".btn:hover { background:#15803d; }"
                + "</style></head><body>"
                + "<div class='stopped-box'>"
                + "<h1>QR SCANNER STOPPED</h1>"
                + "<p>Attendance marking is currently closed</p>"
                + "<button class='btn' onclick='startQR()'>START QR SCANNER</button>"
                + "</div>"
                + "<script>"
                + "function startQR() {"
                + "  fetch('/qr/start').then(() => location.reload());"
                + "}"
                + "</script>"
                + "</body></html>";
    }

    private String getStudentScannerPage() {
        Path scannerPath = Path.of("web", "student", "index.html");
        try {
            return Files.readString(scannerPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return getErrorPage("Missing Student Page", "web/student/index.html not found.");
        }
    }

    private String getHomePage() {
        String qrStatus = QRTokenManager.isQREnabled() ? "ENABLED" : "DISABLED";
        String qrColor = QRTokenManager.isQREnabled() ? "#16a34a" : "#dc2626";
        
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
                + ".info{background:#f3f4f6;padding:15px;border-radius:10px;margin-top:20px;}"
                + ".info h3{margin-bottom:10px;color:#111827;}"
                + ".info ul{margin-left:20px;color:#4b5563;}"
                + ".info li{margin-bottom:5px;}"
                + "</style></head><body>"
                + "<div class='card'>"
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
                + "<div class='info'>"
                + "<h3>How it works:</h3>"
                + "<ul>"
                + "<li>QR code stays SAME until someone marks attendance</li>"
                + "<li>After attendance is marked, QR automatically changes</li>"
                + "<li>Old QR becomes invalid immediately after use</li>"
                + "<li>Click 'NEW QR' to manually generate fresh QR</li>"
                + "</ul>"
                + "</div>"
                + "</div>"
                + "<script>"
                + "function toggleQR() { fetch('/qr/toggle').then(() => location.reload()); }"
                + "function refreshQR() { fetch('/qr/refresh').then(() => { alert('New QR generated!'); location.reload(); }); }"
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

    private String getDashboard() {
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
                + ".qr-status{display:inline-block;padding:6px 12px;border-radius:6px;background:" + qrColor + ";color:#fff;font-weight:bold;margin-left:10px;}"
                + ".stats{display:grid;grid-template-columns:repeat(auto-fit,minmax(170px,1fr));gap:12px;margin-bottom:16px;}"
                + ".card{background:#fff;border-radius:10px;padding:14px;box-shadow:0 4px 16px rgba(0,0,0,.08);}"
                + "table{width:100%;background:#fff;border-radius:10px;overflow:hidden;border-collapse:collapse;}"
                + "th{background:#2563eb;color:#fff;padding:11px;text-align:left;}"
                + "td{padding:11px;border-bottom:1px solid #e5e7eb;}"
                + "</style></head><body>"
                + "<div class='header'><h1>Today's Attendance Dashboard <span class='qr-status'>QR: " + qrStatus + "</span></h1><p>Auto-refresh every 5 seconds.</p></div>"
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
        if ("::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "127.0.0.1".equals(ip)) {
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

    public static void main(String[] args) {
        try {
            // Generate initial token
            QRTokenManager.generateNewToken();

            // NO AUTO-REFRESH - Token stays same until used or manually refreshed
            System.out.println("QR Token will remain valid until attendance is marked.");

            new AttendanceServer();
        } catch (IOException e) {
            System.err.println("Server failed to start: " + e.getMessage());
        }
    }
}
