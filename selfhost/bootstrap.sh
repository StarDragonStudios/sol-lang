#!/bin/sh
set -eu

SELFHOST_DIR=$(cd "$(dirname "$0")" && pwd)

SEED_SOLC=${SOLC:-solc}
SOURCE="$SELFHOST_DIR/src/main.sol"
BUILD_DIR="$SELFHOST_DIR/build/stage1"
OUTPUT="$BUILD_DIR/solc"
TEST_SOURCE="$SELFHOST_DIR/src/lexer_test.sol"
TEST_BUILD_DIR="$SELFHOST_DIR/build/tests"
TEST_OUTPUT="$TEST_BUILD_DIR/lexer_test"

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
"$SEED_SOLC" "$TEST_SOURCE" -o "$TEST_OUTPUT"

echo "bootstrap: validating self-host lexer"
"$TEST_OUTPUT"

echo "bootstrap: stage 1 ready at $OUTPUT"
