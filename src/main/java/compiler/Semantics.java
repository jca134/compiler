package compiler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import compiler.grammar.LangParser;
import compiler.grammar.LangParserBaseListener;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTreeProperty;

// one tree walk: builds scopes and checks types as it goes
public final class Semantics extends LangParserBaseListener {
    private static final int CONTROL_LINK_SIZE = Integer.BYTES;

    private final StringBuilder rulePrints = new StringBuilder();
    private final List<String> errors = new ArrayList<>();
    private final Deque<Env> scopes = new ArrayDeque<>();
    private final Map<LangParser.BlockContext, Env> blockScopes = new IdentityHashMap<>();
    private final Map<LangParser.LocContext, Symbol> resolvedLocations = new IdentityHashMap<>();
    private final ParseTreeProperty<TypeSpec> types = new ParseTreeProperty<>();
    private Env env;
    private Env head;

    public Semantics(Env global) {
        env = global;
        head = global;
        global.initFrame(CONTROL_LINK_SIZE);
        scopes.push(global);
    }

    public String ruleTrace() { return rulePrints.toString(); }
    public Env head() { return head; }
    public List<String> errors() { return List.copyOf(errors); }
    public boolean hasErrors() { return !errors.isEmpty(); }
    public Env scopeOf(LangParser.BlockContext block) { return blockScopes.get(block); }
    public Symbol symbolOf(LangParser.LocContext loc) { return resolvedLocations.get(loc); }

    private void println(String text) {
        if (rulePrints.length() != 0) rulePrints.append('\n');
        rulePrints.append(text);
    }

    private void error(ParserRuleContext ctx, String message) {
        errors.add("Semantic error at " + ctx.getStart().getLine() + ":" +
                   ctx.getStart().getCharPositionInLine() + ": " + message);
    }

    @Override public void enterBlock(LangParser.BlockContext ctx) {
        Env parent = env;
        env = new Env(parent);
        head = env;
        env.initFrame(CONTROL_LINK_SIZE);
        scopes.push(env);
        blockScopes.put(ctx, env);
    }

    @Override public void exitBlock(LangParser.BlockContext ctx) {
        scopes.pop();
        env = scopes.peek();
    }

    @Override public void exitDecl(LangParser.DeclContext ctx) {
        String name = ctx.ID().getText();
        if (env.getLocal(name) != null) {
            error(ctx, "duplicate declaration of '" + name + "' in the same scope");
            return;
        }

        BasicType base = switch (ctx.BASIC().getText()) {
            case "int" -> BasicType.INT;
            case "float" -> BasicType.FLOAT;
            case "boolean" -> BasicType.BOOLEAN;
            default -> throw new IllegalStateException("unknown basic type");
        };
        List<Integer> dims = new ArrayList<>();
        if (ctx.dims() != null) {
            for (var token : ctx.dims().NUM()) {
                try {
                    int dimension = Integer.parseInt(token.getText());
                    if (dimension <= 0) {
                        error(ctx, "array dimensions must be positive");
                        return;
                    }
                    dims.add(dimension);
                } catch (NumberFormatException ex) {
                    error(ctx, "array dimension is too large: " + token.getText());
                    return;
                }
            }
        }

        TypeSpec type = new TypeSpec(base, dims);
        Symbol symbol = new Symbol(name);
        symbol.type = type;
        try {
            env.declare(symbol);
        } catch (IllegalArgumentException | ArithmeticException ex) {
            error(ctx, "invalid storage for '" + name + "': " + ex.getMessage());
        }
    }

    @Override public void exitLoc(LangParser.LocContext ctx) {
        Symbol symbol = env.get(ctx.ID().getText());
        if (symbol == null || symbol.type == null) {
            error(ctx, "undeclared identifier '" + ctx.ID().getText() + "'");
            return;
        }
        resolvedLocations.put(ctx, symbol);
        if (ctx.expr().isEmpty()) println("loc->id");

        if (ctx.expr().size() > symbol.type.dims.size()) {
            error(ctx, "too many indices for '" + symbol.name + "'");
            return;
        }
        for (var index : ctx.expr()) {
            TypeSpec indexType = types.get(index);
            if (indexType != null && !(indexType.isScalar() && indexType.base == BasicType.INT))
                error(index, "array index must have type int");
        }
        types.put(ctx, symbol.type.indexed(ctx.expr().size()));
    }

