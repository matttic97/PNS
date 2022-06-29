package compiler.data.abstree;

import compiler.common.report.*;
import compiler.data.abstree.visitor.*;

public class AbsUntilStmt extends AbsStmt {

    public final AbsExpr cond;

    public final AbsStmts stmts;

    public AbsUntilStmt(Locatable location, AbsExpr cond, AbsStmts stmts) {
        super(location);
        this.cond = cond;
        this.stmts = stmts;
    }

    @Override
    public <Result, Arg> Result accept(AbsVisitor<Result, Arg> visitor, Arg accArg) {
        return visitor.visit(this, accArg);
    }

}
