package compiler.ir;

import compiler.TypeSpec;

public final class LabelAddr implements Address {
    public final String name;

    public LabelAddr(String name) {
        this.name = name;
    }

    @Override public TypeSpec type() { return null; }

    @Override public String repr() { return name; }

    @Override public String toString() { return repr(); }
}
