package compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import compiler.grammar.LangParser;
import compiler.grammar.LangParserBaseVisitor;
import compiler.grammar.LangParser.*;
import compiler.ir.*;

import org.antlr.v4.runtime.tree.ParseTree;

public final class CodeGen extends LangParserBaseVisitor<Address> {
    private Env env;
    private final Semantics semantics;
    private final IntermediateCode ic;
    private final TempFactory temps;
    private int nextLabel;

    public CodeGen(Semantics semantics, IntermediateCode ic) {
        this.semantics = Objects.requireNonNull(semantics, "semantics");
        this.env = semantics.head();
        this.ic = Objects.requireNonNull(ic, "intermediate code");
        this.temps = new TempFactory(this.env);
    }

    private void emit(Opcode op, Address a, Address b, Address c) {
        ic.emit(op, a, b, c);
    }

    private Address ensureFloat(Address a) {
        if (a.type().base == BasicType.FLOAT) return a;
        return Widen.to(a, TypeSpec.scalar(BasicType.FLOAT), ic, temps);
    }

    private Address ensureType(Address a, TypeSpec t) {
        TypeSpec at = a.type();
        if (at.equals(t)) return a;
        
        if (at.isScalar() && t.isScalar() &&
            at.base == BasicType.INT &&
            t.base  == BasicType.FLOAT) {
            return Widen.to(a, t, ic, temps);
        }

        // anything else that mismatches should've already been rejected by Semantics
        return a;
    }

    private TempAddr newTemp(TypeSpec t) {
        return new TempAddr(temps.newTemp(t));
    }

    private static int dimsSize(TypeSpec t) {
        return t.dims.size();
    }

    private Symbol symbol(LocContext loc) {
        Symbol symbol = semantics.symbolOf(loc);
        if (symbol == null) throw new IllegalStateException("Unresolved identifier: " + loc.ID().getText());
        return symbol;
    }

    // blocks and statements

    @Override
    public Address visitProgram(ProgramContext ctx) {
        return visitBlock(ctx.block());
    }

    @Override
    public Address visitBlock(BlockContext ctx) {
        Env previous = env;
        env = semantics.scopeOf(ctx);
        if (env == null) throw new IllegalStateException("No semantic scope for block");
        temps.setEnv(env);

        try {
            Address last = null;
            for (ParseTree child : ctx.children) {
                if (child instanceof StmtContext statement) {
                    last = visitStmt(statement);
                }
            }
            return last;
        } finally {
            env = previous;
            temps.setEnv(env);
        }
    }

    @Override
    public Address visitStmt(StmtContext ctx) {
        if (ctx.assign() != null) return visitAssign(ctx.assign());
        if (ctx.whileStmt() != null) return visitWhileStmt(ctx.whileStmt());
        if (ctx.block() != null) return visitBlock(ctx.block());
        return null;
    }

    @Override
    public Address visitWhileStmt(WhileStmtContext ctx) {
        LabelAddr Lstart = new LabelAddr("L" + nextLabel++);
        LabelAddr Lbody  = new LabelAddr("L" + nextLabel++);
        LabelAddr Lend   = new LabelAddr("L" + nextLabel++);

        emit(Opcode.LABEL, null, null, Lstart);
        Address cond = visitExpr(ctx.expr());
        emit(Opcode.IFGOTO, cond, null, Lbody);
        emit(Opcode.GOTO, null, null, Lend);
        emit(Opcode.LABEL, null, null, Lbody);
        visitStmt(ctx.stmt());
        emit(Opcode.GOTO, null, null, Lstart);
        emit(Opcode.LABEL, null, null, Lend);
        return null;
    }

    @Override
    public Address visitAssign(AssignContext ctx) {
        LValueRef lref = asLValue(ctx.loc());
        Address rval = visitExpr(ctx.expr());
        rval = ensureType(rval, lref.elemType);
        if (lref.addrOfElem != null) {
            // store value into computed address
            emit(Opcode.STORE, rval, null, lref.addrOfElem);
        } else {
            emit(Opcode.ASSIGN, rval, null, new VarAddr(lref.sym));
        }
        return null;
    }

    // lvalues and arrays

    private record LValueRef(Symbol sym, TypeSpec elemType, Address addrOfElem) {}

