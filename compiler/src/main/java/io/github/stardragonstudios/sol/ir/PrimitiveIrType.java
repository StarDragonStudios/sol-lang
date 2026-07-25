package io.github.stardragonstudios.sol.ir;

public enum PrimitiveIrType implements IrType {
    INT        ("int",      true,   true,   true ),
    FLOAT      ("float",    true,   true,   false),
    BOOLEAN    ("boolean",  true,   false,  false),
    CHAR       ("char",     true,   false,  false),
    STRING     ("string",   true,   false,  false),
    VOID       ("void",     false,  false,  false);

    private final String displayName;
    private final boolean value;
    private final boolean numeric;
    private final boolean integral;

    PrimitiveIrType(String displayName, boolean value, boolean numeric, boolean integral) {
        if (displayName.isBlank()) throw new IllegalArgumentException("Primitive IR type name must not be blank.");

        if (integral && !numeric) throw new IllegalArgumentException("An integral primitive IR type must also be numeric.");

        this.displayName = displayName;
        this.value = value;
        this.numeric = numeric;
        this.integral = integral;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public boolean isValue() {
        return value;
    }

    @Override
    public boolean isNumeric() {
        return numeric;
    }

    @Override
    public boolean isIntegral() {
        return integral;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
