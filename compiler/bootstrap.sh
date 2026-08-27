#!/bin/sh
set -eu

COMPILER_DIR=$(cd "$(dirname "$0")" && pwd)
REPO_ROOT=$(cd "$COMPILER_DIR/.." && pwd)

SEED_SOLC=${SOLC:-solc}
SOURCE="$COMPILER_DIR/src/main.sol"
BUILD_DIR="$COMPILER_DIR/build/stage1"
OUTPUT="$BUILD_DIR/solc-core"
TEST_BUILD_DIR="$COMPILER_DIR/build/tests"
LEXER_TEST_SOURCE="$COMPILER_DIR/src/lexer_test.sol"
LEXER_TEST_OUTPUT="$TEST_BUILD_DIR/lexer_test"
PARSER_TEST_SOURCE="$COMPILER_DIR/src/parser_test.sol"
PARSER_TEST_OUTPUT="$TEST_BUILD_DIR/parser_test"
GRAMMAR_TEST_SOURCE="$COMPILER_DIR/src/grammar_test.sol"
GRAMMAR_TEST_OUTPUT="$TEST_BUILD_DIR/grammar_test"
SEMANTIC_FOUNDATION_TEST_SOURCE="$COMPILER_DIR/src/semantic_foundation_test.sol"
SEMANTIC_FOUNDATION_TEST_OUTPUT="$TEST_BUILD_DIR/semantic_foundation_test"
SEMANTIC_ANALYSIS_TEST_SOURCE="$COMPILER_DIR/src/semantic_analysis_test.sol"
SEMANTIC_ANALYSIS_TEST_OUTPUT="$TEST_BUILD_DIR/semantic_analysis_test"
IR_TEST_SOURCE="$COMPILER_DIR/src/ir_test.sol"
IR_TEST_OUTPUT="$TEST_BUILD_DIR/ir_test"
LOWERING_TEST_SOURCE="$COMPILER_DIR/src/lowering_test.sol"
LOWERING_TEST_OUTPUT="$TEST_BUILD_DIR/lowering_test"
LLVM_TEST_SOURCE="$COMPILER_DIR/src/llvm_generation_test.sol"
LLVM_TEST_OUTPUT="$TEST_BUILD_DIR/llvm_generation_test"
LLVM_FIXTURE_SOURCE="$COMPILER_DIR/src/llvm_fixture.sol"
LLVM_FIXTURE_OUTPUT="$TEST_BUILD_DIR/llvm_fixture"
LLVM_FIXTURE_IR="$TEST_BUILD_DIR/llvm_fixture.ll"
NATIVE_ARTIFACT_SOURCE="$COMPILER_DIR/src/native_artifact_fixture.sol"
NATIVE_ARTIFACT_OUTPUT="$TEST_BUILD_DIR/native_artifact_fixture"
NATIVE_FIXTURE_IR="$TEST_BUILD_DIR/native_fixture.ll"
NATIVE_FIXTURE_LITERALS="$TEST_BUILD_DIR/native_literals.c"
NATIVE_FIXTURE_OUTPUT="$TEST_BUILD_DIR/native fixture"
NATIVE_FAILURE_DIAGNOSTIC="$TEST_BUILD_DIR/native-failure.txt"
NATIVE_FAILURE_STDERR="$TEST_BUILD_DIR/native-failure.stderr.txt"
CLI_FIXTURE_SOURCE="$COMPILER_DIR/fixtures/cli/main.sol"
CLI_FIXTURE_OUTPUT="$TEST_BUILD_DIR/cli fixture"
CLI_INVALID_SOURCE="$COMPILER_DIR/fixtures/cli-invalid.sol"
CLI_DIAGNOSTIC="$TEST_BUILD_DIR/cli-diagnostic.txt"
CLI_TEST_SOURCE="$COMPILER_DIR/src/cli_test.sol"
CLI_TEST_OUTPUT="$TEST_BUILD_DIR/cli_test"
CLI_STDIN_SOURCE="$COMPILER_DIR/fixtures/cli-stdin.sol"
CLANG=${SOL_CLANG:-clang}

VERSION=$("$SEED_SOLC" --version)

if [ "$VERSION" != "Sol 0.1.1" ]; then
    echo "bootstrap error: expected Sol 0.1.1 seed compiler, got: $VERSION" >&2
    exit 1
fi

mkdir -p "$BUILD_DIR" "$TEST_BUILD_DIR"

echo "bootstrap: compiling stage 1 with $VERSION"
"$SEED_SOLC" "$SOURCE" -o "$OUTPUT"

