#!/bin/sh
set -eu

SELFHOST_DIR=$(cd "$(dirname "$0")" && pwd)

SEED_SOLC=${SOLC:-solc}
SOURCE="$SELFHOST_DIR/src/main.sol"
BUILD_DIR="$SELFHOST_DIR/build/stage1"
OUTPUT="$BUILD_DIR/solc"

VERSION=$("$SEED_SOLC" --version)

if [ "$VERSION" != "Sol 0.1.0" ]; then
    echo "bootstrap error: expected Sol 0.1.0 seed compiler, got: $VERSION" >&2
    exit 1
fi

mkdir -p "$BUILD_DIR"

echo "bootstrap: compiling stage 1 with $VERSION"
"$SEED_SOLC" "$SOURCE" -o "$OUTPUT"

echo "bootstrap: validating stage 1 executable"
"$OUTPUT"

echo "bootstrap: stage 1 ready at $OUTPUT"