    private LValueRef asLValue(LocContext loc) {
        String name = loc.ID().getText();
        Symbol s = symbol(loc);
        if (s == null || s.type == null)
            throw new IllegalStateException("Undeclared identifier: " + name);

        if (loc.expr().isEmpty()) {
            if (dimsSize(s.type) != 0)
                throw new IllegalStateException("Cannot assign array variable; assign to an element");
            return new LValueRef(s, s.type, null);
        }

        if (dimsSize(s.type) == 0)
            throw new IllegalStateException("Indexing a scalar variable: " + name);

        List<Integer> dims = s.type.dims;
        int k = loc.expr().size();
        if (k > dims.size())
            throw new IllegalStateException("Too many indices for " + name);

        List<Address> idxAddrs = new ArrayList<>();
        for (ExprContext e : loc.expr()) {
            Address a = visitExpr(e);
            a = ensureType(a, TypeSpec.scalar(BasicType.INT));
            idxAddrs.add(a);
        }

        Address linear = null;
        for (int pos = 0; pos < k; pos++) {
            int mult = 1;
            for (int j = pos + 1; j < dims.size(); j++) mult *= dims.get(j);

            Address term = idxAddrs.get(pos);
            if (mult != 1) {
                Address c = ConstAddr.ofInt(mult);
                var tmp = newTemp(TypeSpec.scalar(BasicType.INT));
                emit(Opcode.MUL, term, c, tmp);
                term = tmp;
            }

            if (linear == null) {
                linear = term;
            } else {
                var tmp = newTemp(TypeSpec.scalar(BasicType.INT));
                emit(Opcode.ADD, linear, term, tmp);
                linear = tmp;
            }
        }

        int elemSize = SizeOf.of(TypeSpec.scalar(s.type.base));
        if (elemSize != 1) {
            Address c = ConstAddr.ofInt(elemSize);
            var tmp = newTemp(TypeSpec.scalar(BasicType.INT));
            emit(Opcode.MUL, linear, c, tmp);
            linear = tmp;
        }

        if (s.address < 0) {
            throw new IllegalStateException("Array symbol has no address: " + s.name);
        }
        Address baseAddr = ConstAddr.ofInt((int) s.address);

        var addr = new TempAddr(temps.newTemp(TypeSpec.scalar(s.type.base)));
        emit(Opcode.INDEX, baseAddr, linear, addr);
        TypeSpec elemT = TypeSpec.scalar(s.type.base);
        return new LValueRef(s, elemT, addr);
    }

    // expressions

    @Override
    public Address visitExpr(ExprContext ctx) {
        return visitAddExpr(ctx.addExpr());
    }

    @Override
    public Address visitAddExpr(AddExprContext ctx) {
        Address acc = visitMulExpr(ctx.mulExpr(0));
        for (int i = 1; i < ctx.mulExpr().size(); i++) {
            Address rhs = visitMulExpr(ctx.mulExpr(i));
            boolean needFloat =
                acc.type().base == BasicType.FLOAT || rhs.type().base == BasicType.FLOAT;
            if (needFloat) {
                acc = ensureFloat(acc);
                rhs = ensureFloat(rhs);
            }
            var dst = newTemp(acc.type());
            String op = ctx.getChild(2 * i - 1).getText();
            switch (op) {
                case "+":
                    emit(Opcode.ADD, acc, rhs, dst);
                    break;
                case "-":
                    emit(Opcode.SUB, acc, rhs, dst);
                    break;
                default:
                    throw new IllegalStateException("Unknown add op: " + op);
            }
            acc = dst;
        }
        return acc;
    }

    @Override
    public Address visitMulExpr(MulExprContext ctx) {
        Address acc = visitAtom(ctx.atom(0));
        for (int i = 1; i < ctx.atom().size(); i++) {
            Address rhs = visitAtom(ctx.atom(i));
            boolean needFloat =
                acc.type().base == BasicType.FLOAT || rhs.type().base == BasicType.FLOAT;
            if (needFloat) {
                acc = ensureFloat(acc);
                rhs = ensureFloat(rhs);
            }
            var dst = newTemp(acc.type());
            String op = ctx.getChild(2 * i - 1).getText();
            switch (op) {
                case "*":
                    emit(Opcode.MUL, acc, rhs, dst);
                    break;
                case "/":
                    emit(Opcode.DIV, acc, rhs, dst);
                    break;
                default:
                    throw new IllegalStateException("Unknown mul op: " + op);
            }
            acc = dst;
        }
        return acc;
    }

    @Override
    public Address visitAtom(AtomContext ctx) {
        if (ctx.NUM() != null)
            return ConstAddr.ofInt(Integer.parseInt(ctx.NUM().getText()));
        if (ctx.REAL() != null)
            return ConstAddr.ofFloat(Double.parseDouble(ctx.REAL().getText()));
        if (ctx.TRUE() != null)
            return ConstAddr.ofBool(true);
        if (ctx.FALSE() != null)
            return ConstAddr.ofBool(false);

        if (ctx.loc() != null) {
            LocContext lc = ctx.loc();
            if (lc.expr().isEmpty()) {
                return new VarAddr(symbol(lc));
            } else {
                LValueRef lv = asLValue(lc);
                var tmp = newTemp(lv.elemType);
                emit(Opcode.LOAD, lv.addrOfElem, null, tmp);
                return tmp;
            }
        }

        if (ctx.expr() != null)
            return visitExpr(ctx.expr());

        throw new IllegalStateException("Unrecognized atom");
    }
}