echo "bootstrap: validating stage 1 command launchers"
SOL_SELFHOST_CORE="$OUTPUT" "$COMPILER_DIR/solc.sh" --version
SOL_SELFHOST_CORE="$OUTPUT" "$COMPILER_DIR/sol.sh" --version

# The published seed builds stage 1; stage 1 compiles the test suite so that
# compiler improvements are exercised without replacing the trusted seed.
compile_test() {
    SOL_SELFHOST_CORE="$OUTPUT" "$COMPILER_DIR/solc.sh" "$@"
}

echo "bootstrap: compiling self-host lexer tests"
compile_test "$LEXER_TEST_SOURCE" -o "$LEXER_TEST_OUTPUT"

echo "bootstrap: validating self-host lexer"
"$LEXER_TEST_OUTPUT"

echo "bootstrap: compiling self-host parser tests"
compile_test "$PARSER_TEST_SOURCE" -o "$PARSER_TEST_OUTPUT"

echo "bootstrap: validating self-host parser foundations"
"$PARSER_TEST_OUTPUT"

echo "bootstrap: compiling self-host grammar tests"
compile_test "$GRAMMAR_TEST_SOURCE" -o "$GRAMMAR_TEST_OUTPUT"

echo "bootstrap: validating complete self-host grammar"
"$GRAMMAR_TEST_OUTPUT"

echo "bootstrap: compiling self-host semantic foundation tests"
compile_test "$SEMANTIC_FOUNDATION_TEST_SOURCE" -o "$SEMANTIC_FOUNDATION_TEST_OUTPUT"

echo "bootstrap: validating self-host symbols, scopes, and types"
"$SEMANTIC_FOUNDATION_TEST_OUTPUT"

echo "bootstrap: compiling self-host semantic analysis tests"
compile_test "$SEMANTIC_ANALYSIS_TEST_SOURCE" -o "$SEMANTIC_ANALYSIS_TEST_OUTPUT"

echo "bootstrap: validating self-host semantic analysis and module resolution"
"$SEMANTIC_ANALYSIS_TEST_OUTPUT"

echo "bootstrap: compiling self-host typed Sol IR tests"
compile_test "$IR_TEST_SOURCE" -o "$IR_TEST_OUTPUT"

echo "bootstrap: validating self-host typed Sol IR"
"$IR_TEST_OUTPUT"

echo "bootstrap: compiling self-host semantic-to-IR lowering tests"
compile_test "$LOWERING_TEST_SOURCE" -o "$LOWERING_TEST_OUTPUT"

echo "bootstrap: validating self-host semantic-to-IR lowering"
"$LOWERING_TEST_OUTPUT"

echo "bootstrap: compiling self-host LLVM generation tests"
compile_test "$LLVM_TEST_SOURCE" -o "$LLVM_TEST_OUTPUT"

echo "bootstrap: validating self-host LLVM generation"
"$LLVM_TEST_OUTPUT"

if ! command -v "$CLANG" >/dev/null 2>&1; then
    echo "bootstrap error: LLVM verifier not found: $CLANG" >&2
    exit 1
fi

echo "bootstrap: compiling LLVM verification fixture"
compile_test "$LLVM_FIXTURE_SOURCE" -o "$LLVM_FIXTURE_OUTPUT"

echo "bootstrap: verifying generated textual LLVM IR"
"$LLVM_FIXTURE_OUTPUT" > "$LLVM_FIXTURE_IR"
"$CLANG" -x ir -S -emit-llvm "$LLVM_FIXTURE_IR" -o /dev/null

echo "bootstrap: compiling native artifact fixture"
compile_test "$NATIVE_ARTIFACT_SOURCE" -o "$NATIVE_ARTIFACT_OUTPUT"

echo "bootstrap: generating deterministic native inputs"
(cd "$REPO_ROOT" && "$NATIVE_ARTIFACT_OUTPUT")

echo "bootstrap: compiling and linking native executable"
"$COMPILER_DIR/native-link.sh" "$NATIVE_FIXTURE_IR" "$NATIVE_FIXTURE_LITERALS" "$NATIVE_FIXTURE_OUTPUT"

echo "bootstrap: validating linked native executable"
printf '%s\n' input | (cd "$REPO_ROOT" && "$NATIVE_FIXTURE_OUTPUT")

echo "bootstrap: validating native runtime failure contract"
set +e
printf '%s\n' vector-failure | (cd "$REPO_ROOT" && "$NATIVE_FIXTURE_OUTPUT") >"$NATIVE_FAILURE_DIAGNOSTIC" 2>"$NATIVE_FAILURE_STDERR"
NATIVE_FAILURE_STATUS=$?
set -e
if [ "$NATIVE_FAILURE_STATUS" -ne 70 ]; then
    echo "bootstrap error: expected native runtime status 70, got: $NATIVE_FAILURE_STATUS" >&2
    exit 1
