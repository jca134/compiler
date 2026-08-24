package compiler;

import java.util.List;
import java.util.Objects;

public final class TypeSpec {
    public final BasicType base;
    public final List<Integer> dims;

    public TypeSpec(BasicType base, List<Integer> dims) {
        this.base = Objects.requireNonNull(base, "base");
        this.dims = dims == null ? List.of() : List.copyOf(dims);
    }

    public static TypeSpec scalar(BasicType base) {
        return new TypeSpec(base, List.of());
    }

    public boolean isScalar() {
        return dims.isEmpty();
    }

    public boolean isNumeric() {
        return isScalar() && base.isNumeric();
    }

    public boolean canAssignFrom(TypeSpec source) {
        Objects.requireNonNull(source, "source");
        return isScalar() && source.isScalar()
                && (base == source.base || base == BasicType.FLOAT && source.base == BasicType.INT);
    }

    public TypeSpec indexed(int indexCount) {
        if (indexCount < 0 || indexCount > dims.size()) {
            throw new IllegalArgumentException("invalid index count: " + indexCount);
        }
        return new TypeSpec(base, dims.subList(indexCount, dims.size()));
    }

    @Override public boolean equals(Object other) {
        return other instanceof TypeSpec type && base == type.base && dims.equals(type.dims);
    }

    @Override public int hashCode() {
        return Objects.hash(base, dims);
    }

    @Override public String toString() {
        if (dims.isEmpty()) return base.toString().toLowerCase();
        StringBuilder sb = new StringBuilder(base.toString().toLowerCase());
        for (int d : dims) sb.append('[').append(d).append(']');
        return sb.toString();
    }
}
