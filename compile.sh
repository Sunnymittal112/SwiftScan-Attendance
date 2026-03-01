#!/bin/bash

echo "Compiling Smart QR Attendance System..."
mkdir -p bin

javac -cp "libs/*" -d bin src/com/attendance/models/*.java src/com/attendance/utils/*.java src/com/attendance/server/*.java

if [ $? -eq 0 ]; then
  echo "Compilation successful."
  echo "Run server with ./run.sh"
else
  echo "Compilation failed."
fi