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
set "TEST_BUILD_DIR=%SELFHOST_DIR%build\tests"
set "LEXER_TEST_SOURCE=%SELFHOST_DIR%src\lexer_test.sol"
set "LEXER_TEST_OUTPUT=%TEST_BUILD_DIR%\lexer_test.exe"
set "PARSER_TEST_SOURCE=%SELFHOST_DIR%src\parser_test.sol"
set "PARSER_TEST_OUTPUT=%TEST_BUILD_DIR%\parser_test.exe"
set "GRAMMAR_TEST_SOURCE=%SELFHOST_DIR%src\grammar_test.sol"
set "GRAMMAR_TEST_OUTPUT=%TEST_BUILD_DIR%\grammar_test.exe"
set "SEMANTIC_FOUNDATION_TEST_SOURCE=%SELFHOST_DIR%src\semantic_foundation_test.sol"
set "SEMANTIC_FOUNDATION_TEST_OUTPUT=%TEST_BUILD_DIR%\semantic_foundation_test.exe"
set "SEMANTIC_ANALYSIS_TEST_SOURCE=%SELFHOST_DIR%src\semantic_analysis_test.sol"
set "SEMANTIC_ANALYSIS_TEST_OUTPUT=%TEST_BUILD_DIR%\semantic_analysis_test.exe"

set "VERSION="
for /f "delims=" %%V in ('call "%SEED_SOLC%" --version') do set "VERSION=%%V"

if not "%VERSION%"=="Sol 0.1.1" (
    echo bootstrap error: expected Sol 0.1.1 seed compiler, got: %VERSION% 1>&2
    exit /b 1
)

if not exist "%BUILD_DIR%" mkdir "%BUILD_DIR%"
if errorlevel 1 exit /b 1
if not exist "%TEST_BUILD_DIR%" mkdir "%TEST_BUILD_DIR%"
if errorlevel 1 exit /b 1

echo bootstrap: compiling stage 1 with %VERSION%
call "%SEED_SOLC%" "%SOURCE%" -o "%OUTPUT%"
if errorlevel 1 exit /b %errorlevel%

echo bootstrap: validating stage 1 executable
"%OUTPUT%"
if errorlevel 1 exit /b %errorlevel%

echo bootstrap: compiling self-host lexer tests
call "%SEED_SOLC%" "%LEXER_TEST_SOURCE%" -o "%LEXER_TEST_OUTPUT%"
if errorlevel 1 exit /b %errorlevel%

echo bootstrap: validating self-host lexer
"%LEXER_TEST_OUTPUT%"
if errorlevel 1 exit /b %errorlevel%

echo bootstrap: compiling self-host parser tests
call "%SEED_SOLC%" "%PARSER_TEST_SOURCE%" -o "%PARSER_TEST_OUTPUT%"
if errorlevel 1 exit /b %errorlevel%

echo bootstrap: validating self-host parser foundations
"%PARSER_TEST_OUTPUT%"
if errorlevel 1 exit /b %errorlevel%

echo bootstrap: compiling self-host grammar tests
call "%SEED_SOLC%" "%GRAMMAR_TEST_SOURCE%" -o "%GRAMMAR_TEST_OUTPUT%"
if errorlevel 1 exit /b %errorlevel%

echo bootstrap: validating complete self-host grammar
"%GRAMMAR_TEST_OUTPUT%"
if errorlevel 1 exit /b %errorlevel%

echo bootstrap: compiling self-host semantic foundation tests
call "%SEED_SOLC%" "%SEMANTIC_FOUNDATION_TEST_SOURCE%" -o "%SEMANTIC_FOUNDATION_TEST_OUTPUT%"
if errorlevel 1 exit /b %errorlevel%

echo bootstrap: validating self-host symbols, scopes, and types
"%SEMANTIC_FOUNDATION_TEST_OUTPUT%"
if errorlevel 1 exit /b %errorlevel%

echo bootstrap: compiling self-host semantic analysis tests
call "%SEED_SOLC%" "%SEMANTIC_ANALYSIS_TEST_SOURCE%" -o "%SEMANTIC_ANALYSIS_TEST_OUTPUT%"
if errorlevel 1 exit /b %errorlevel%

echo bootstrap: validating self-host semantic analysis and module resolution
"%SEMANTIC_ANALYSIS_TEST_OUTPUT%"
if errorlevel 1 exit /b %errorlevel%

echo bootstrap: stage 1 ready at %OUTPUT%
exit /b 0
