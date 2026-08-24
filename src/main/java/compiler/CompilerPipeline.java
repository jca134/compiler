package compiler;

import compiler.grammar.LangLexer;
import compiler.grammar.LangParser;
import compiler.ir.IntermediateCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

// wires up lex -> parse -> semantics -> codegen, bailing out early if a stage reports diagnostics
public final class CompilerPipeline {
    public CompilationResult compile(CharStream input) {
        Objects.requireNonNull(input, "input");
        List<String> diagnostics = new ArrayList<>();
        BaseErrorListener errorListener = new DiagnosticListener(diagnostics);

        LangLexer lexer = new LangLexer(input);
        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);

        LangParser parser = new LangParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);
        LangParser.ProgramContext program = parser.program();
        if (!diagnostics.isEmpty()) {
            return new CompilationResult(program, null, null, diagnostics);
        }

        Semantics semantics = new Semantics(new Env(null));
        ParseTreeWalker.DEFAULT.walk(semantics, program);
        if (semantics.hasErrors()) {
            return new CompilationResult(program, semantics, null, semantics.errors());
        }

        IntermediateCode code = new IntermediateCode();
        new CodeGen(semantics, code).visitProgram(program);
        code.applyStrengthReduction();
        return new CompilationResult(program, semantics, code, List.of());
    }

    private static final class DiagnosticListener extends BaseErrorListener {
        private final List<String> diagnostics;

        private DiagnosticListener(List<String> diagnostics) {
            this.diagnostics = diagnostics;
        }

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                int line, int charPositionInLine, String message, RecognitionException cause) {
            diagnostics.add("Syntax error at " + line + ":" + charPositionInLine + ": " + message);
        }
    }
}
