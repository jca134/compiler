package compiler;

import compiler.ir.*;

public final class Widen {
    private Widen() {}

    public static Address to(Address src, TypeSpec target, IntermediateCode ic, TempFactory temps) {
        if (src == null) throw new IllegalArgumentException("source address is null");
        if (target == null) throw new IllegalArgumentException("target type is null");
        if (ic == null) throw new IllegalArgumentException("ic is null");
        if (temps == null) throw new IllegalArgumentException("temps is null");

        TypeSpec from = src.type();
        if (from == null) throw new IllegalArgumentException("source has no type: " + src);

        // If types already match structurally, no conversion needed.
        if (from.equals(target)) return src;

        // Only support scalar int -> float widening.
        if (from.isScalar() && target.isScalar()
                && from.base == BasicType.INT && target.base == BasicType.FLOAT) {
            TempAddr t = new TempAddr(temps.newTemp(TypeSpec.scalar(BasicType.FLOAT)));
            ic.emit(Opcode.I2F, src, null, t);
            return t;
        }

        throw new IllegalArgumentException("Cannot widen from " + from + " to " + target);
    }
}
