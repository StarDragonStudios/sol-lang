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
set "OUTPUT=%BUILD_DIR%\solc-core.exe"
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
set "NATIVE_FAILURE_DIAGNOSTIC=%TEST_BUILD_DIR%\native-failure.txt"
set "CLI_FIXTURE_SOURCE=%SELFHOST_DIR%fixtures\cli\main.sol"
set "CLI_FIXTURE_OUTPUT=%TEST_BUILD_DIR%\cli fixture.exe"
set "CLI_INVALID_SOURCE=%SELFHOST_DIR%fixtures\cli-invalid.sol"
set "CLI_DIAGNOSTIC=%TEST_BUILD_DIR%\cli-diagnostic.txt"
set "CLI_TEST_SOURCE=%SELFHOST_DIR%src\cli_test.sol"
set "CLI_TEST_OUTPUT=%TEST_BUILD_DIR%\cli_test.exe"
set "CLI_STDIN_SOURCE=%SELFHOST_DIR%fixtures\cli-stdin.sol"
set "CLI_STDIN_INPUT=%TEST_BUILD_DIR%\cli-stdin.txt"
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

echo bootstrap: validating stage 1 command launchers
set "SOL_SELFHOST_CORE=%OUTPUT%"
call "%SELFHOST_DIR%solc.bat" --version
if errorlevel 1 exit /b %errorlevel%
call "%SELFHOST_DIR%sol.bat" --version
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
pushd "%SELFHOST_DIR%.."
(echo input)| "%NATIVE_FIXTURE_OUTPUT%"
set "NATIVE_RESULT=%errorlevel%"
popd
if not "!NATIVE_RESULT!"=="0" exit /b !NATIVE_RESULT!

echo bootstrap: validating native runtime failure contract
pushd "%SELFHOST_DIR%.."
(echo vector-failure)| "%NATIVE_FIXTURE_OUTPUT%" >NUL 2>"%NATIVE_FAILURE_DIAGNOSTIC%"
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

echo bootstrap: compiling a multi-module program through self-host solc
call "%SELFHOST_DIR%solc.bat" --keep-intermediates --output "%CLI_FIXTURE_OUTPUT%" "%CLI_FIXTURE_SOURCE%"
if errorlevel 1 exit /b %errorlevel%

for %%F in (
    "%CLI_FIXTURE_OUTPUT%.sol-selfhost.ll"
    "%CLI_FIXTURE_OUTPUT%.sol-selfhost-literals.c"
    "%CLI_FIXTURE_OUTPUT%.sol-link.obj"
    "%CLI_FIXTURE_OUTPUT%.sol-runtime.obj"
    "%CLI_FIXTURE_OUTPUT%.sol-literals.obj"
) do (
    if not exist "%%~F" (
        echo bootstrap error: expected retained self-host artifact: %%~F 1>&2
        exit /b 1
    )
    for %%S in ("%%~F") do if %%~zS LEQ 0 (
        echo bootstrap error: retained self-host artifact is empty: %%~F 1>&2
        exit /b 1
    )
)

echo bootstrap: validating self-host solc executable output
"%CLI_FIXTURE_OUTPUT%"
set "CLI_STATUS=!errorlevel!"
if not "!CLI_STATUS!"=="37" (
    echo bootstrap error: expected self-host solc program status 37, got: !CLI_STATUS! 1>&2
    exit /b 1
)
del /q "%CLI_FIXTURE_OUTPUT%.sol-selfhost.ll" "%CLI_FIXTURE_OUTPUT%.sol-selfhost-literals.c" "%CLI_FIXTURE_OUTPUT%.sol-link.obj" "%CLI_FIXTURE_OUTPUT%.sol-runtime.obj" "%CLI_FIXTURE_OUTPUT%.sol-literals.obj"

echo bootstrap: compiling self-host CLI protocol tests
call "%SEED_SOLC%" "%CLI_TEST_SOURCE%" -o "%CLI_TEST_OUTPUT%"
if errorlevel 1 exit /b %errorlevel%
"%CLI_TEST_OUTPUT%"
if errorlevel 1 exit /b %errorlevel%

echo bootstrap: validating self-host command-line rejection
call "%SELFHOST_DIR%solc.bat" --unknown >NUL 2>NUL
set "CLI_STATUS=!errorlevel!"
if not "!CLI_STATUS!"=="2" (
    echo bootstrap error: expected command-line status 2, got: !CLI_STATUS! 1>&2
    exit /b 1
)

echo bootstrap: validating self-host frontend diagnostics
call "%SELFHOST_DIR%solc.bat" "%CLI_INVALID_SOURCE%" -o "%TEST_BUILD_DIR%\invalid-output.exe" 2>"%CLI_DIAGNOSTIC%"
set "CLI_STATUS=!errorlevel!"
if not "!CLI_STATUS!"=="4" (
    echo bootstrap error: expected self-host frontend status 4, got: !CLI_STATUS! 1>&2
    exit /b 1
)
findstr /l /c:": error [SOL-S019]: Cannot resolve module 'missing.module'." "%CLI_DIAGNOSTIC%" >nul
if errorlevel 1 (
    echo bootstrap error: self-host diagnostic did not preserve source location and code 1>&2
    exit /b 1
)

echo bootstrap: validating sol run status propagation
echo input>"%CLI_STDIN_INPUT%"
call "%SELFHOST_DIR%sol.bat" run "%CLI_STDIN_SOURCE%" < "%CLI_STDIN_INPUT%"
set "CLI_STATUS=!errorlevel!"
if not "!CLI_STATUS!"=="29" (
    echo bootstrap error: expected sol run status 29, got: !CLI_STATUS! 1>&2
    exit /b 1
)

echo bootstrap: running released-versus-self-host conformance
call "%SELFHOST_DIR%conformance\run.bat"
if errorlevel 1 exit /b %errorlevel%

echo bootstrap: stage 1 ready at %OUTPUT%
exit /b 0
