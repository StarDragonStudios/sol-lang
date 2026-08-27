@echo off
setlocal EnableExtensions

if "%~3"=="" (
    echo native link error: expected ^<module.ll^> ^<literals.c^> ^<output^> 1>&2
    exit /b 64
)
if not "%~4"=="" (
    echo native link error: expected ^<module.ll^> ^<literals.c^> ^<output^> 1>&2
    exit /b 64
)

set "LLVM_SOURCE=%~f1"
set "LITERAL_SOURCE=%~f2"
set "OUTPUT=%~f3"
set "COMPILER_DIR=%~dp0"
set "RUNTIME_DIR=%COMPILER_DIR%..\runtime-c"

if not exist "%LLVM_SOURCE%" (
    echo native link error: LLVM input is not a regular file: %LLVM_SOURCE% 1>&2
    exit /b 66
)
if not exist "%LITERAL_SOURCE%" (
    echo native link error: literal input is not a regular file: %LITERAL_SOURCE% 1>&2
    exit /b 66
)

if defined SOL_LINKER (
    set "DRIVER=%SOL_LINKER%"
) else (
    where clang >nul 2>nul
    if not errorlevel 1 (
        set "DRIVER=clang"
    ) else (
        where cc >nul 2>nul
        if errorlevel 1 (
            echo native link error: no compiler driver found; install clang/cc or set SOL_LINKER 1>&2
            exit /b 69
        )
        set "DRIVER=cc"
    )
)

call "%DRIVER%" --version >nul 2>nul
if errorlevel 1 (
    echo native link error: compiler driver is not executable: %DRIVER% 1>&2
    exit /b 69
)

set "LLVM_OBJECT=%OUTPUT%.sol-link.obj"
set "RUNTIME_OBJECT=%OUTPUT%.sol-runtime.obj"
set "LITERAL_OBJECT=%OUTPUT%.sol-literals.obj"

if exist "%OUTPUT%" del /q "%OUTPUT%"
if exist "%LLVM_OBJECT%" del /q "%LLVM_OBJECT%"
if exist "%RUNTIME_OBJECT%" del /q "%RUNTIME_OBJECT%"
if exist "%LITERAL_OBJECT%" del /q "%LITERAL_OBJECT%"

call "%DRIVER%" -Wno-override-module -x ir -c "%LLVM_SOURCE%" -o "%LLVM_OBJECT%"
if errorlevel 1 goto :failure
call "%DRIVER%" -std=c11 -D_CRT_SECURE_NO_WARNINGS -I"%RUNTIME_DIR%" -c "%RUNTIME_DIR%\selfhost.c" -o "%RUNTIME_OBJECT%"
if errorlevel 1 goto :failure
call "%DRIVER%" -std=c11 -I"%RUNTIME_DIR%" -c "%LITERAL_SOURCE%" -o "%LITERAL_OBJECT%"
if errorlevel 1 goto :failure
if "%SOL_REPRODUCIBLE_LINK%"=="1" (
    call "%DRIVER%" "%LLVM_OBJECT%" "%RUNTIME_OBJECT%" "%LITERAL_OBJECT%" -Wl,/Brepro -Wl,/STACK:16777216 -o "%OUTPUT%"
) else (
    call "%DRIVER%" "%LLVM_OBJECT%" "%RUNTIME_OBJECT%" "%LITERAL_OBJECT%" -Wl,/STACK:16777216 -o "%OUTPUT%"
)
if errorlevel 1 goto :failure

if not exist "%OUTPUT%" goto :missing
for %%F in ("%OUTPUT%") do if %%~zF LEQ 0 goto :missing

if not "%SOL_KEEP_INTERMEDIATES%"=="1" (
    del /q "%LLVM_OBJECT%" "%RUNTIME_OBJECT%" "%LITERAL_OBJECT%"
)
echo %OUTPUT%
exit /b 0

:missing
echo native link error: compiler driver did not produce a non-empty executable: %OUTPUT% 1>&2

:failure
set "DRIVER_STATUS=%errorlevel%"
if exist "%OUTPUT%" del /q "%OUTPUT%"
if not "%SOL_KEEP_INTERMEDIATES%"=="1" (
    if exist "%LLVM_OBJECT%" del /q "%LLVM_OBJECT%"
    if exist "%RUNTIME_OBJECT%" del /q "%RUNTIME_OBJECT%"
    if exist "%LITERAL_OBJECT%" del /q "%LITERAL_OBJECT%"
)
exit /b %DRIVER_STATUS%
