@echo off
setlocal EnableExtensions EnableDelayedExpansion

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
set "IR_TEST_SOURCE=%SELFHOST_DIR%src\ir_test.sol"
set "IR_TEST_OUTPUT=%TEST_BUILD_DIR%\ir_test.exe"
set "LOWERING_TEST_SOURCE=%SELFHOST_DIR%src\lowering_test.sol"
set "LOWERING_TEST_OUTPUT=%TEST_BUILD_DIR%\lowering_test.exe"
set "LLVM_TEST_SOURCE=%SELFHOST_DIR%src\llvm_generation_test.sol"
set "LLVM_TEST_OUTPUT=%TEST_BUILD_DIR%\llvm_generation_test.exe"
set "LLVM_FIXTURE_SOURCE=%SELFHOST_DIR%src\llvm_fixture.sol"
set "LLVM_FIXTURE_OUTPUT=%TEST_BUILD_DIR%\llvm_fixture.exe"
set "LLVM_FIXTURE_IR=%TEST_BUILD_DIR%\llvm_fixture.ll"
set "NATIVE_ARTIFACT_SOURCE=%SELFHOST_DIR%src\native_artifact_fixture.sol"
set "NATIVE_ARTIFACT_OUTPUT=%TEST_BUILD_DIR%\native_artifact_fixture.exe"
set "NATIVE_FIXTURE_IR=%TEST_BUILD_DIR%\native_fixture.ll"
set "NATIVE_FIXTURE_LITERALS=%TEST_BUILD_DIR%\native_literals.c"
set "NATIVE_FIXTURE_OUTPUT=%TEST_BUILD_DIR%\native fixture.exe"
set "NATIVE_INPUT=%TEST_BUILD_DIR%\native-input.txt"
set "NATIVE_FAILURE_DIAGNOSTIC=%TEST_BUILD_DIR%\native-failure.txt"
set "NATIVE_FAILURE_STDERR=%TEST_BUILD_DIR%\native-failure.stderr.txt"
if defined SOL_CLANG (
    set "CLANG=%SOL_CLANG%"
) else if defined SOL_LINKER (
    set "CLANG=%SOL_LINKER%"
) else (
    set "CLANG=clang"
)

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

echo bootstrap: compiling self-host typed Sol IR tests
call "%SEED_SOLC%" "%IR_TEST_SOURCE%" -o "%IR_TEST_OUTPUT%"
if errorlevel 1 exit /b %errorlevel%

echo bootstrap: validating self-host typed Sol IR
"%IR_TEST_OUTPUT%"
if errorlevel 1 exit /b %errorlevel%

echo bootstrap: compiling self-host semantic-to-IR lowering tests
call "%SEED_SOLC%" "%LOWERING_TEST_SOURCE%" -o "%LOWERING_TEST_OUTPUT%"
if errorlevel 1 exit /b %errorlevel%

echo bootstrap: validating self-host semantic-to-IR lowering
"%LOWERING_TEST_OUTPUT%"
if errorlevel 1 exit /b %errorlevel%

echo bootstrap: compiling self-host LLVM generation tests
call "%SEED_SOLC%" "%LLVM_TEST_SOURCE%" -o "%LLVM_TEST_OUTPUT%"
if errorlevel 1 exit /b %errorlevel%

echo bootstrap: validating self-host LLVM generation
"%LLVM_TEST_OUTPUT%"
if errorlevel 1 exit /b %errorlevel%

call "%CLANG%" --version >nul 2>nul
if errorlevel 1 (
    echo bootstrap error: LLVM verifier not found: %CLANG% 1>&2
    exit /b 1
)

echo bootstrap: compiling LLVM verification fixture
call "%SEED_SOLC%" "%LLVM_FIXTURE_SOURCE%" -o "%LLVM_FIXTURE_OUTPUT%"
if errorlevel 1 exit /b %errorlevel%

echo bootstrap: verifying generated textual LLVM IR
"%LLVM_FIXTURE_OUTPUT%" > "%LLVM_FIXTURE_IR%"
if errorlevel 1 exit /b %errorlevel%
call "%CLANG%" -x ir -S -emit-llvm "%LLVM_FIXTURE_IR%" -o NUL
if errorlevel 1 exit /b %errorlevel%

echo bootstrap: compiling native artifact fixture
call "%SEED_SOLC%" "%NATIVE_ARTIFACT_SOURCE%" -o "%NATIVE_ARTIFACT_OUTPUT%"
if errorlevel 1 exit /b %errorlevel%

echo bootstrap: generating deterministic native inputs
pushd "%SELFHOST_DIR%.."
"%NATIVE_ARTIFACT_OUTPUT%"
set "NATIVE_RESULT=%errorlevel%"
popd
if not "!NATIVE_RESULT!"=="0" exit /b !NATIVE_RESULT!

echo bootstrap: compiling and linking native executable
call "%SELFHOST_DIR%native-link.bat" "%NATIVE_FIXTURE_IR%" "%NATIVE_FIXTURE_LITERALS%" "%NATIVE_FIXTURE_OUTPUT%"
if errorlevel 1 exit /b %errorlevel%

echo bootstrap: validating linked native executable
echo input>"%NATIVE_INPUT%"
pushd "%SELFHOST_DIR%.."
"%NATIVE_FIXTURE_OUTPUT%" < "%NATIVE_INPUT%"
set "NATIVE_RESULT=%errorlevel%"
popd
if not "!NATIVE_RESULT!"=="0" exit /b !NATIVE_RESULT!

echo bootstrap: validating native runtime failure contract
echo vector-failure>"%NATIVE_INPUT%"
pushd "%SELFHOST_DIR%.."
"%NATIVE_FIXTURE_OUTPUT%" < "%NATIVE_INPUT%" >"%NATIVE_FAILURE_DIAGNOSTIC%" 2>"%NATIVE_FAILURE_STDERR%"
set "NATIVE_RESULT=!errorlevel!"
popd
if not "!NATIVE_RESULT!"=="70" (
    echo bootstrap error: expected native runtime status 70, got: !NATIVE_RESULT! 1>&2
    exit /b 1
)
findstr /x /c:"Sol runtime error: vector index out of bounds." "%NATIVE_FAILURE_DIAGNOSTIC%" >nul
if errorlevel 1 (
    echo bootstrap error: native runtime failure diagnostic did not match 1>&2
    exit /b 1
)
for %%A in ("%NATIVE_FAILURE_STDERR%") do if not "%%~zA"=="0" (
    echo bootstrap error: native runtime failure wrote to stderr 1>&2
    exit /b 1
)

echo bootstrap: stage 1 ready at %OUTPUT%
exit /b 0
