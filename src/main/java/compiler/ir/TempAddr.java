package compiler.ir;

import compiler.Symbol;
import compiler.TypeSpec;

public final class TempAddr implements Address {
    public final Symbol tempSym;

    public TempAddr(Symbol tempSym) {
        this.tempSym = tempSym;
    }

    @Override public TypeSpec type() { return tempSym.type; }

    @Override public String repr() { return tempSym.name; }

    @Override public String toString() { return repr(); }
}
