@echo off
echo [32mCompiling Smart QR Attendance System...[0m

if exist bin rmdir /s /q bin
mkdir bin

javac --release 8 -cp "libs/*" -d bin src\com\attendance\models\*.java src\com\attendance\utils\*.java src\com\attendance\server\*.java
if %errorlevel% neq 0 (
  echo [33m--release not supported, retrying with -source/-target 1.8...[0m
  javac -source 1.8 -target 1.8 -cp "libs/*" -d bin src\com\attendance\models\*.java src\com\attendance\utils\*.java src\com\attendance\server\*.java
)

if %errorlevel%==0 (
  echo [32mCompilation successful - Java 8 compatible.[0m
  echo.
  echo [33mRun server with: run.bat[0m
) else (
  echo [31mCompilation failed![0m
)

pause
