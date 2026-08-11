package io.github.stardragonstudios.sol.parser;

import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.syntax.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

final class PointerParserTest {
    @Test
    void parsesPointerTypesNullDereferenceIndexingAndStores() {
        var unit = Parser.parse(Lexer.scan(
            """
            fn use(values: pointer<int>) -> int
                @mut let current: pointer<int> = null
                values[1] = 40
                *values = 2
                current = values
                return *current + values[1]
            end
            """
        ));
        var function = assertInstanceOf(FunctionDeclaration.class, unit.declarations().getFirst());
        var pointerType = function.parameters().getFirst().type();
        var statements = function.body().orElseThrow().statements();

        assertEquals("pointer", pointerType.name());
        assertEquals("int", pointerType.arguments().getFirst().name());
        assertInstanceOf(NullExpression.class, assertInstanceOf(VariableDeclarationStatement.class, statements.get(0)).initializer());
        assertInstanceOf(PointerIndexExpression.class, assertInstanceOf(PointerAssignmentStatement.class, statements.get(1)).target());
        assertInstanceOf(PointerDereferenceExpression.class, assertInstanceOf(PointerAssignmentStatement.class, statements.get(2)).target());

        var returned = assertInstanceOf(ReturnStatement.class, statements.get(4));
        var addition = assertInstanceOf(BinaryExpression.class, returned.expression().orElseThrow());

        assertInstanceOf(PointerDereferenceExpression.class, addition.left());
        assertInstanceOf(PointerIndexExpression.class, addition.right());
    }
}
