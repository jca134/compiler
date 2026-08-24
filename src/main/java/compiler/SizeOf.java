package compiler;

public final class SizeOf {
    private SizeOf() {}

    public static int of(TypeSpec t) {
        if (t == null) throw new IllegalArgumentException("type is null");

        final int baseSize = t.base.byteSize();

        long count = 1L;
        for (int d : t.dims) {
            if (d <= 0) throw new IllegalArgumentException("invalid dimension: " + d);
            try {
                count = Math.multiplyExact(count, (long) d);
            } catch (ArithmeticException ex) {
                throw new ArithmeticException("size overflow");
            }
        }

        final long total;
        try {
            total = Math.multiplyExact(count, (long) baseSize);
        } catch (ArithmeticException ex) {
            throw new ArithmeticException("size overflow");
        }
        if (total > Integer.MAX_VALUE) throw new ArithmeticException("size overflow");
        return (int) total;
    }
}
