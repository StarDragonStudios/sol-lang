package io.github.stardragonstudios.sol.cli;

public enum CompilerExitCode {
    SUCCESS(0),
    COMMAND_LINE_ERROR(2),
    INPUT_ERROR(3),
    FRONTEND_ERROR(4),
    LOWERING_ERROR(5),
    BACKEND_ERROR(6),
    TOOLCHAIN_ERROR(7);

    private final int value;

    CompilerExitCode(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
