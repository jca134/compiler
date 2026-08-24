package compiler.ir;

import compiler.Symbol;
import compiler.TypeSpec;

public final class VarAddr implements Address {
    public final Symbol sym;

    public VarAddr(Symbol sym) {
        this.sym = sym;
    }

    @Override public TypeSpec type() { return sym.type; }

    @Override public String repr() { return sym.name; }

    @Override public String toString() { return repr(); }
}
