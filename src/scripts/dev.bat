@echo off
REM ============================================
REM Online Mall - local dev service launcher
REM
REM Usage:
REM   dev.bat start              start all
REM   dev.bat stop               stop all
REM   dev.bat restart            restart all
REM   dev.bat status             show status
REM   dev.bat logs               list log files
REM   dev.bat start nacos        start nacos only
REM   dev.bat stop gateway       stop gateway only
REM
REM Uses -ExecutionPolicy Bypass because Windows
REM blocks PowerShell scripts by default.
REM ============================================

setlocal
set "SCRIPT=%~dp0dev.ps1"

powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT%" %*
set "RC=%ERRORLEVEL%"

REM Double-click keeps window open
if "%~1"=="" pause
exit /b %RC%