@echo off
echo [32mCompiling Smart QR Attendance System...[0m

if not exist bin mkdir bin

javac -cp "libs/*" -d bin src\com\attendance\models\*.java src\com\attendance\utils\*.java src\com\attendance\server\*.java

if %errorlevel%==0 (
  echo [32mCompilation successful![0m
  echo.
  echo [33mRun server with: run.bat[0m
) else (
  echo [31mCompilation failed![0m
)

pause