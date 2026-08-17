@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0sol.ps1" %*
exit /b %errorlevel%
