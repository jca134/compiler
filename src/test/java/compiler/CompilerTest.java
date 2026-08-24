package compiler;

import static org.junit.jupiter.api.Assertions.*;

import compiler.grammar.LangLexer;
import compiler.grammar.LangParser;
import compiler.ir.IntermediateCode;
import compiler.ir.Instruction;
import compiler.ir.Opcode;
import compiler.ir.ConstAddr;
import compiler.ir.TempAddr;
import compiler.ir.VarAddr;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.junit.jupiter.api.Test;

import java.util.List;

final class CompilerTest {
    private record Analysis(LangParser.ProgramContext program, Semantics semantics, int syntaxErrors) {}

    private static Analysis analyze(String source) {
        LangLexer lexer = new LangLexer(CharStreams.fromString(source));
        LangParser parser = new LangParser(new CommonTokenStream(lexer));
        LangParser.ProgramContext program = parser.program();
        Env global = new Env(null);
        Semantics semantics = new Semantics(global);
        ParseTreeWalker.DEFAULT.walk(semantics, program);
        return new Analysis(program, semantics, parser.getNumberOfSyntaxErrors());
    }

    @Test void shadowedVariablesBindToTheirLexicalDeclarations() {
        Analysis result = analyze("""
            { int a; a = 5; { float a; a = 2.5; } a = a + 1; }
            """);
        assertEquals(0, result.syntaxErrors);
        assertTrue(result.semantics.errors().isEmpty(), result.semantics.errors().toString());

        IntermediateCode code = new IntermediateCode();
        new CodeGen(result.semantics, code).visitProgram(result.program);
        var assignments = code.list().stream().filter(i -> i.op == Opcode.ASSIGN).toList();

        VarAddr firstOuter = (VarAddr) assignments.get(0).dst;
        VarAddr inner = (VarAddr) assignments.get(1).dst;
        VarAddr lastOuter = (VarAddr) assignments.get(2).dst;
        assertEquals(BasicType.INT, firstOuter.type().base);
        assertEquals(BasicType.FLOAT, inner.type().base);
        assertSame(firstOuter.sym, lastOuter.sym);
        assertNotSame(firstOuter.sym, inner.sym);
    }

    @Test void reportsDeclarationTypeAndArrayErrorsTogether() {
        Analysis result = analyze("""
            { int x; int x; float f; int a[3];
              missing = 1;
              x = f;
              a[f] = 2;
            }
            """);
        String messages = String.join("\n", result.semantics.errors());
        assertTrue(messages.contains("duplicate declaration"), messages);
        assertTrue(messages.contains("undeclared identifier"), messages);
        assertTrue(messages.contains("cannot assign float to int"), messages);
        assertTrue(messages.contains("array index must have type int"), messages);
    }

    @Test void permitsIntToFloatWideningAndGeneratesConversion() {
        Analysis result = analyze("{ int x; float y; x = 2; y = x; }");
        assertTrue(result.semantics.errors().isEmpty(), result.semantics.errors().toString());
        IntermediateCode code = new IntermediateCode();
        new CodeGen(result.semantics, code).visitProgram(result.program);
        assertTrue(code.list().stream().anyMatch(i -> i.op == Opcode.I2F));
    }

    @Test void reportsOutOfRangeNumericLiteralsInsteadOfCrashingCodeGeneration() {
        Analysis integer = analyze("{ int x; x = 999999999999; }");
        assertTrue(String.join("\n", integer.semantics.errors()).contains("32-bit signed range"));

        Analysis floating = analyze("{ float x; x = 1e999; }");
        assertTrue(String.join("\n", floating.semantics.errors()).contains("floating-point literal"));
    }

    @Test void reportsNonPositiveArrayDimensionOnceInsteadOfCascadingIntoSizeOf() {
        Analysis result = analyze("{ int a[0]; }");
        assertEquals(List.of("Semantic error at 1:2: array dimensions must be positive"),
                result.semantics.errors());
    }

    @Test void storageAddressesDoNotOverlapAcrossInterleavedScopesOrTemporaries() {
        Analysis result = analyze("{ int a; { int inner; inner = 1 + 2; } int later; later = a + 3; }");
        assertTrue(result.semantics.errors().isEmpty(), result.semantics.errors().toString());

        IntermediateCode code = new IntermediateCode();
        new CodeGen(result.semantics, code).visitProgram(result.program);
        var addresses = code.list().stream()
                .flatMap(i -> java.util.stream.Stream.of(i.src1, i.src2, i.dst))
                .filter(a -> a instanceof VarAddr || a instanceof TempAddr)
                .map(a -> a instanceof VarAddr v ? v.sym : ((TempAddr) a).tempSym)
                .distinct()
                .map(s -> s.address)
                .toList();
        assertEquals(addresses.size(), addresses.stream().distinct().count(), addresses.toString());
    }

