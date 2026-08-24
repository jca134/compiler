package compiler;

public final class Symbol {
    public final String name;
    public TypeSpec type;

    // offset starts over in each new scope (position within that block's own frame),
    // address just keeps climbing across the whole program - that's the one CodeGen uses
    public long offset = -1L;
    public long address = -1L;

    public Symbol(String name) { this.name = name; }

    @Override public String toString() {
        if (type == null) {
            return "Symbol(" + name + ")";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Symbol(").append(name).append(" : ").append(type);
        if (offset >= 0) {
            sb.append(", offset=").append(offset);
        }
        if (address >= 0) {
            sb.append(", addr=").append(address);
        }
        sb.append(")");
        return sb.toString();
    }
}
