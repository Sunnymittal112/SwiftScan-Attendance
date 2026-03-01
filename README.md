Enhanced System Design

Current Plan Issues:
Security Risk: Static QR codes easily shareable - proxy attendance ho sakti hai
Phone Detection: MAC address spoofing possible hai
No Authentication: Koi bhi QR scan kar sakta hai
Improved Architecture:
text

┌─────────────────────────────────────────────────────┐
│            TEACHER'S SERVER (Java)                   │
│  - Dynamic QR Generation (5 sec rotation)            │
│  - Token-based Authentication                        │
│  - Device Fingerprinting                             │
│  - Attendance File Management                        │
└─────────────────────────────────────────────────────┘
                        ▲
                        │ LAN (Socket Connection)
                        ▼
┌─────────────────────────────────────────────────────┐
│         STUDENT'S PHONE (Web Browser)                │
│  - Scan Dynamic QR from Projector/Screen             │
│  - Auto-submit with device info                      │
│  - One-time session token                            │
└─────────────────────────────────────────────────────┘
Recommended Features
1. Dynamic QR with Time-based Token
Java

// Server generates QR every 5 seconds
String token = generateToken(currentTime, secretKey);
String qrData = "http://192.168.1.100:8080/mark?token=" + token;
2. Device Fingerprinting
Java

// Detect unique device characteristics
- User-Agent
- Screen Resolution
- IP Address
- Browser Fingerprint (Canvas/WebGL)
- Session Cookie
3. Geo-fencing (Optional but Powerful)
Java

// Ensure student is physically in classroom
if (studentLocation.distanceTo(classroomLocation) > 50) {
    return "OUT_OF_RANGE";
}
4. Rate Limiting
Java

// Prevent spam submissions
Map<String, Long> lastSubmission = new HashMap<>();
if (currentTime - lastSubmission.get(deviceId) < 300000) { // 5 min
    return "COOLDOWN_ACTIVE";
}
Improved Project Structure
text

attendance-system/
│
├── server/                          # Java Server Application
│   ├── AttendanceServer.java        # Main server with Socket + HTTP
│   ├── QRGenerator.java             # Dynamic QR with encryption
│   ├── TokenManager.java            # JWT/HMAC token handling
│   ├── DeviceTracker.java           # Device fingerprint validator
│   ├── FileManager.java             # CSV/TXT file operations
│   └── config.properties            # Server settings
│
├── client-web/                      # Lightweight Web Interface
│   ├── index.html                   # Student attendance page
│   ├── scanner.js                   # QR scanning logic (HTML5-QR)
│   └── styles.css
│
├── admin-panel/                     # Teacher's Dashboard
│   ├── dashboard.html               # Real-time attendance view
│   └── reports.html                 # Export/Download logs
│
├── data/
│   ├── attendance_2024-01-15.csv    # Daily logs
│   ├── students.txt                 # Enrolled students
│   └── devices_whitelist.txt        # Approved devices
│
└── libs/
    ├── zxing-core-3.5.1.jar
    ├── javax.websocket-api.jar
    └── gson-2.10.jar
Security Enhancements
A. Encrypted QR Payload
Java

// Server side
String payload = studentId + "|" + timestamp + "|" + classId;
String encrypted = AES.encrypt(payload, SECRET_KEY);
generateQR("http://server/attend?data=" + encrypted);

// Client side decrypts and validates timestamp
if (currentTime - qrTimestamp > 5000) {
    return "QR_EXPIRED";
}
B. Device Whitelisting
Java

// First-time device registration
if (!deviceWhitelist.contains(deviceFingerprint)) {
    sendApprovalRequest(teacher);
    return "DEVICE_PENDING_APPROVAL";
}
C. Session Management

Java

// One QR scan = One session token
String sessionToken = UUID.randomUUID().toString();
activeSessions.put(sessionToken, expiryTime);
Additional Features to Add
Feature	Priority	Description
Late Entry Tracking	High	Mark students arriving after threshold time
Photo Capture	Medium	Optional webcam snapshot during attendance
Multi-Class Support	High	Different QR codes for different periods
Export to Excel	Medium	Convert TXT to XLSX with statistics
Absence Alerts	Low	Notify if student misses >3 classes
Offline Mode	Medium	Queue attendance if server disconnects

Implementation Steps
Phase 1: Core System (Week 1)
Java

✓ Basic Socket Server
✓ Static QR Generation
✓ File-based Storage
✓ Simple Web Interface
Phase 2: Security (Week 2)
Java

✓ Dynamic QR Rotation
✓ Token Authentication
✓ Device Fingerprinting
✓ Duplicate Prevention
Phase 3: Polish (Week 3)
Java

✓ Admin Dashboard
✓ Real-time Updates (WebSocket)
✓ Report Generation
✓ Error Handling

Quick Start Code Snippet
Server (AttendanceServer.java)
Java

public class AttendanceServer {
    private static final int PORT = 8080;
    private static String currentQRToken;
    
    public static void main(String[] args) throws IOException {
        ServerSocket server = new ServerSocket(PORT);
        
        // Start QR rotation thread
        new Thread(() -> {
            while(true) {
                currentQRToken = generateToken();
                displayQRCode(currentQRToken);
                Thread.sleep(5000);
            }
        }).start();
        
        // Handle attendance requests
        while(true) {
            Socket client = server.accept();
            new AttendanceHandler(client).start();
        }
    }
}
Client Web (scanner.js)
JavaScript

// HTML5 QR Code Scanner
const html5QrCode = new Html5Qrcode("reader");

html5QrCode.start(
    { facingMode: "environment" },
    { fps: 10, qrbox: 250 },
    qrCodeMessage => {
        fetch(qrCodeMessage) // Server URL from QR
            .then(res => res.json())
            .then(data => {
                if(data.status === "SUCCESS") {
                    document.body.innerHTML = "Attendance Marked!";
                    setTimeout(() => window.close(), 2000);
                }
            });
    }
);