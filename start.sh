#!/bin/bash

echo "Starting SwiftScan Attendance Server..."

screen -dmS swiftscan java -cp "bin:libs/*" com.attendance.server.AttendanceServer

echo "Server started in screen session: swiftscan"