    @Test void loadAndStoreInstructionsHaveUnambiguousFormatting() {
        Symbol addressSymbol = new Symbol("addr");
        addressSymbol.type = TypeSpec.scalar(BasicType.INT);
        TempAddr address = new TempAddr(addressSymbol);
        Symbol valueSymbol = new Symbol("value");
        valueSymbol.type = TypeSpec.scalar(BasicType.INT);
        TempAddr value = new TempAddr(valueSymbol);

        assertEquals("value = load addr", new Instruction(Opcode.LOAD, address, null, value).toString());
        assertEquals("store 7 -> addr",
                new Instruction(Opcode.STORE, ConstAddr.ofInt(7), null, address).toString());
    }

    @Test void rejectsArraySizesThatOverflowLongArithmetic() {
        TypeSpec huge = new TypeSpec(BasicType.INT,
                java.util.List.of(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
        ArithmeticException error = assertThrows(ArithmeticException.class, () -> SizeOf.of(huge));
        assertEquals("size overflow", error.getMessage());
    }

    @Test void generatesAddressingStoreAndLoadForArrayElements() {
        Analysis result = analyze("{ int a[2]; int x; a[1] = 4; x = a[1]; }");
        assertTrue(result.semantics.errors().isEmpty(), result.semantics.errors().toString());

        IntermediateCode code = new IntermediateCode();
        new CodeGen(result.semantics, code).visitProgram(result.program);
        assertEquals(2, code.list().stream().filter(i -> i.op == Opcode.INDEX).count());
        assertEquals(1, code.list().stream().filter(i -> i.op == Opcode.STORE).count());
        assertEquals(1, code.list().stream().filter(i -> i.op == Opcode.LOAD).count());
    }

    @Test void generatesCompleteControlFlowForWhileLoop() {
        Analysis result = analyze("{ int i; i = 2; while (i) i = i - 1; }");
        assertTrue(result.semantics.errors().isEmpty(), result.semantics.errors().toString());

        IntermediateCode code = new IntermediateCode();
        new CodeGen(result.semantics, code).visitProgram(result.program);
        assertEquals(3, code.list().stream().filter(i -> i.op == Opcode.LABEL).count());
        assertEquals(2, code.list().stream().filter(i -> i.op == Opcode.GOTO).count());
        assertEquals(1, code.list().stream().filter(i -> i.op == Opcode.IFGOTO).count());
    }

    @Test void rejectsArraysAndBooleansInArithmetic() {
        Analysis result = analyze("{ int a[2]; boolean flag; int x; x = a + flag; }");
        String messages = String.join("\n", result.semantics.errors());
        assertTrue(messages.contains("require scalar numeric operands"), messages);
    }

    @Test void widensIntegerOperandInMixedArithmetic() {
        Analysis result = analyze("{ int x; float y; y = x + 1.5; }");
        assertTrue(result.semantics.errors().isEmpty(), result.semantics.errors().toString());

        IntermediateCode code = new IntermediateCode();
        new CodeGen(result.semantics, code).visitProgram(result.program);
        assertEquals(List.of(Opcode.I2F, Opcode.ADD, Opcode.ASSIGN),
                code.list().stream().map(i -> i.op).toList());
    }

    @Test void temporaryNamesDoNotCollideWithUserDeclarations() {
        Analysis result = analyze("{ int t0; int x; x = t0 + 1; }");
        assertTrue(result.semantics.errors().isEmpty(), result.semantics.errors().toString());

        IntermediateCode code = new IntermediateCode();
        new CodeGen(result.semantics, code).visitProgram(result.program);
        TempAddr temporary = (TempAddr) code.list().stream()
                .filter(i -> i.op == Opcode.ADD)
                .findFirst().orElseThrow().dst;
        assertEquals("t1", temporary.repr());
        assertNotSame(result.semantics.head().get("t0"), temporary.tempSym);
        assertNull(result.semantics.head().get("t1"), "temporaries must not enter lexical lookup");
    }
}
