package compiler;

import static org.junit.jupiter.api.Assertions.*;

import compiler.ir.Opcode;
import org.antlr.v4.runtime.CharStreams;
import org.junit.jupiter.api.Test;

final class CompilerPipelineTest {
    private final CompilerPipeline compiler = new CompilerPipeline();

    @Test void successfulCompilationRunsEveryStageAndOptimization() {
        CompilationResult result = compiler.compile(CharStreams.fromString("{ int x; x = 2 * 4; }"));

        assertTrue(result.successful(), result.diagnostics().toString());
        assertNotNull(result.program());
        assertNotNull(result.semantics());
        assertNotNull(result.intermediateCode());
        assertTrue(result.intermediateCode().list().stream().anyMatch(i -> i.op == Opcode.SHL));
        assertFalse(result.intermediateCode().list().stream().anyMatch(i -> i.op == Opcode.MUL));
    }

    @Test void syntaxErrorsStopBeforeSemanticAnalysis() {
        CompilationResult result = compiler.compile(CharStreams.fromString("{ int x x = 1; }"));

        assertFalse(result.successful());
        assertTrue(result.diagnostics().getFirst().startsWith("Syntax error at"));
        assertNull(result.semantics());
        assertNull(result.intermediateCode());
    }

    @Test void lexerErrorsAreReportedThroughTheSameDiagnosticChannel() {
        CompilationResult result = compiler.compile(CharStreams.fromString("{ int x; x = @; }"));

        assertFalse(result.successful());
        assertTrue(result.diagnostics().stream().anyMatch(message -> message.contains("token recognition error")));
        assertNull(result.intermediateCode());
    }

    @Test void semanticErrorsStopBeforeCodeGeneration() {
        CompilationResult result = compiler.compile(CharStreams.fromString("{ int x; x = 1.5; }"));

        assertFalse(result.successful());
        assertNotNull(result.semantics());
        assertNull(result.intermediateCode());
        assertTrue(result.diagnostics().getFirst().contains("cannot assign float to int"));
    }
}
