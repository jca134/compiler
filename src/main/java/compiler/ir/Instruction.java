package compiler.ir;

import java.util.Objects;

public final class Instruction {
    public final Opcode op;
    public final Address src1, src2, dst;

    public Instruction(Opcode op, Address s1, Address s2, Address dst) {
        if (op == null) throw new IllegalArgumentException("Opcode cannot be null");
        switch (op) {
            case LABEL:
            case GOTO:
                if (dst == null) throw new IllegalArgumentException(op + " requires non-null dst");
                break;
            case IFGOTO:
                if (s1 == null || dst == null)
                    throw new IllegalArgumentException("IFGOTO requires non-null condition (src1) and dst");
                break;
            case ASSIGN:
            case I2F:
                if (s1 == null || dst == null)
                    throw new IllegalArgumentException(op + " requires non-null src1 and dst");
                break;
            case INDEX:
                if (s1 == null || s2 == null || dst == null)
                    throw new IllegalArgumentException("INDEX requires non-null src1, src2, and dst");
                break;
            case ADD:
            case SUB:
            case MUL:
            case DIV:
            case SHL:
            case SHR:
                if (s1 == null || s2 == null || dst == null)
                    throw new IllegalArgumentException(op + " requires non-null src1, src2, and dst");
                break;
            case LOAD:
                if (s1 == null || dst == null)
                    throw new IllegalArgumentException("LOAD requires non-null src1 (address) and dst");
                break;
            case STORE:
                if (s1 == null || dst == null)
                    throw new IllegalArgumentException("STORE requires non-null src1 (value) and dst (address)");
                break;
            default:
                // nothing else needs validating
                break;
        }
        this.op = op;
        this.src1 = s1;
        this.src2 = s2;
        this.dst = dst;
    }

    private static String r(Address a) { return a == null ? "_" : a.repr(); }

    @Override
    public String toString() {
        // Null-safe pretty printer so partially-formed instructions don't crash
        switch (op) {
            case LABEL:
                return r(dst) + ":";
            case GOTO:
                return "goto " + r(dst);
            case IFGOTO:
                return "if " + r(src1) + " goto " + r(dst);
            case ASSIGN:
                return r(dst) + " = " + r(src1);
            case I2F:
                return r(dst) + " = (float) " + r(src1);
            case INDEX:
                return r(dst) + " = " + r(src1) + "[" + r(src2) + "]";
            case LOAD:
                return r(dst) + " = load " + r(src1);
            case STORE:
                return "store " + r(src1) + " -> " + r(dst);
            default:
                // Binary math/logical ops. If src2 is missing, print as unary to avoid NPEs.
                if (src2 == null) {
                    return r(dst) + " = " + op.name().toLowerCase() + " " + r(src1);
                }
                // Pretty-print shifts using << and >> to match assignment notation.
                if (op == Opcode.SHL) {
                    return r(dst) + " = " + r(src1) + " << " + r(src2);
                }
                if (op == Opcode.SHR) {
                    return r(dst) + " = " + r(src1) + " >> " + r(src2);
                }
                return r(dst) + " = " + r(src1) + " " + op.name().toLowerCase() + " " + r(src2);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Instruction that = (Instruction) o;
        return op == that.op &&
               Objects.equals(src1, that.src1) &&
               Objects.equals(src2, that.src2) &&
               Objects.equals(dst, that.dst);
    }

    @Override
    public int hashCode() {
        return Objects.hash(op, src1, src2, dst);
    }
}
