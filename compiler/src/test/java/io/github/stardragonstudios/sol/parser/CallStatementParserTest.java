package io.github.stardragonstudios.sol.parser;

import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.syntax.AssignmentStatement;
import io.github.stardragonstudios.sol.syntax.CallExpression;
import io.github.stardragonstudios.sol.syntax.CallStatement;
import io.github.stardragonstudios.sol.syntax.FunctionDeclaration;
import io.github.stardragonstudios.sol.syntax.NameExpression;
import io.github.stardragonstudios.sol.syntax.QualifiedNameExpression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class CallStatementParserTest {
    @Test
    void parsesDirectCallStatements() {
        var declaration = parseFunction(
            """
            fn perform() -> void
                write(1)
                return
            end
            """
        );

        var statement = assertInstanceOf(CallStatement.class, declaration.body().orElseThrow().statements().getFirst());
        var call = statement.call();
        var callee = assertInstanceOf(NameExpression.class, call.callee());

        assertEquals("write", callee.name());
        assertEquals(1, call.arguments().size());
    }

    @Test
    void parsesNamespaceQualifiedCallStatements() {
        var declaration = parseFunction(
            """
            fn perform() -> void
                console::write(1)
                return
            end
            """
        );

        var statement = assertInstanceOf(
            CallStatement.class,
            declaration.body()
                .orElseThrow()
                .statements()
                .getFirst()
        );

        var qualified = assertInstanceOf(QualifiedNameExpression.class, statement.call().callee());

        assertEquals("console", qualified.qualifier().name());
        assertEquals("write", qualified.member().name());
    }

    @Test
    void continuesParsingAssignments() {
        var declaration = parseFunction(
            """
            fn update() -> int
                @mut let value: int = 0
                value = 1
                return value
            end
            """
        );

        assertInstanceOf(AssignmentStatement.class, declaration.body().orElseThrow().statements().get(1));
    }

    private static FunctionDeclaration parseFunction(String source) {
        var unit = Parser.parse(Lexer.scan(source));

        return assertInstanceOf(FunctionDeclaration.class, unit.declarations().getFirst());
    }
}