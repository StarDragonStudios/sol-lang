package io.github.stardragonstudios.sol.parser;

import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.syntax.CallExpression;
import io.github.stardragonstudios.sol.syntax.FunctionDeclaration;
import io.github.stardragonstudios.sol.syntax.ReturnStatement;
import io.github.stardragonstudios.sol.syntax.StructConstructionExpression;
import io.github.stardragonstudios.sol.syntax.StructDeclaration;
import io.github.stardragonstudios.sol.syntax.VariableDeclarationStatement;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

final class GenericParserTest {
    @Test
    void parsesGenericDeclarationsNestedTypesConstructionAndCalls() {
        var unit = Parser.parse(Lexer.scan(
            """
            struct Pair<T, U>
                first: T
                second: U
            end

            struct Envelope<T>
                value: T
            end

            fn identity<T>(value: T) -> T
                return value
            end

            fn use() -> int
                let pair: Envelope<Pair<int, string>> = Envelope<Pair<int, string>> {
                    value: Pair<int, string> { first: 42, second: "Sol" },
                }
                return identity<int>(pair.value.first)
            end
            """
        ));

        var pair = assertInstanceOf(StructDeclaration.class, unit.declarations().get(0));
        var envelope = assertInstanceOf(StructDeclaration.class, unit.declarations().get(1));
        var identity = assertInstanceOf(FunctionDeclaration.class, unit.declarations().get(2));
        var use = assertInstanceOf(FunctionDeclaration.class, unit.declarations().get(3));

        assertEquals(List.of("T", "U"), pair.typeParameters().stream().map(parameter -> parameter.name()).toList());
        assertEquals(List.of("T"), envelope.typeParameters().stream().map(parameter -> parameter.name()).toList());
        assertEquals(List.of("T"), identity.typeParameters().stream().map(parameter -> parameter.name()).toList());
        assertEquals("T", identity.parameters().getFirst().type().name());
        assertEquals("T", identity.returnType().name());

        var declaration = assertInstanceOf(VariableDeclarationStatement.class, use.body().orElseThrow().statements().getFirst());
        var declaredType = declaration.type();

        assertEquals("Envelope", declaredType.name());
        assertEquals("Pair", declaredType.arguments().getFirst().name());
        assertEquals(List.of("int", "string"), declaredType.arguments().getFirst().arguments().stream().map(type -> type.name()).toList());

        var construction = assertInstanceOf(StructConstructionExpression.class, declaration.initializer());

        assertEquals("Envelope", construction.type().name());
        assertEquals("Pair", construction.type().arguments().getFirst().name());
        assertInstanceOf(StructConstructionExpression.class, construction.fields().getFirst().value());

        var returned = assertInstanceOf(ReturnStatement.class, use.body().orElseThrow().statements().get(1));
        var call = assertInstanceOf(CallExpression.class, returned.expression().orElseThrow());

        assertEquals(List.of("int"), call.typeArguments().stream().map(type -> type.name()).toList());
    }

    @Test
    void parsesExplicitTypeArgumentsOnNamespaceCalls() {
        var unit = Parser.parse(Lexer.scan(
            """
            inject namespace helpers as h

            fn use() -> int
                return h::identity<int>(42)
            end
            """
        ));
        var use = assertInstanceOf(FunctionDeclaration.class, unit.declarations().get(1));
        var returned = assertInstanceOf(ReturnStatement.class, use.body().orElseThrow().statements().getFirst());
        var call = assertInstanceOf(CallExpression.class, returned.expression().orElseThrow());

        assertEquals("int", call.typeArguments().getFirst().name());
    }
}
