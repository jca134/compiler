package compiler;

import compiler.grammar.LangParser;
import compiler.ir.IntermediateCode;
import java.util.List;

public record CompilationResult(
        LangParser.ProgramContext program,
        Semantics semantics,
        IntermediateCode intermediateCode,
        List<String> diagnostics) {

    public CompilationResult {
        diagnostics = List.copyOf(diagnostics);
    }

    public boolean successful() {
        return diagnostics.isEmpty();
    }
}
