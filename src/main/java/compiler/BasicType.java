package compiler;

public enum BasicType {
    INT(Integer.BYTES),
    FLOAT(Double.BYTES),
    BOOLEAN(1);

    private final int byteSize;

    BasicType(int byteSize) {
        this.byteSize = byteSize;
    }

    public int byteSize() {
        return byteSize;
    }

    public boolean isNumeric() {
        return this == INT || this == FLOAT;
    }
}
