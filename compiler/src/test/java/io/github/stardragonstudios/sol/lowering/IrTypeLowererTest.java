package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.ir.PrimitiveIrType;
import io.github.stardragonstudios.sol.semantics.types.BuiltInTypes;
import io.github.stardragonstudios.sol.semantics.types.TypeKind;
import io.github.stardragonstudios.sol.semantics.types.TypeSymbol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IrTypeLowererTest {
    @Test
    void lowersCanonicalPrimitiveTypes() {
        assertSame(PrimitiveIrType.INT,     IrTypeLowerer.lower(BuiltInTypes.INT));
        assertSame(PrimitiveIrType.FLOAT,   IrTypeLowerer.lower(BuiltInTypes.FLOAT));
        assertSame(PrimitiveIrType.BOOLEAN, IrTypeLowerer.lower(BuiltInTypes.BOOLEAN));
        assertSame(PrimitiveIrType.CHAR,    IrTypeLowerer.lower(BuiltInTypes.CHAR));
        assertSame(PrimitiveIrType.STRING,  IrTypeLowerer.lower(BuiltInTypes.STRING));
        assertSame(PrimitiveIrType.VOID,    IrTypeLowerer.lower(BuiltInTypes.VOID));
    }

    @Test
    void rejectsSemanticErrorType() {
        assertThrows(
            IrLoweringException.class,
            () -> IrTypeLowerer.lower(BuiltInTypes.ERROR)
        );
    }

    @Test
    void doesNotResolveTypesByName() {
        TypeSymbol impostor = new TypeSymbol() {
            @Override public TypeKind kind() {return TypeKind.PRIMITIVE;}
            @Override public String   name() {return "int";}
            @Override public boolean  isValue() {return true;}
            @Override public boolean  isNumeric() {return true;}
            @Override public boolean  isIntegral() {return true;}
        };

        assertThrows(
            IrLoweringException.class,
            () -> IrTypeLowerer.lower(impostor)
        );
    }

    @Test
    void rejectsNullTypes() {
        assertThrows(
            NullPointerException.class,
            () -> IrTypeLowerer.lower(null)
        );
    }
}
