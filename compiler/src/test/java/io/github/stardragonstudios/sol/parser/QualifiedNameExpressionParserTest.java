package io.github.stardragonstudios.sol.parser;

import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.syntax.CallExpression;
import io.github.stardragonstudios.sol.syntax.FunctionDeclaration;
import io.github.stardragonstudios.sol.syntax.QualifiedNameExpression;
import io.github.stardragonstudios.sol.syntax.ReturnStatement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QualifiedNameExpressionParserTest {
    @Test
    void parsesNamespaceQualifiedFunctionCalls() {
        var source = """
            fn test() -> int
                return io::read()
            end
            """;

        var unit = Parser.parse(Lexer.scan(source));
        var function = assertInstanceOf(FunctionDeclaration.class, unit.declarations().getFirst());
        var returnStatement = assertInstanceOf(
            ReturnStatement.class,
            function.body()
                .orElseThrow()
                .statements()
                .getFirst()
        );

        var call = assertInstanceOf(CallExpression.class, returnStatement.expression().orElseThrow());
        var qualified = assertInstanceOf(QualifiedNameExpression.class, call.callee());

        assertEquals("io", qualified.qualifier().name());
        assertEquals("read", qualified.member().name());
        assertEquals(source.indexOf("io::read"), qualified.span().start().offset());
        assertEquals(source.indexOf("io::read") + "io::read".length(), qualified.span().end().offset());
        assertEquals(source.indexOf("io::read"), call.span().start().offset());
        assertEquals(source.indexOf("io::read()") + "io::read()".length(), call.span().end().offset());
    }

    @Test
    void rejectsChainedNamespaceQualification() {
        var source = """
            fn test() -> int
                return first::second::third()
            end
            """;

        assertThrows(ParsingException.class, () -> Parser.parse(Lexer.scan(source)));
    }
}
