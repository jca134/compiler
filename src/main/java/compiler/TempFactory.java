package compiler;

public final class TempFactory {
    private int next = 0;

    private Env env;

    public TempFactory(Env env) {
        if (env == null) {
            throw new IllegalArgumentException("env is null");
        }
        this.env = env;
    }

    public void setEnv(Env env) {
        if (env == null) {
            throw new IllegalArgumentException("env is null");
        }
        this.env = env;
    }

    public Symbol newTemp(TypeSpec t) {
        String n;
        // keep counting up past any name a user already declared, e.g. a real "t0"
        do {
            n = "t" + (next++);
        } while (env.get(n) != null);
        Symbol s = new Symbol(n);
        s.type = t;
        env.allocateTemporary(s);
        return s;
    }
}
