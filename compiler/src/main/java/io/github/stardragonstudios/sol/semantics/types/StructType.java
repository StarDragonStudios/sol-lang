package io.github.stardragonstudios.sol.semantics.types;

import io.github.stardragonstudios.sol.semantics.StructSymbol;

import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

public final class StructType implements TypeSymbol {
    private final StructSymbol symbol;
    private final List<TypeSymbol> arguments;

    public StructType(StructSymbol symbol) {
        this(symbol, List.of());
    }

    public StructType(StructSymbol symbol, List<TypeSymbol> arguments) {
        this.symbol = Objects.requireNonNull(symbol, "Struct type symbol must not be null.");
        Objects.requireNonNull(arguments, "Struct type arguments must not be null.");

        this.arguments = List.copyOf(arguments);
        this.arguments.forEach(argument -> Objects.requireNonNull(argument, "Struct type arguments must not contain null values."));

        if (this.arguments.size() != symbol.typeParameters().size()) throw new IllegalArgumentException(
            "Struct '%s' expects %d type arguments, but received %d."
                .formatted(symbol.name(), symbol.typeParameters().size(), this.arguments.size())
        );
    }

    public StructSymbol symbol() {
        return symbol;
    }

    public List<TypeSymbol> arguments() {
        return arguments;
    }

    @Override
    public TypeKind kind() {
        return TypeKind.STRUCT;
    }

    @Override
    public String name() {
        if (arguments.isEmpty()) return symbol.name();

        var text = new StringJoiner(", ", symbol.name() + "<", ">");

        arguments.forEach(argument -> text.add(argument.name()));

        return text.toString();
    }

    @Override
    public boolean isValue() {
        return true;
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

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof StructType type && symbol == type.symbol && arguments.equals(type.arguments);
    }

    @Override
    public int hashCode() {
        return 31 * System.identityHashCode(symbol) + arguments.hashCode();
    }
}
