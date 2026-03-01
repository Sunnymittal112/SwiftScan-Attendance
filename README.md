LAN-Based Smart QR Attendance System
A robust and efficient attendance management solution developed in Java. This system utilizes Network Socket Programming and QR Code technology to manage student attendance over a Local Area Network (LAN). It is designed to be lightweight, using a file-based storage system instead of a heavy database.

Project Overview
The system operates on a Client-Server architecture. The Server (Teacher's Terminal) maintains a centralized log of attendance in a text-based format, while one or more Clients (Scanner Nodes) act as the interface to scan student QR codes and transmit data over the network.

Key Features
Networked Synchronization: Real-time data transmission between the scanner and the central server using TCP/IP Sockets.

QR Integration: Seamless decoding of student identifiers using the ZXing library.

File-Based Persistence: High-speed data logging into a flat-file (.txt or .csv) format, demonstrating advanced File I/O operations.

Redundancy Control: Built-in logic to prevent duplicate attendance entries for the same student on the same calendar date.

Scalability: Capable of handling multiple scanner clients connected to a single central server.

Technical Specifications
Language: Java (JDK 17 or higher)

Networking: Java Socket API

QR Engine: ZXing (Zebra Crossing) Library

Camera Interface: Sarxos Webcam-Capture API

Data Storage: Flat File System (CSV/Text)

System Architecture
1. Central Server
The server component is responsible for listening for incoming connections on a designated port. Upon receiving a student's unique ID, it validates the data against the current date and appends the record to the master attendance file.

Data Format: Date | Student_ID | Timestamp | Status

2. Scanner Client
The client component interfaces with the system's hardware (Webcam). It processes the video feed to detect QR codes, extracts the student ID, and establishes a handshake with the server to log the attendance.

Installation and Setup
Prerequisites
All devices must be connected to the same Local Area Network (LAN).

Java Runtime Environment (JRE) must be configured on all participating machines.

Execution Steps
Initialize Server: Run AttendanceServer.java. Note the IP address of the host machine.

Launch Client: Run AttendanceScanner.java on the scanning station. Enter the Server's IP address when prompted.

Log Attendance: Present a valid QR code to the camera. The server will confirm the entry and update the local text file instantly.

Project Structure
Plaintext
├── src
│   ├── com.attendance.server
│   │   └── AttendanceServer.java     # Socket listening and File I/O logic
│   ├── com.attendance.client
│   │   └── AttendanceScanner.java    # Webcam integration and QR processing
│   ├── com.attendance.utils
│   │   └── QRGenerator.java          # Utility to generate student-specific QRs
├── data
│   └── attendance.txt                # Centralized attendance log file
└── README.md                         # Project documentation