fi
if ! grep -Fx "Sol runtime error: vector index out of bounds." "$NATIVE_FAILURE_DIAGNOSTIC" >/dev/null; then
    echo "bootstrap error: native runtime failure diagnostic did not match" >&2
    exit 1
fi
if [ -s "$NATIVE_FAILURE_STDERR" ]; then
    echo "bootstrap error: native runtime failure wrote to stderr" >&2
    exit 1
fi

echo "bootstrap: compiling a multi-module program through self-host solc"
SOL_SELFHOST_CORE="$OUTPUT" "$COMPILER_DIR/solc.sh" --keep-intermediates "--output=$CLI_FIXTURE_OUTPUT" "$CLI_FIXTURE_SOURCE"

for CLI_ARTIFACT in \
    "$CLI_FIXTURE_OUTPUT.sol-selfhost.ll" \
    "$CLI_FIXTURE_OUTPUT.sol-selfhost-literals.c" \
    "$CLI_FIXTURE_OUTPUT.sol-link.o" \
    "$CLI_FIXTURE_OUTPUT.sol-runtime.o" \
    "$CLI_FIXTURE_OUTPUT.sol-literals.o"
do
    if [ ! -s "$CLI_ARTIFACT" ]; then
        echo "bootstrap error: expected retained self-host artifact: $CLI_ARTIFACT" >&2
        exit 1
    fi
done

echo "bootstrap: validating self-host solc executable output"
set +e
"$CLI_FIXTURE_OUTPUT"
CLI_STATUS=$?
set -e
if [ "$CLI_STATUS" -ne 37 ]; then
    echo "bootstrap error: expected self-host solc program status 37, got: $CLI_STATUS" >&2
    exit 1
fi
rm -f -- \
    "$CLI_FIXTURE_OUTPUT.sol-selfhost.ll" \
    "$CLI_FIXTURE_OUTPUT.sol-selfhost-literals.c" \
    "$CLI_FIXTURE_OUTPUT.sol-link.o" \
    "$CLI_FIXTURE_OUTPUT.sol-runtime.o" \
    "$CLI_FIXTURE_OUTPUT.sol-literals.o"

echo "bootstrap: compiling self-host CLI protocol tests"
compile_test "$CLI_TEST_SOURCE" -o "$CLI_TEST_OUTPUT"
"$CLI_TEST_OUTPUT"

echo "bootstrap: validating self-host command-line rejection"
set +e
SOL_SELFHOST_CORE="$OUTPUT" "$COMPILER_DIR/solc.sh" --unknown >/dev/null 2>&1
CLI_STATUS=$?
set -e
if [ "$CLI_STATUS" -ne 2 ]; then
    echo "bootstrap error: expected command-line status 2, got: $CLI_STATUS" >&2
    exit 1
fi

echo "bootstrap: validating self-host frontend diagnostics"
set +e
SOL_SELFHOST_CORE="$OUTPUT" "$COMPILER_DIR/solc.sh" "$CLI_INVALID_SOURCE" -o "$TEST_BUILD_DIR/invalid-output" 2>"$CLI_DIAGNOSTIC"
CLI_STATUS=$?
set -e
if [ "$CLI_STATUS" -ne 4 ]; then
    echo "bootstrap error: expected self-host frontend status 4, got: $CLI_STATUS" >&2
    exit 1
fi
if ! grep -F ": error [SOL-S019]: Cannot resolve module 'missing.module'." "$CLI_DIAGNOSTIC" >/dev/null; then
    echo "bootstrap error: self-host diagnostic did not preserve source location and code" >&2
    exit 1
fi

echo "bootstrap: validating sol run status propagation"
set +e
printf '%s\n' input | SOL_SELFHOST_CORE="$OUTPUT" "$COMPILER_DIR/sol.sh" run "$CLI_STDIN_SOURCE"
CLI_STATUS=$?
set -e
if [ "$CLI_STATUS" -ne 29 ]; then
    echo "bootstrap error: expected sol run status 29, got: $CLI_STATUS" >&2
    exit 1
fi

echo "bootstrap: running released-versus-self-host conformance"
SOLC="$SEED_SOLC" SOL_SELFHOST_CORE="$OUTPUT" "$COMPILER_DIR/conformance/run.sh"

echo "bootstrap: stage 1 ready at $OUTPUT"
