package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.ir.IrBlockId;
import io.github.stardragonstudios.sol.ir.IrIntConstant;
import io.github.stardragonstudios.sol.ir.IrUnaryInstruction;
import io.github.stardragonstudios.sol.ir.IrUnaryOperator;
import io.github.stardragonstudios.sol.ir.IrValueId;
import io.github.stardragonstudios.sol.ir.PrimitiveIrType;
import io.github.stardragonstudios.sol.ir.IrReturnTerminator;
import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.parser.Parser;
import io.github.stardragonstudios.sol.semantics.FunctionSymbol;
import io.github.stardragonstudios.sol.semantics.ParameterSymbol;
import io.github.stardragonstudios.sol.semantics.SemanticAnalyzer;
import io.github.stardragonstudios.sol.syntax.FunctionDeclaration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IrFunctionLoweringContextTest {
    @Test
    void allocatesParameterAndValueIdentifiersInOrder() {
        var symbols = symbols(
            """
            fn calculate(left: int, right: float) -> int
                return left
            end
            """
        );

        var context = new IrFunctionLoweringContext(symbols.function());
        var left = context.declareParameter(symbols.parameters().get(0), PrimitiveIrType.INT);
        var right = context.declareParameter(symbols.parameters().get(1), PrimitiveIrType.FLOAT);

        assertEquals(new IrValueId(0), left.id());
        assertEquals(new IrValueId(1), right.id());
        assertEquals(new IrValueId(2), context.nextValueId());
        assertSame(left, context.parameter(symbols.parameters().get(0)));
        assertSame(right, context.parameter(symbols.parameters().get(1)));
    }

    @Test
    void allocatesBlockIdentifiersLocallyInOrder() {
        var symbols = symbols(
            """
            fn calculate() -> int
                return 1
            end
            """
        );

        var context = new IrFunctionLoweringContext(symbols.function());

        assertEquals(new IrBlockId(0), context.nextBlockId());
        assertEquals(new IrBlockId(1), context.nextBlockId());
    }

    @Test
    void preservesInstructionEmissionOrder() {
        var symbols = symbols(
            """
            fn calculate() -> int
                return 1
            end
            """
        );

        var context = new IrFunctionLoweringContext(symbols.function());
        var constant = new IrIntConstant(context.nextValueId(), 1);
        var first = new IrUnaryInstruction(context.nextValueId(), IrUnaryOperator.NEGATE, constant);
        var second = new IrUnaryInstruction(context.nextValueId(), IrUnaryOperator.POSITIVE, first);

        context.emit(first);
        context.emit(second);

        assertEquals(List.of(first, second), context.instructions());

        assertThrows(
            UnsupportedOperationException.class,
            () -> context.instructions().clear()
        );
    }

    @Test
    void rejectsDuplicateInstructionEmission() {
        var symbols = symbols(
            """
            fn calculate() -> int
                return 1
            end
            """
        );

        var context = new IrFunctionLoweringContext(symbols.function());
        var instruction = new IrUnaryInstruction(
            context.nextValueId(),
            IrUnaryOperator.NEGATE,
            new IrIntConstant(context.nextValueId(), 1)
        );

        context.emit(instruction);

        assertThrows(
            IrLoweringException.class,
            () -> context.emit(instruction)
        );
    }

    @Test
    void rejectsForeignAndDuplicateParameters() {
        var first = symbols(
            """
            fn first(value: int) -> int
                return value
            end
            """
        );

        var second = symbols(
            """
            fn second(other: int) -> int
                return other
            end
            """
        );

        var context = new IrFunctionLoweringContext(first.function());
        var parameter = first.parameters().getFirst();
        context.declareParameter(parameter, PrimitiveIrType.INT);

        assertThrows(
            IrLoweringException.class,
            () -> context.declareParameter(parameter, PrimitiveIrType.INT)
        );

        assertThrows(
            IrLoweringException.class,
            () -> context.declareParameter(second.parameters().getFirst(), PrimitiveIrType.INT)
        );
    }

    @Test
    void usesCanonicalParameterSymbolIdentity() {
        var symbols = symbols(
            """
            fn calculate(value: int) -> int
                return value
            end
            """
        );

        var canonical = symbols.parameters().getFirst();
        var equivalentWrapper = new ParameterSymbol(canonical.declaration());
        var context = new IrFunctionLoweringContext(symbols.function());
        context.declareParameter(canonical, PrimitiveIrType.INT);

        assertThrows(
            IrLoweringException.class,
            () -> context.parameter(equivalentWrapper)
        );
    }

    @Test
    void rejectsInvalidContextInputs() {
        var symbols = symbols(
            """
            fn calculate(value: int) -> int
                return value
            end
            """
        );

        assertThrows(
            NullPointerException.class,
            () -> new IrFunctionLoweringContext(null)
        );

        var context = new IrFunctionLoweringContext(symbols.function());

        assertThrows(
            NullPointerException.class,
            () -> context.declareParameter(null, PrimitiveIrType.INT)
        );

        assertThrows(
            NullPointerException.class,
            () -> context.declareParameter(symbols.parameters().getFirst(), null)
        );

        assertThrows(
            NullPointerException.class,
            () -> context.parameter(null)
        );

        assertThrows(
            NullPointerException.class,
            () -> context.emit(null)
        );
    }

    @Test
    void finishesFunctionBlocksExactlyOnce() {
        var symbols = symbols(
            """
            fn calculate() -> int
                return 1
            end
            """
        );

        var context = new IrFunctionLoweringContext(symbols.function());
        var instruction = new IrUnaryInstruction(
            context.nextValueId(),
            IrUnaryOperator.NEGATE,
            new IrIntConstant(
                context.nextValueId(),
                1
            )
        );

        context.emit(instruction);

        var block = context.finishBlock(IrReturnTerminator.returning(instruction));

        assertEquals(new IrBlockId(0), block.id());
        assertEquals(List.of(instruction), block.instructions());
        assertSame(instruction, ((IrReturnTerminator) block.terminator()).value().orElseThrow());

        assertThrows(
            IrLoweringException.class,
            () -> context.finishBlock(IrReturnTerminator.bare())
        );
    }

    @Test
    void rejectsInstructionEmissionAfterTermination() {
        var symbols = symbols(
            """
            fn perform() -> void
                return
            end
            """
        );

        var context = new IrFunctionLoweringContext(symbols.function());

        context.finishBlock(IrReturnTerminator.bare());

        var instruction = new IrUnaryInstruction(
            context.nextValueId(),
            IrUnaryOperator.NEGATE,
            new IrIntConstant(
                context.nextValueId(),
                1
            )
        );

        assertThrows(
            IrLoweringException.class,
            () -> context.emit(instruction)
        );
    }

    private static Symbols symbols(String source) {
        var unit = Parser.parse(Lexer.scan(source));
        var result = SemanticAnalyzer.analyze(unit);
        var declaration = (FunctionDeclaration) unit.declarations().getFirst();
        var function = result.model().symbolOf(declaration).orElseThrow();
        var parameters = declaration.parameters()
                .stream()
                .map(parameter -> result.model().symbolOf(parameter).orElseThrow())
                .toList();

        return new Symbols(function, parameters);
    }

    private record Symbols(FunctionSymbol function, List<ParameterSymbol> parameters) {}
}
