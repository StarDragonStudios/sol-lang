package io.github.stardragonstudios.sol.semantics.types;

public final class ErrorType
    implements TypeSymbol {

    public static final ErrorType INSTANCE =
        new ErrorType();

    private ErrorType() {
    }

    @Override
    public TypeKind kind() {
        return TypeKind.ERROR;
    }

    @Override
    public String name() {
        return "<error>";
    }

    @Override
    public boolean isValue() {
        return false;
    }

    @Override
    public boolean isNumeric() {
        return false;
    }

    @Override
    public boolean isIntegral() {
        return false;
    }

    @Override
    public String toString() {
        return name();
    }
}