    @Override public void exitAtom(LangParser.AtomContext ctx) {
        TypeSpec type;
        if (ctx.NUM() != null) {
            try {
                Integer.parseInt(ctx.NUM().getText());
                type = TypeSpec.scalar(BasicType.INT);
            } catch (NumberFormatException ex) {
                error(ctx, "integer literal is outside the 32-bit signed range");
                type = null;
            }
        }
        else if (ctx.REAL() != null) {
            try {
                double value = Double.parseDouble(ctx.REAL().getText());
                if (!Double.isFinite(value)) throw new NumberFormatException();
                type = TypeSpec.scalar(BasicType.FLOAT);
            } catch (NumberFormatException ex) {
                error(ctx, "floating-point literal is outside the supported range");
                type = null;
            }
        }
        else if (ctx.TRUE() != null || ctx.FALSE() != null) type = TypeSpec.scalar(BasicType.BOOLEAN);
        else if (ctx.loc() != null) type = types.get(ctx.loc());
        else type = types.get(ctx.expr());
        if (type != null) types.put(ctx, type);
    }

    @Override public void exitMulExpr(LangParser.MulExprContext ctx) {
        TypeSpec result = types.get(ctx.atom(0));
        for (int i = 1; i < ctx.atom().size(); i++) {
            TypeSpec rhs = types.get(ctx.atom(i));
            if (result == null || rhs == null) { result = null; continue; }
            if (!result.isNumeric() || !rhs.isNumeric()) {
                error(ctx, "'*' and '/' require scalar numeric operands");
                result = null;
            } else if (result.base == BasicType.FLOAT || rhs.base == BasicType.FLOAT) {
                result = TypeSpec.scalar(BasicType.FLOAT);
            } else result = TypeSpec.scalar(BasicType.INT);
        }
        if (result != null) types.put(ctx, result);
    }

    @Override public void exitAddExpr(LangParser.AddExprContext ctx) {
        TypeSpec result = types.get(ctx.mulExpr(0));
        for (int i = 1; i < ctx.mulExpr().size(); i++) {
            TypeSpec rhs = types.get(ctx.mulExpr(i));
            if (result == null || rhs == null) { result = null; continue; }
            if (!result.isNumeric() || !rhs.isNumeric()) {
                error(ctx, "'+' and '-' require scalar numeric operands");
                result = null;
            } else if (result.base == BasicType.FLOAT || rhs.base == BasicType.FLOAT) {
                result = TypeSpec.scalar(BasicType.FLOAT);
            } else result = TypeSpec.scalar(BasicType.INT);
        }
        if (result != null) types.put(ctx, result);
    }

    @Override public void exitExpr(LangParser.ExprContext ctx) {
        TypeSpec type = types.get(ctx.addExpr());
        if (type != null) types.put(ctx, type);
    }

    @Override public void exitAssign(LangParser.AssignContext ctx) {
        TypeSpec lhs = types.get(ctx.loc());
        TypeSpec rhs = types.get(ctx.expr());
        if (lhs == null || rhs == null) return;
        if (!lhs.isScalar()) {
            error(ctx.loc(), "array value requires exactly " + lhs.dims.size() + " more index(es)");
            return;
        }
        if (!rhs.isScalar()) {
            error(ctx.expr(), "an array cannot be used as an assignment value");
            return;
        }
        if (!lhs.canAssignFrom(rhs))
            error(ctx, "cannot assign " + rhs + " to " + lhs);
    }

    @Override public void exitWhileStmt(LangParser.WhileStmtContext ctx) {
        TypeSpec condition = types.get(ctx.expr());
        if (condition != null && !condition.isScalar())
            error(ctx.expr(), "while condition must be a scalar value");
    }
}
