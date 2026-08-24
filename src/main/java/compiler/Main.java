package compiler;
import java.nio.charset.StandardCharsets;

import compiler.grammar.LangLexer;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;

public class Main {

    public static void main(String[] args) throws Exception {
        CharStream input = (args.length > 0)
                ? CharStreams.fromFileName(args[0])
                : CharStreams.fromStream(System.in, StandardCharsets.UTF_8);

        LangLexer lexer = new LangLexer(input);

        // just tokenizing here, no parsing or type info - symbols only get a name
        Env env = new Env(null);
        StringBuilder out = new StringBuilder();
        Yylval yylval = new Yylval();

        for (Token tk = lexer.nextToken(); tk.getType() != Token.EOF; tk = lexer.nextToken()) {
            int ttype = tk.getType();
            fillYylval(ttype, tk, yylval);

            if (ttype == LangLexer.ID) {
                String name = tk.getText();
                if (env.get(name) == null) {
                    env.define(new Symbol(name));
                }
            }

            out.append(printable(ttype, tk)).append(' ');
        }

        System.out.println(out.toString().trim());
        System.out.println("\n--- Symbol Table ---");
        Env.printTable(env);
    }

    private static void fillYylval(int ttype, Token tk, Yylval y) {
        y.clear();
        String text = tk.getText();

        switch (ttype) {
            case LangLexer.BASIC -> {
                if ("int".equals(text)) y.set(BasicType.INT);
                else if ("float".equals(text)) y.set(BasicType.FLOAT);
                else if ("boolean".equals(text)) y.set(BasicType.BOOLEAN);
            }
            case LangLexer.ID -> y.set(text);
            case LangLexer.NUM -> {
                try { y.set(Integer.parseInt(text)); }
                catch (NumberFormatException e) { y.set(text); }
            }
            case LangLexer.REAL -> {
                try { y.set(Double.parseDouble(text)); }
                catch (NumberFormatException e) { y.set(text); }
            }
            case LangLexer.TRUE -> y.set(Boolean.TRUE);
            case LangLexer.FALSE -> y.set(Boolean.FALSE);
            default -> {}
        }
    }

    private static String printable(int ttype, Token tk) {
        return switch (ttype) {
            case LangLexer.BASIC -> "basic";
            case LangLexer.ID -> "id";
            case LangLexer.NUM -> "num";
            case LangLexer.REAL -> "real";
            case LangLexer.TRUE -> "true";
            case LangLexer.FALSE -> "false";
            case LangLexer.WHILE -> "while";
            default -> tk.getText();
        };
    }
}
