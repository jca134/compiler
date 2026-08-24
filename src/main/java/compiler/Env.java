package compiler;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Env {
    private static final class AddressSpace {
        long nextAddress;
    }

    private final Map<String, Symbol> table = new LinkedHashMap<>();
    protected final Env prev;
    private final AddressSpace addressSpace;

    private long base = 0L;

    private long nextOffset = 0L;

    private long frameSize = 0L;

    public Env(Env prev) {
        this.prev = prev;
        this.addressSpace = prev == null ? new AddressSpace() : prev.addressSpace;
    }

    public Symbol get(String s) {
        for (Env e = this; e != null; e = e.prev) {
            Symbol found = e.table.get(s);
            if (found != null) return found;
        }
        return null;
    }

    public Symbol getLocal(String s) { return table.get(s); }

    public void define(Symbol symbol) {
        if (symbol == null) throw new IllegalArgumentException("symbol is null");
        table.putIfAbsent(symbol.name, symbol);
    }

    public void initFrame(int controlLinkSize) {
        if (controlLinkSize < 0) throw new IllegalArgumentException("negative control link size");
        // all scopes pull from one shared cursor, so a later outer-scope decl
        // can't land on top of an inner scope we already laid out
        this.base = addressSpace.nextAddress;
        addressSpace.nextAddress = Math.addExact(addressSpace.nextAddress, controlLinkSize);
        this.nextOffset = controlLinkSize;
        this.frameSize = controlLinkSize;
    }

    private long allocate(TypeSpec type) {
        if (type == null) throw new IllegalArgumentException("type is null");
        int sz = SizeOf.of(type);
        long offset = nextOffset;
        nextOffset += sz;
        frameSize = nextOffset;
        return offset;
    }

    private long allocateAddress(TypeSpec type) {
        if (type == null) throw new IllegalArgumentException("type is null");
        long address = addressSpace.nextAddress;
        addressSpace.nextAddress = Math.addExact(addressSpace.nextAddress, SizeOf.of(type));
        return address;
    }

    public void declare(Symbol symbol) {
        allocateStorage(symbol);
        define(symbol);
    }

    public void allocateTemporary(Symbol symbol) {
        allocateStorage(symbol);
    }

    private void allocateStorage(Symbol symbol) {
        if (symbol == null || symbol.type == null) {
            throw new IllegalArgumentException("symbol and symbol type are required");
        }
        symbol.offset = allocate(symbol.type);
        symbol.address = allocateAddress(symbol.type);
    }

    public static void printTable(Env node) {
        for (Env e = node; e != null; e = e.prev) {
            System.out.println(e);
            for (Map.Entry<String, Symbol> entry : e.table.entrySet()) {
                System.out.println(entry);
            }
        }
    }

    @Override public String toString() {
        return "\nEnv(size=" + table.size() +
               ", base=" + base +
               ", frameSize=" + frameSize +
               ", id=" + Integer.toHexString(System.identityHashCode(this)) + ")";
    }
}
