#!/bin/sh
set -eu

if [ "$#" -ne 3 ]; then
    echo "native link error: expected <module.ll> <literals.c> <output>" >&2
    exit 64
fi

LLVM_SOURCE=$1
LITERAL_SOURCE=$2
OUTPUT=$3
COMPILER_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
RUNTIME_DIR=$(CDPATH= cd -- "$COMPILER_DIR/../runtime-c" && pwd)

if [ ! -f "$LLVM_SOURCE" ]; then
    echo "native link error: LLVM input is not a regular file: $LLVM_SOURCE" >&2
    exit 66
fi
if [ ! -f "$LITERAL_SOURCE" ]; then
    echo "native link error: literal input is not a regular file: $LITERAL_SOURCE" >&2
    exit 66
fi

if [ "${SOL_LINKER+x}" = x ]; then
    if [ -z "$SOL_LINKER" ]; then
        echo "native link error: SOL_LINKER must not be empty" >&2
        exit 69
    fi
    DRIVER=$SOL_LINKER
elif command -v clang >/dev/null 2>&1; then
    DRIVER=clang
elif command -v cc >/dev/null 2>&1; then
    DRIVER=cc
else
    echo "native link error: no compiler driver found; install clang/cc or set SOL_LINKER" >&2
    exit 69
fi

if ! "$DRIVER" --version >/dev/null 2>&1; then
    echo "native link error: compiler driver is not executable: $DRIVER" >&2
    exit 69
fi

LLVM_OBJECT="$OUTPUT.sol-link.o"
RUNTIME_OBJECT="$OUTPUT.sol-runtime.o"
LITERAL_OBJECT="$OUTPUT.sol-literals.o"

cleanup_failure() {
    rm -f -- "$OUTPUT"
    if [ "${SOL_KEEP_INTERMEDIATES:-0}" != "1" ]; then
        rm -f -- "$LLVM_OBJECT" "$RUNTIME_OBJECT" "$LITERAL_OBJECT"
    fi
}

run_driver() {
    set +e
    "$DRIVER" "$@"
    DRIVER_STATUS=$?
    set -e
    if [ "$DRIVER_STATUS" -ne 0 ]; then
        cleanup_failure
        exit "$DRIVER_STATUS"
    fi
}

rm -f -- "$OUTPUT" "$LLVM_OBJECT" "$RUNTIME_OBJECT" "$LITERAL_OBJECT"

run_driver -Wno-override-module -x ir -c "$LLVM_SOURCE" -o "$LLVM_OBJECT"
run_driver -std=c11 -I"$RUNTIME_DIR" -c "$RUNTIME_DIR/selfhost.c" -o "$RUNTIME_OBJECT"
run_driver -std=c11 -I"$RUNTIME_DIR" -c "$LITERAL_SOURCE" -o "$LITERAL_OBJECT"
if [ "${SOL_REPRODUCIBLE_LINK:-0}" = "1" ]; then
    case "$(uname -s)" in
        Darwin) run_driver "$LLVM_OBJECT" "$RUNTIME_OBJECT" "$LITERAL_OBJECT" -Wl,-reproducible -o "$OUTPUT" ;;
        Linux) run_driver "$LLVM_OBJECT" "$RUNTIME_OBJECT" "$LITERAL_OBJECT" -Wl,--build-id=sha1 -o "$OUTPUT" ;;
        *)
            cleanup_failure
            echo "native link error: reproducible link mode is unsupported on this host" >&2
            exit 69
            ;;
    esac
else
    run_driver "$LLVM_OBJECT" "$RUNTIME_OBJECT" "$LITERAL_OBJECT" -o "$OUTPUT"
fi

if [ ! -f "$OUTPUT" ] || [ ! -s "$OUTPUT" ]; then
    cleanup_failure
    echo "native link error: compiler driver did not produce a non-empty executable: $OUTPUT" >&2
    exit 7
fi

if [ "${SOL_KEEP_INTERMEDIATES:-0}" != "1" ]; then
    rm -f -- "$LLVM_OBJECT" "$RUNTIME_OBJECT" "$LITERAL_OBJECT"
fi

echo "$OUTPUT"
