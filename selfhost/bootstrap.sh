#!/bin/sh
set -eu

SELFHOST_DIR=$(cd "$(dirname "$0")" && pwd)

SEED_SOLC=${SOLC:-solc}
SOURCE="$SELFHOST_DIR/src/main.sol"
BUILD_DIR="$SELFHOST_DIR/build/stage1"
OUTPUT="$BUILD_DIR/solc"
TEST_BUILD_DIR="$SELFHOST_DIR/build/tests"
LEXER_TEST_SOURCE="$SELFHOST_DIR/src/lexer_test.sol"
LEXER_TEST_OUTPUT="$TEST_BUILD_DIR/lexer_test"
PARSER_TEST_SOURCE="$SELFHOST_DIR/src/parser_test.sol"
PARSER_TEST_OUTPUT="$TEST_BUILD_DIR/parser_test"
GRAMMAR_TEST_SOURCE="$SELFHOST_DIR/src/grammar_test.sol"
GRAMMAR_TEST_OUTPUT="$TEST_BUILD_DIR/grammar_test"
SEMANTIC_FOUNDATION_TEST_SOURCE="$SELFHOST_DIR/src/semantic_foundation_test.sol"
SEMANTIC_FOUNDATION_TEST_OUTPUT="$TEST_BUILD_DIR/semantic_foundation_test"
SEMANTIC_ANALYSIS_TEST_SOURCE="$SELFHOST_DIR/src/semantic_analysis_test.sol"
SEMANTIC_ANALYSIS_TEST_OUTPUT="$TEST_BUILD_DIR/semantic_analysis_test"
IR_TEST_SOURCE="$SELFHOST_DIR/src/ir_test.sol"
IR_TEST_OUTPUT="$TEST_BUILD_DIR/ir_test"
LOWERING_TEST_SOURCE="$SELFHOST_DIR/src/lowering_test.sol"
LOWERING_TEST_OUTPUT="$TEST_BUILD_DIR/lowering_test"
LLVM_TEST_SOURCE="$SELFHOST_DIR/src/llvm_generation_test.sol"
LLVM_TEST_OUTPUT="$TEST_BUILD_DIR/llvm_generation_test"
LLVM_FIXTURE_SOURCE="$SELFHOST_DIR/src/llvm_fixture.sol"
LLVM_FIXTURE_OUTPUT="$TEST_BUILD_DIR/llvm_fixture"
LLVM_FIXTURE_IR="$TEST_BUILD_DIR/llvm_fixture.ll"
CLANG=${SOL_CLANG:-clang}

VERSION=$("$SEED_SOLC" --version)

if [ "$VERSION" != "Sol 0.1.1" ]; then
    echo "bootstrap error: expected Sol 0.1.1 seed compiler, got: $VERSION" >&2
    exit 1
fi

mkdir -p "$BUILD_DIR" "$TEST_BUILD_DIR"

echo "bootstrap: compiling stage 1 with $VERSION"
"$SEED_SOLC" "$SOURCE" -o "$OUTPUT"

echo "bootstrap: validating stage 1 executable"
"$OUTPUT"

echo "bootstrap: compiling self-host lexer tests"
"$SEED_SOLC" "$LEXER_TEST_SOURCE" -o "$LEXER_TEST_OUTPUT"

echo "bootstrap: validating self-host lexer"
"$LEXER_TEST_OUTPUT"

echo "bootstrap: compiling self-host parser tests"
"$SEED_SOLC" "$PARSER_TEST_SOURCE" -o "$PARSER_TEST_OUTPUT"

echo "bootstrap: validating self-host parser foundations"
"$PARSER_TEST_OUTPUT"

echo "bootstrap: compiling self-host grammar tests"
"$SEED_SOLC" "$GRAMMAR_TEST_SOURCE" -o "$GRAMMAR_TEST_OUTPUT"

echo "bootstrap: validating complete self-host grammar"
"$GRAMMAR_TEST_OUTPUT"

echo "bootstrap: compiling self-host semantic foundation tests"
"$SEED_SOLC" "$SEMANTIC_FOUNDATION_TEST_SOURCE" -o "$SEMANTIC_FOUNDATION_TEST_OUTPUT"

echo "bootstrap: validating self-host symbols, scopes, and types"
"$SEMANTIC_FOUNDATION_TEST_OUTPUT"

echo "bootstrap: compiling self-host semantic analysis tests"
"$SEED_SOLC" "$SEMANTIC_ANALYSIS_TEST_SOURCE" -o "$SEMANTIC_ANALYSIS_TEST_OUTPUT"

echo "bootstrap: validating self-host semantic analysis and module resolution"
"$SEMANTIC_ANALYSIS_TEST_OUTPUT"

echo "bootstrap: compiling self-host typed Sol IR tests"
"$SEED_SOLC" "$IR_TEST_SOURCE" -o "$IR_TEST_OUTPUT"

echo "bootstrap: validating self-host typed Sol IR"
"$IR_TEST_OUTPUT"

echo "bootstrap: compiling self-host semantic-to-IR lowering tests"
"$SEED_SOLC" "$LOWERING_TEST_SOURCE" -o "$LOWERING_TEST_OUTPUT"

echo "bootstrap: validating self-host semantic-to-IR lowering"
"$LOWERING_TEST_OUTPUT"

echo "bootstrap: compiling self-host LLVM generation tests"
"$SEED_SOLC" "$LLVM_TEST_SOURCE" -o "$LLVM_TEST_OUTPUT"

echo "bootstrap: validating self-host LLVM generation"
"$LLVM_TEST_OUTPUT"

if ! command -v "$CLANG" >/dev/null 2>&1; then
    echo "bootstrap error: LLVM verifier not found: $CLANG" >&2
    exit 1
fi

echo "bootstrap: compiling LLVM verification fixture"
"$SEED_SOLC" "$LLVM_FIXTURE_SOURCE" -o "$LLVM_FIXTURE_OUTPUT"

echo "bootstrap: verifying generated textual LLVM IR"
"$LLVM_FIXTURE_OUTPUT" > "$LLVM_FIXTURE_IR"
"$CLANG" -x ir -S -emit-llvm "$LLVM_FIXTURE_IR" -o /dev/null

echo "bootstrap: stage 1 ready at $OUTPUT"
