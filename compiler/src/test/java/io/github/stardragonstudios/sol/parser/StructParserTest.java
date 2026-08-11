package io.github.stardragonstudios.sol.parser;

import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.syntax.FieldAccessExpression;
import io.github.stardragonstudios.sol.syntax.FieldAssignmentStatement;
import io.github.stardragonstudios.sol.syntax.FunctionDeclaration;
import io.github.stardragonstudios.sol.syntax.ReturnStatement;
import io.github.stardragonstudios.sol.syntax.StructConstructionExpression;
import io.github.stardragonstudios.sol.syntax.StructDeclaration;
import io.github.stardragonstudios.sol.syntax.VariableDeclarationStatement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StructParserTest {
    @Test
    void parsesStructDeclarationsAndMultilineConstruction() {
        var unit = Parser.parse(Lexer.scan(
            """
            struct Position
                line: int
                column: int
            end

            fn create() -> Position
                return Position {
                    column: 2,
                    line: 1,
                }
            end
            """
        ));

        var struct = assertInstanceOf(StructDeclaration.class, unit.declarations().get(0));

        assertEquals("Position", struct.name());
        assertEquals(2, struct.fields().size());
        assertEquals("line", struct.fields().get(0).name());
        assertEquals("int", struct.fields().get(0).type().name());
        assertEquals("column", struct.fields().get(1).name());

        var function = assertInstanceOf(FunctionDeclaration.class, unit.declarations().get(1));
        var returned = assertInstanceOf(ReturnStatement.class, function.body().orElseThrow().statements().getFirst());
        var construction = assertInstanceOf(StructConstructionExpression.class, returned.expression().orElseThrow());

        assertEquals("Position", construction.type().name());
        assertEquals(2, construction.fields().size());
        assertEquals("column", construction.fields().get(0).name());
        assertEquals("line", construction.fields().get(1).name());
    }

    @Test
    void parsesChainedFieldAccessAndMutation() {
        var unit = Parser.parse(Lexer.scan(
            """
            struct Point
                x: int
            end

            struct Box
                point: Point
            end

            fn update(box: Box) -> int
                @mut let copy: Box = box
                copy.point.x = 42
                return copy.point.x
            end
            """
        ));

        var function = assertInstanceOf(FunctionDeclaration.class, unit.declarations().get(2));
        var statements = function.body().orElseThrow().statements();
        var declaration = assertInstanceOf(VariableDeclarationStatement.class, statements.get(0));
        var assignment = assertInstanceOf(FieldAssignmentStatement.class, statements.get(1));
        var returned = assertInstanceOf(ReturnStatement.class, statements.get(2));
        var returnedField = assertInstanceOf(FieldAccessExpression.class, returned.expression().orElseThrow());

        assertEquals("copy", declaration.name());
        assertEquals("x", assignment.target().fieldName());
        assertEquals("point", assertInstanceOf(FieldAccessExpression.class, assignment.target().target()).fieldName());
        assertEquals("x", returnedField.fieldName());
        assertTrue(assignment.span().start().offset() < assignment.span().end().offset());
    }

    @Test
    void parsesEmptyStructsAndEmptyConstruction() {
        var unit = Parser.parse(Lexer.scan(
            """
            struct Marker
            end

            fn marker() -> Marker
                return Marker {}
            end
            """
        ));

        assertTrue(assertInstanceOf(StructDeclaration.class, unit.declarations().getFirst()).fields().isEmpty());

        var function = assertInstanceOf(FunctionDeclaration.class, unit.declarations().get(1));
        var returned = assertInstanceOf(ReturnStatement.class, function.body().orElseThrow().statements().getFirst());

        assertTrue(assertInstanceOf(StructConstructionExpression.class, returned.expression().orElseThrow()).fields().isEmpty());
    }
}
