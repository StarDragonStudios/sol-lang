package io.github.stardragonstudios.sol.semantics;

import io.github.stardragonstudios.sol.semantics.types.StructType;
import io.github.stardragonstudios.sol.semantics.types.TypeSymbol;
import io.github.stardragonstudios.sol.source.SourceSpan;
import io.github.stardragonstudios.sol.syntax.StructDeclaration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class StructSymbol implements Symbol {
    private final StructDeclaration declaration;
    private final Optional<ModuleName> moduleName;
    private final StructType type;
    private final List<TypeParameterSymbol> typeParameters;
    private final List<StructFieldSymbol> fields;
    private final LinkedHashMap<String, StructFieldSymbol> fieldsByName = new LinkedHashMap<>();

    public StructSymbol(StructDeclaration declaration) {
        this(declaration, null);
    }

    StructSymbol(StructDeclaration declaration, ModuleName moduleName) {
        this.declaration = Objects.requireNonNull(declaration, "Struct symbol declaration must not be null.");
        this.moduleName = Optional.ofNullable(moduleName);
        typeParameters = declaration.typeParameters().stream().map(parameter -> new TypeParameterSymbol(parameter, this)).toList();
        type = new StructType(this, typeParameters.stream().<TypeSymbol>map(TypeParameterSymbol::type).toList());

        var declaredFields = new ArrayList<StructFieldSymbol>();

        for (var index = 0; index < declaration.fields().size(); index++) {
            var field = new StructFieldSymbol(this, declaration.fields().get(index), index);

            declaredFields.add(field);
            fieldsByName.putIfAbsent(field.name(), field);
        }

        fields = List.copyOf(declaredFields);
    }

    public StructDeclaration declaration() {
        return declaration;
    }

    public Optional<ModuleName> moduleName() {
        return moduleName;
    }

    public StructType type() {
        return type;
    }

    public List<TypeParameterSymbol> typeParameters() {
        return typeParameters;
    }

    public List<StructFieldSymbol> fields() {
        return fields;
    }

    public Optional<StructFieldSymbol> field(String name) {
        Objects.requireNonNull(name, "Struct field lookup name must not be null.");

        if (name.isBlank()) throw new IllegalArgumentException("Struct field lookup name must not be blank.");

        return Optional.ofNullable(fieldsByName.get(name));
    }

    @Override
    public SymbolKind kind() {
        return SymbolKind.STRUCT;
    }

    @Override
    public String name() {
        return declaration.name();
    }

    @Override
    public SourceSpan span() {
        return declaration.span();
    }
}
