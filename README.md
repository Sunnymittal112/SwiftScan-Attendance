# SwiftScan Attendance (Smart QR Attendance System)

A lightweight Java-based LAN attendance system that uses **single-use QR tokens** to reduce proxy attendance and simplify classroom check-ins.

## Overview

This project runs a local HTTP server on the teacher system. Students scan a QR code from their phone, submit roll/name/class, and attendance is saved to date-wise CSV files.

Core goals:
- Fast attendance on local network
- One-scan, one-submit flow
- Basic anti-abuse controls (single-use token + device blocking)
- Simple CSV-based storage (no database required)

## Key Features

- QR-based attendance marking (`/qr` + `/mark`)
- Single-use token flow: token is invalidated after successful attendance
- Auto-generation of next token after each successful mark
- Device fingerprint based one-device-one-submit-per-day restriction
- LAN/private network access check for attendance endpoint
- Web dashboard with live stats and auto-refresh
- QR start/stop controls for class management
- Late/PRESENT status based on configurable cutoff time

## Tech Stack

- Java (JDK 8+ recommended)
- [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd) for embedded HTTP server
- [ZXing](https://github.com/zxing/zxing) for QR generation
- HTML/CSS/JS frontend (`web/student/index.html`)
- CSV file persistence in `data/`

## Project Structure

```text
SmartQRAttendance/
|-- src/com/attendance/
|   |-- server/
|   |   |-- AttendanceServer.java
|   |   |-- FileManager.java
|   |   |-- DeviceManager.java
|   |   `-- QRTokenManager.java
|   |-- models/
|   |   |-- Student.java
|   |   `-- AttendanceRecord.java
|   `-- utils/
|       |-- ConfigLoader.java
|       |-- QRCodeGenerator.java
|       `-- TokenEncryption.java
|-- web/student/index.html
|-- data/
|   |-- config.properties
|   |-- students.csv
|   |-- attendance/
|   `-- devices/
|-- libs/
|-- compile.bat / run.bat
`-- compile.sh / run.sh
```

## How It Works

1. Teacher starts server.
2. QR page (`/qr`) displays tokenized attendance URL.
3. Student scans QR and opens secure attendance form.
4. Student submits details.
5. Server validates:
   - request from LAN/private network
   - token is valid and unused
   - device has not submitted today
   - roll number not already marked today
6. On success:
   - attendance written to `data/attendance/YYYY-MM-DD.csv`
   - device fingerprint written to `data/devices/YYYY-MM-DD.csv`
   - token is marked used
   - new token generated for next student

## Setup and Run

## Prerequisites

- Java installed (`java` and `javac` in PATH)
- All jars present in `SmartQRAttendance/libs`

## Windows

```bash
cd SmartQRAttendance
compile.bat
run.bat
```

## Linux/macOS

```bash
cd SmartQRAttendance
chmod +x compile.sh run.sh
./compile.sh
./run.sh
```

When server starts, it prints URLs like:
- Home: `http://<server-ip>:8080/`
- QR Screen: `http://<server-ip>:8080/qr`
- Dashboard: `http://<server-ip>:8080/dashboard`
- Student Scanner: `http://<server-ip>:8080/student`

## Configuration

Edit `SmartQRAttendance/data/config.properties`:

```properties
server.port=8080
qr.refresh.interval=60000
encryption.secret=MySecretKey12345
late.time.threshold=09:10
```

Parameter meaning:
- `server.port`: HTTP server port
- `qr.refresh.interval`: token validity window in milliseconds (also used by QR page refresh timer)
- `encryption.secret`: AES secret seed for token encryption (normalized to 16 bytes)
- `late.time.threshold`: after this time, status is marked `LATE`

## API / Routes

- `GET /` -> Home page with controls
- `GET /qr` -> QR display page
- `GET /qr/start` -> Enable QR scanning
- `GET /qr/stop` -> Disable QR scanning
- `GET /qr/toggle` -> Toggle QR status
- `GET /qr/refresh` -> Generate new token manually
- `GET /student` -> Student web scanner UI
- `GET /mark` -> Attendance validation + marking endpoint
- `GET /dashboard` -> Live attendance dashboard
- `GET /api/stats` -> JSON stats

## Data Files

- `data/students.csv`
  - master student list
  - format: `RollNo,Name,Class`
- `data/attendance/YYYY-MM-DD.csv`
  - daily attendance entries
  - format: `roll,name,timestamp,status`
- `data/devices/YYYY-MM-DD.csv`
  - daily device fingerprint registry
  - format: `fingerprint,roll`

## Security Notes

Current implementation includes:
- Single-use QR tokens
- Time-bound token validation
- Device fingerprint daily lock
- LAN-only access check for attendance marking

Recommended production hardening:
- Move from plain HTTP to HTTPS/reverse proxy
- Use stronger identity verification than self-entered name/class
- Rotate encryption secret and keep it outside repo
- Add authentication for admin endpoints (`/qr/*`, `/dashboard`)

## Troubleshooting

- `Error loading students`: confirm `data/students.csv` exists and has proper header + rows.
- `Compilation failed`: verify Java version and `libs/*` jars.
- Students cannot access server: confirm both devices are on same LAN and firewall allows chosen port.
- Camera not opening in scanner page: allow browser camera permission on phone.

## Current Limitations

- CSV storage only (no DB transactions)
- No teacher authentication for control endpoints
- Device fingerprint can be bypassed on advanced browsers/devices
- In-memory state (token/device maps) resets on server restart

## License

No license file is currently included. Add a `LICENSE` file before open-source distribution.
