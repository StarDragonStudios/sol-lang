@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0solc.ps1" %*
exit /b %errorlevel%
