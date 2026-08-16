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

echo "bootstrap: stage 1 ready at $OUTPUT"
