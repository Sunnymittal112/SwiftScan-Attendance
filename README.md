# SwiftScan Attendance (Smart QR Attendance System)

A lightweight Java-based attendance system that uses **single-use QR tokens** to reduce proxy attendance and simplify classroom check-ins.

Originally the project used **CSV file-based storage**, but it has now been **upgraded to support MySQL / MariaDB databases** for better scalability and real-world deployment.

---

# Overview

This project runs a local HTTP server on Your Computer system or VPS. Students scan a QR code from their phone, submit roll/name/class, and attendance is saved in a database.

Core goals:

* Fast attendance marking
* One-scan, one-submit flow
* Anti-proxy attendance protection
* Real database storage

---

# Key Features

* QR-based attendance marking (`/qr` + `/mark`)
* Single-use token system
* Device fingerprint blocking (one device per day)
* Dashboard with live stats
* QR start / stop controls
* Late/PRESENT status based on configurable cutoff
* MySQL / MariaDB database storage
* CSV system replaced with database support

---

# Tech Stack

* Java (JDK 8+ recommended)
* NanoHTTPD (Embedded HTTP server)
* ZXing (QR code generation)
* HTML/CSS/JS frontend
* MySQL / MariaDB database

---

# Project Structure

```
SmartQRAttendance/
|-- src/com/attendance/
|   |-- server/
|   |   |-- AttendanceServer.java
|   |   |-- FileManager.java
|   |   |-- DatabaseManager.java
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
|-- data/config.properties
|-- libs/
|-- compile.bat / run.bat
`-- compile.sh / run.sh
```

---

# How It Works

1. You starts server.
2. QR page (`/qr`) displays tokenized attendance URL.
3. Student scans QR and opens secure attendance form.
4. Student submits roll number, name and class.
5. Server validates:

* token is valid and unused
* device has not submitted today
* roll number not already marked today

6. On success:

* attendance saved to **database**
* device fingerprint stored
* token marked used
* new token generated

---

# Database Setup

Create a database first.

```
CREATE DATABASE swiftscan;
```

Select database:

```
USE swiftscan;
```

---

## Attendance Table

```
CREATE TABLE attendance (
    id INT AUTO_INCREMENT PRIMARY KEY,
    student_id VARCHAR(50),
    name VARCHAR(100),
    class_id VARCHAR(50),
    date DATE,
    time TIME,
    status VARCHAR(20)
);
```

---

## Students Table (Optional but Recommended)

```
CREATE TABLE students (
    id INT AUTO_INCREMENT PRIMARY KEY,
    roll_no VARCHAR(50),
    name VARCHAR(100),
    class VARCHAR(50)
);
```

---

# Database Configuration

Edit `data/config.properties`:

```
db.url=jdbc:mysql://localhost:3306/swiftscan
db.username=YOUR_DB_USERNAME
db.password=YOUR_DB_PASSWORD
```

Example:

```
db.url=jdbc:mysql://localhost:3306/swiftscan
db.username=SunnyMittal
db.password=yourpassword
```

---

# Setup and Run

## Windows

```
cd SmartQRAttendance
compile.bat
run.bat
```

## Linux / macOS / Ubuntu

```
cd SmartQRAttendance
chmod +x compile.sh start.sh session.sh stop.sh 
./compile.sh
./start.sh
./session.sh
./stop.sh
```

---

# Server URLs

When server starts, it prints:

Home

```
http://<server-ip>:8080/
```

QR Screen

```
http://<server-ip>:8080/qr
```

Dashboard

```
http://<server-ip>:8080/dashboard
```

Student Scanner

```
http://<server-ip>:8080/student
```

---

# API Routes

| Route         | Description       |
| ------------- | ----------------- |
| `/`           | Home page         |
| `/qr`         | QR display        |
| `/qr/start`   | Start attendance  |
| `/qr/stop`    | Stop attendance   |
| `/qr/refresh` | Generate new QR   |
| `/student`    | Student scanner   |
| `/mark`       | Attendance submit |
| `/dashboard`  | Live dashboard    |
| `/api/stats`  | JSON stats        |

---

# Security Features

* Single-use QR tokens
* Device fingerprint lock
* Token expiration
* Anti-proxy design

---

# Future Improvements

Planned improvements:

* Weekly attendance reports
* Monthly analytics dashboard
* Student auto lookup from database
* Export attendance to Excel
* Teacher authentication system

---

