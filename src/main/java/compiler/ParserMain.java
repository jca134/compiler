package compiler;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;

import java.nio.charset.StandardCharsets;

public class ParserMain {
    public static void main(String[] args) throws Exception {
        CharStream input = (args.length > 0)
                ? CharStreams.fromFileName(args[0], StandardCharsets.UTF_8)
                : CharStreams.fromStream(System.in, StandardCharsets.UTF_8);

        CompilationResult result = new CompilerPipeline().compile(input);
        if (!result.successful()) {
            result.diagnostics().forEach(System.err::println);
            System.err.println("Compilation stopped: " + result.diagnostics().size() + " error(s).");
            System.exit(1);
        }

        Semantics semantics = result.semantics();
        String trace = semantics.ruleTrace();
        if (!trace.isEmpty()) {
            System.out.println(trace);
            System.out.println();
        }

        System.out.println("--- Symbol Table ---");
        Env.printTable(semantics.head());

        
        System.out.println("\n--- Intermediate Code ---");
        if (result.intermediateCode().list().isEmpty())
            System.out.println("(empty)");
        else
            result.intermediateCode().print();
    }
}
