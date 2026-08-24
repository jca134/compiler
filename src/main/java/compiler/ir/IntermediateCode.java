package compiler.ir;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import compiler.BasicType;
import compiler.TypeSpec;

public final class IntermediateCode {
    private final List<Instruction> code = new ArrayList<>();

    public void emit(Instruction instruction) {
        code.add(Objects.requireNonNull(instruction, "instruction"));
    }

    public void emit(Opcode op, Address a, Address b, Address c) {
        emit(new Instruction(op, a, b, c));
    }

    public List<Instruction> list() { return Collections.unmodifiableList(code); }

    public void applyStrengthReduction() {
        List<Instruction> optimized = new ArrayList<>();
        for (Instruction inst : code) {
            if (inst.op == Opcode.MUL) {
                Instruction[] transformed = strengthReduceMul(inst);
                if (transformed != null) {
                    Collections.addAll(optimized, transformed);
                    continue;
                }
            }
            optimized.add(inst);
        }
        code.clear();
        code.addAll(optimized);
    }

    // printing

    public void print() {
        System.out.print(format());
    }

    public String format() {
        int n = code.size(), w = Math.max(1, String.valueOf(n).length());
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < n; i++) {
            String idx = String.format("(%" + w + "d)", i + 1);
            output.append(idx).append("  ").append(code.get(i)).append(System.lineSeparator());
        }
        return output.toString();
    }

    // strength reduction helpers

    private static boolean isIntScalar(Address a) {
        if (a == null) return false;
        TypeSpec t = a.type();
        if (t == null) return false;
        if (t.base != BasicType.INT) return false;
        return t.dims.isEmpty();
    }

    private static Integer constIntValue(Address a) {
        if (!(a instanceof ConstAddr constant)) return null;
        if (!isIntScalar(a)) return null;
        return constant.intValue();
    }

    private static boolean isPowerOfTwo(int k) {
        return k > 0 && (k & (k - 1)) == 0;
    }

    private static Instruction[] strengthReduceMul(Instruction inst) {
        // Only consider scalar integer multiplies
        if (!isIntScalar(inst.dst)) return null;

        Address x = null;
        Integer kVal = null;

        Integer c1 = constIntValue(inst.src1);
        Integer c2 = constIntValue(inst.src2);

        if (c1 != null && isIntScalar(inst.src2)) {
            kVal = c1;
            x = inst.src2;
        } else if (c2 != null && isIntScalar(inst.src1)) {
            kVal = c2;
            x = inst.src1;
        } else {
            return null;
        }

        int k = kVal;
        if (k == 0 || k == 1) {
            // Not reduced.
            return null;
        }

        // Case 1: k is an exact power of two => shift left
        if (isPowerOfTwo(k)) {
            int n = Integer.numberOfTrailingZeros(k);
            Address shift = ConstAddr.ofInt(n);
            Instruction shl = new Instruction(Opcode.SHL, x, shift, inst.dst);
            return new Instruction[] { shl };
        }

        // Case 2: k = 2^n + 1 or 2^n - 1
        int nPlus = -1;
        int nMinus = -1;
        for (int n = 0; n < 31; n++) {
            int pow = 1 << n;
            if (k == pow + 1) {
                nPlus = n;
                break;
            }
            if (k == pow - 1) {
                nMinus = n;
                break;
            }
        }

        if (nPlus >= 0) {
            Address shift = ConstAddr.ofInt(nPlus);
            Instruction shl = new Instruction(Opcode.SHL, x, shift, inst.dst);
            Instruction add = new Instruction(Opcode.ADD, inst.dst, x, inst.dst);
            return new Instruction[] { shl, add };
        }

        if (nMinus >= 0) {
            Address shift = ConstAddr.ofInt(nMinus);
            Instruction shl = new Instruction(Opcode.SHL, x, shift, inst.dst);
            Instruction sub = new Instruction(Opcode.SUB, inst.dst, x, inst.dst);
            return new Instruction[] { shl, sub };
        }

        // No applicable pattern.
        return null;
    }
}
