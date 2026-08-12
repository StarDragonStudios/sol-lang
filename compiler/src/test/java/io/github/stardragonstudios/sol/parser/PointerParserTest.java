package io.github.stardragonstudios.sol.parser;

import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.syntax.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class PointerParserTest {
    @Test
    void parsesPointerTypesNullExplicitStructFieldAccessAndStringIndexing() {
        var unit = Parser.parse(Lexer.scan(
            """
            struct Node
                value: int
                next: pointer<Node>
            end

            fn use(node: pointer<Node>, text: string) -> int
                let current: pointer<Node> = null
                node->value = 2
                return node->next->value + text[1]
            end
            """
        ));
        var function = assertInstanceOf(FunctionDeclaration.class, unit.declarations().get(1));
        var pointerType = function.parameters().getFirst().type();
        var statements = function.body().orElseThrow().statements();

        assertEquals("pointer", pointerType.name());
        assertEquals("Node", pointerType.arguments().getFirst().name());
        assertInstanceOf(NullExpression.class, assertInstanceOf(VariableDeclarationStatement.class, statements.get(0)).initializer());
        assertInstanceOf(PointerFieldAccessExpression.class, assertInstanceOf(PointerFieldAssignmentStatement.class, statements.get(1)).target());

        var returned = assertInstanceOf(ReturnStatement.class, statements.get(2));
        var addition = assertInstanceOf(BinaryExpression.class, returned.expression().orElseThrow());

        var chained = assertInstanceOf(PointerFieldAccessExpression.class, addition.left());
        assertInstanceOf(PointerFieldAccessExpression.class, chained.pointer());
        assertInstanceOf(IndexExpression.class, addition.right());
    }

    @Test
    void rejectsMagicPointerDereferenceSyntax() {
        assertThrows(ParsingException.class, () -> Parser.parse(Lexer.scan(
            """
            fn read(values: pointer<int>) -> int
                return *values
            end
            """
        )));
    }
}
