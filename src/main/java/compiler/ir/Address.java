package compiler.ir;

import compiler.TypeSpec;

public interface Address {
    TypeSpec type();
    String repr();
}
