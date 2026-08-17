#!/bin/sh
set -eu

SELFHOST_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
SOLC=${SOL_SELFHOST_SOLC:-$SELFHOST_DIR/solc.sh}

command_error() {
    echo "command-line error: $1" >&2
    exit 2
}

if [ "$#" -eq 1 ] && { [ "$1" = "--version" ] || [ "$1" = "-v" ]; }; then
    echo "Sol 0.1.1"
    exit 0
fi
[ "$#" -gt 0 ] || command_error "Sol requires a command."
COMMAND=$1
shift
[ "$COMMAND" = "run" ] || command_error "Unknown Sol command '$COMMAND'."

SOURCE=
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
            -*) command_error "Unknown run option '$ARGUMENT'." ;;
        esac
    fi
    if [ -n "$SOURCE" ]; then
        command_error "Run expects exactly one source file, but received both '$SOURCE' and '$ARGUMENT'."
    fi
    SOURCE=$ARGUMENT
done
[ -n "$SOURCE" ] || command_error "Run requires one Sol source file."

RUN_DIRECTORY=$(mktemp -d "${TMPDIR:-/tmp}/sol-run.XXXXXX")
RUN_OUTPUT=$RUN_DIRECTORY/program
cleanup_run() {
    rm -f -- "$RUN_OUTPUT" "$RUN_OUTPUT.exe"
    rmdir -- "$RUN_DIRECTORY" 2>/dev/null || true
}
trap cleanup_run EXIT HUP INT TERM

set +e
"$SOLC" -o "$RUN_OUTPUT" -- "$SOURCE"
COMPILE_STATUS=$?
set -e
if [ "$COMPILE_STATUS" -ne 0 ]; then
    exit "$COMPILE_STATUS"
fi

EXECUTABLE=$RUN_OUTPUT
if [ -f "$RUN_OUTPUT.exe" ]; then
    EXECUTABLE=$RUN_OUTPUT.exe
fi
if [ ! -x "$EXECUTABLE" ]; then
    echo "execution error: compiled program is not executable: $EXECUTABLE" >&2
    exit 8
fi
set +e
"$EXECUTABLE"
PROGRAM_STATUS=$?
set -e
exit "$PROGRAM_STATUS"
