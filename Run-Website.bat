@echo off
setlocal
cd /d "%~dp0"

set "JAVA=java"
set "JAVAC=javac"
if exist "oracleJdk-26\bin\java.exe" set "JAVA=oracleJdk-26\bin\java.exe"
if exist "oracleJdk-26\bin\javac.exe" set "JAVAC=oracleJdk-26\bin\javac.exe"

if not exist build mkdir build
"%JAVAC%" --add-modules jdk.httpserver -cp ".;*;lib\*" -d build backend\*.java
if errorlevel 1 (
  echo Build failed. Keep the backend folder, frontend folder, and Java library JAR files beside this script.
  pause
  exit /b 1
)
"%JAVA%" --add-modules jdk.httpserver --enable-native-access=ALL-UNNAMED -cp "build;.;*;lib\*" WebServer
pause
