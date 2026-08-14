@echo off
setlocal EnableExtensions

set "SELFHOST_DIR=%~dp0"

if defined SOLC (
    set "SEED_SOLC=%SOLC%"
) else (
    set "SEED_SOLC=solc"
)

set "SOURCE=%SELFHOST_DIR%src\main.sol"
set "BUILD_DIR=%SELFHOST_DIR%build\stage1"
set "OUTPUT=%BUILD_DIR%\solc.exe"

set "VERSION="
for /f "delims=" %%V in ('call "%SEED_SOLC%" --version') do set "VERSION=%%V"

if not "%VERSION%"=="Sol 0.1.1" (
    echo bootstrap error: expected Sol 0.1.1 seed compiler, got: %VERSION% 1>&2
    exit /b 1
)

if not exist "%BUILD_DIR%" mkdir "%BUILD_DIR%"
if errorlevel 1 exit /b 1

echo bootstrap: compiling stage 1 with %VERSION%
call "%SEED_SOLC%" "%SOURCE%" -o "%OUTPUT%"
if errorlevel 1 exit /b %errorlevel%

echo bootstrap: validating stage 1 executable
"%OUTPUT%"
if errorlevel 1 exit /b %errorlevel%

echo bootstrap: stage 1 ready at %OUTPUT%
exit /b 0
