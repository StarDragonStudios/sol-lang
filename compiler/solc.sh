#!/bin/sh
set -eu

COMPILER_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DEFAULT_CORE=$COMPILER_DIR/build/stage1/solc-core
DEFAULT_STANDARD_LIBRARY=$COMPILER_DIR/stdlib
if [ ! -f "$DEFAULT_CORE" ] && [ -f "$COMPILER_DIR/../libexec/solc-core" ]; then
    DEFAULT_CORE=$COMPILER_DIR/../libexec/solc-core
fi
if [ ! -d "$DEFAULT_STANDARD_LIBRARY" ] && [ -d "$COMPILER_DIR/../stdlib" ]; then
    DEFAULT_STANDARD_LIBRARY=$COMPILER_DIR/../stdlib
fi
CORE=${SOL_SELFHOST_CORE:-$DEFAULT_CORE}
STANDARD_LIBRARY=${SOL_SELFHOST_STDLIB:-$DEFAULT_STANDARD_LIBRARY}
NATIVE_LINK=${SOL_SELFHOST_NATIVE_LINK:-$COMPILER_DIR/native-link.sh}

command_error() {
    echo "command-line error: $1" >&2
    exit 2
}

if [ "$#" -eq 1 ] && { [ "$1" = "--version" ] || [ "$1" = "-v" ]; }; then
    echo "Sol 0.1.1"
    exit 0
fi

SOURCE=
OUTPUT=
KEEP=0
POSITIONAL_ONLY=0
while [ "$#" -gt 0 ]; do
    ARGUMENT=$1
    shift
    if [ "$POSITIONAL_ONLY" -eq 0 ] && [ "$ARGUMENT" = "--" ]; then
        POSITIONAL_ONLY=1
        continue
    fi
    if [ "$POSITIONAL_ONLY" -eq 0 ]; then
        case "$ARGUMENT" in
            --keep-intermediates)
                KEEP=1
                continue
                ;;
            -o|--output)
                [ -z "$OUTPUT" ] || command_error "Compiler output path may only be specified once."
                [ "$#" -gt 0 ] || command_error "Option '$ARGUMENT' requires an output path."
                OUTPUT=$1
                shift
                [ -n "$OUTPUT" ] || command_error "Compiler output path must not be blank."
                continue
                ;;
            --output=*)
                [ -z "$OUTPUT" ] || command_error "Compiler output path may only be specified once."
                OUTPUT=${ARGUMENT#--output=}
                [ -n "$OUTPUT" ] || command_error "Compiler output path must not be blank."
                continue
                ;;
            -*) command_error "Unknown compiler option '$ARGUMENT'." ;;
        esac
    fi
    if [ -n "$SOURCE" ]; then
        command_error "Compiler expects exactly one source file, but received both '$SOURCE' and '$ARGUMENT'."
    fi
    SOURCE=$ARGUMENT
done

[ -n "$SOURCE" ] || command_error "Compiler requires one Sol source file."
case "$SOURCE$OUTPUT" in
    *'
'*) command_error "Bootstrap CLI paths must not contain newlines." ;;
esac

SOURCE_DIRECTORY=$(dirname -- "$SOURCE")
SOURCE_FILENAME=$(basename -- "$SOURCE")
case "$SOURCE_FILENAME" in
    *.[sS][oO][lL]) ;;
    *)
        echo "input error: Sol source file '$SOURCE' must use the '.sol' extension." >&2
        exit 3
        ;;
esac
MODULE_NAME=${SOURCE_FILENAME%????}
[ -n "$MODULE_NAME" ] || {
    echo "input error: Sol source file '$SOURCE' must have a name before '.sol'." >&2
    exit 3
}
if [ ! -d "$SOURCE_DIRECTORY" ]; then
    echo "input error: Sol source file '$SOURCE' does not exist or is not a regular file." >&2
    exit 3
fi
MODULE_ROOT=$(CDPATH= cd -- "$SOURCE_DIRECTORY" && pwd)
SOURCE_PATH=$MODULE_ROOT/$SOURCE_FILENAME
if [ ! -f "$SOURCE_PATH" ]; then
    echo "input error: Sol source file '$SOURCE_PATH' does not exist or is not a regular file." >&2
    exit 3
fi

if [ -z "$OUTPUT" ]; then
    OUTPUT=$MODULE_ROOT/$MODULE_NAME
else
    case "$OUTPUT" in
        /*) ;;
        *) OUTPUT=$(pwd)/$OUTPUT ;;
    esac
fi

[ -x "$CORE" ] || {
    echo "toolchain error: self-host compiler core is not executable: $CORE" >&2
    exit 7
}
[ -x "$NATIVE_LINK" ] || {
    echo "toolchain error: native link driver is not executable: $NATIVE_LINK" >&2
    exit 7
}
[ -d "$STANDARD_LIBRARY" ] || {
    echo "input error: bundled standard library was not found: $STANDARD_LIBRARY" >&2
    exit 3
}

REQUEST=$(mktemp "${TMPDIR:-/tmp}/sol-selfhost-request.XXXXXX")
LLVM_OUTPUT=$OUTPUT.sol-selfhost.ll
LITERAL_OUTPUT=$OUTPUT.sol-selfhost-literals.c
cleanup_request() {
    rm -f -- "$REQUEST"
}
trap cleanup_request EXIT HUP INT TERM

printf '%s\n' \
    SOL-SELFHOST-REQUEST-1 \
    "$SOURCE_PATH" \
    "$MODULE_ROOT" \
    "$MODULE_NAME" \
    "$STANDARD_LIBRARY" \
    "$LLVM_OUTPUT" \
    "$LITERAL_OUTPUT" >"$REQUEST"

rm -f -- "$LLVM_OUTPUT" "$LITERAL_OUTPUT"
set +e
printf '%s\n' "$REQUEST" | "$CORE" 1>&2
CORE_STATUS=$?
set -e
if [ "$CORE_STATUS" -ne 0 ]; then
    rm -f -- "$LLVM_OUTPUT" "$LITERAL_OUTPUT"
    exit "$CORE_STATUS"
fi

set +e
if [ "$KEEP" -eq 1 ]; then
    SOL_KEEP_INTERMEDIATES=1 "$NATIVE_LINK" "$LLVM_OUTPUT" "$LITERAL_OUTPUT" "$OUTPUT" >/dev/null
else
    SOL_KEEP_INTERMEDIATES=0 "$NATIVE_LINK" "$LLVM_OUTPUT" "$LITERAL_OUTPUT" "$OUTPUT" >/dev/null
fi
LINK_STATUS=$?
set -e
if [ "$LINK_STATUS" -ne 0 ]; then
    rm -f -- "$LLVM_OUTPUT" "$LITERAL_OUTPUT"
    echo "toolchain error: native compiler driver failed with exit code $LINK_STATUS." >&2
    exit 7
fi
if [ "$KEEP" -eq 0 ]; then
    rm -f -- "$LLVM_OUTPUT" "$LITERAL_OUTPUT"
fi
exit 0
