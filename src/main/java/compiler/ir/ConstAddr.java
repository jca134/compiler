package compiler.ir;
import compiler.BasicType;
import compiler.TypeSpec;

public final class ConstAddr implements Address {
    private final Object value;
    private final TypeSpec type;

    public static ConstAddr ofInt(int v) {
        return new ConstAddr(v, TypeSpec.scalar(BasicType.INT));
    }

    public static ConstAddr ofFloat(double v) {
        return new ConstAddr(v, TypeSpec.scalar(BasicType.FLOAT));
    }

    public static ConstAddr ofBool(boolean v) {
        return new ConstAddr(v, TypeSpec.scalar(BasicType.BOOLEAN));
    }

    private ConstAddr(Object v, TypeSpec t) {
        this.value = v;
        this.type = t;
    }

    @Override
    public TypeSpec type() { return type; }

    @Override
    public String repr() { return String.valueOf(value); }

    public Integer intValue() {
        return type.base == BasicType.INT ? (Integer) value : null;
    }

    @Override
    public String toString() { return repr(); }
}
