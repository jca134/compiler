package compiler;

import static org.junit.jupiter.api.Assertions.*;

import compiler.ir.*;
import java.util.List;
import org.junit.jupiter.api.Test;

final class IntermediateCodeTest {
    @Test void reducesPowerOfTwoMultiplicationOnEitherSide() {
        assertEquals(List.of(Opcode.SHL), optimizedOpcodes(ConstAddr.ofInt(8), variable("x")));
        assertEquals(List.of(Opcode.SHL), optimizedOpcodes(variable("x"), ConstAddr.ofInt(4)));
    }

    @Test void reducesOneMoreOrLessThanPowerOfTwo() {
        assertEquals(List.of(Opcode.SHL, Opcode.ADD),
                optimizedOpcodes(variable("x"), ConstAddr.ofInt(5)));
        assertEquals(List.of(Opcode.SHL, Opcode.SUB),
                optimizedOpcodes(variable("x"), ConstAddr.ofInt(7)));
    }

    @Test void leavesUnsupportedMultiplicationUntouched() {
        assertEquals(List.of(Opcode.MUL), optimizedOpcodes(variable("x"), ConstAddr.ofInt(6)));
        assertEquals(List.of(Opcode.MUL), optimizedOpcodes(variable("x"), ConstAddr.ofInt(0)));
    }

    @Test void formatsNumberedInstructionsWithoutWritingDuringConstruction() {
        IntermediateCode code = new IntermediateCode();
        code.emit(Opcode.ASSIGN, ConstAddr.ofInt(1), null, variable("x"));
        assertEquals("(1)  x = 1" + System.lineSeparator(), code.format());
    }

    @Test void rejectsNullInstructionsAndMalformedOperands() {
        IntermediateCode code = new IntermediateCode();
        assertThrows(NullPointerException.class, () -> code.emit((Instruction) null));
        assertThrows(IllegalArgumentException.class,
                () -> new Instruction(Opcode.ADD, variable("x"), null, variable("out")));
    }

    private static List<Opcode> optimizedOpcodes(Address left, Address right) {
        IntermediateCode code = new IntermediateCode();
        code.emit(Opcode.MUL, left, right, variable("out"));
        code.applyStrengthReduction();
        return code.list().stream().map(instruction -> instruction.op).toList();
    }

    private static VarAddr variable(String name) {
        Symbol symbol = new Symbol(name);
        symbol.type = TypeSpec.scalar(BasicType.INT);
        return new VarAddr(symbol);
    }
}
