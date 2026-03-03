#!/bin/bash

echo "Compiling Smart QR Attendance System..."
rm -rf bin
mkdir -p bin

javac --release 8 -cp "libs/*" -d bin src/com/attendance/models/*.java src/com/attendance/utils/*.java src/com/attendance/server/*.java
if [ $? -ne 0 ]; then
  echo "--release not supported, retrying with -source/-target 1.8..."
  javac -source 1.8 -target 1.8 -cp "libs/*" -d bin src/com/attendance/models/*.java src/com/attendance/utils/*.java src/com/attendance/server/*.java
fi

if [ $? -eq 0 ]; then
  echo "Compilation successful (Java 8 compatible)."
  echo "Run server with ./run.sh"
else
  echo "Compilation failed."
fi
