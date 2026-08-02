package io.github.stardragonstudios.sol.ir;

import java.util.List;

public interface IrCallInstruction extends IrInstruction {
    IrFunctionReference target();

    List<IrValue> arguments();

    @Override
    default List<IrValue> operands() {
        return arguments();
    }
}
