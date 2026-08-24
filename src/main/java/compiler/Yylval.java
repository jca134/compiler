package compiler;

public final class Yylval {
    public Object value;
    public void set(Object v) { this.value = v; }
    public void clear() { this.value = null; }

    @Override public String toString() {
        return String.valueOf(value);
    }
}
