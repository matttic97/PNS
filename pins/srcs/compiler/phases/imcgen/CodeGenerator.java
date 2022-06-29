package compiler.phases.imcgen;

import compiler.common.report.Report;
import compiler.data.abstree.*;
import compiler.data.abstree.visitor.AbsFullVisitor;
import compiler.data.imcode.ImcExpr;
import compiler.data.layout.*;
import compiler.data.type.SemCharType;
import compiler.data.type.SemType;
import compiler.phases.frames.Frames;
import compiler.data.imcode.*;
import compiler.phases.seman.SemAn;

import java.util.*;

/**
 * Intermediate code generator.
 * 
 * @author sliva
 */
public class CodeGenerator extends AbsFullVisitor<Object, Stack<Frame>> {

    ExprGenerator exprGenerator = new ExprGenerator();
    StmtGenerator stmtGenerator = new StmtGenerator();

    @Override
    public Object visit(AbsSource source, Stack<Frame> visArg) {
        Stack<Frame> stack = new Stack<>();
        return super.visit(source, stack);
    }

    @Override
    public Object visit(AbsFunDef funDef, Stack<Frame> visArg) {
        visArg.push(Frames.frames.get(funDef));
        funDef.value.accept(this, visArg);
        super.visit(funDef, visArg);
        visArg.pop();
        return null;
    }

    /** EXPRESSIONS **/
    @Override
    public Object visit(AbsAtomExpr atomExpr, Stack<Frame> visArg) {
        return exprGenerator.visit(atomExpr, visArg);
    }

    @Override
    public Object visit(AbsVarName varName, Stack<Frame> visArg) {
        return exprGenerator.visit(varName, visArg);
    }

    @Override
    public Object visit(AbsBinExpr binExpr, Stack<Frame> visArg) {
        binExpr.fstExpr.accept(this, visArg);
        binExpr.sndExpr.accept(this, visArg);
        return exprGenerator.visit(binExpr, visArg);
    }

    @Override
    public Object visit(AbsUnExpr unExpr, Stack<Frame> visArg) {
        unExpr.subExpr.accept(this, visArg);
        return exprGenerator.visit(unExpr, visArg);
    }

    @Override
    public Object visit(AbsArrExpr arrExpr, Stack<Frame> visArg) {
        arrExpr.array.accept(this, visArg);
        arrExpr.index.accept(this, visArg);
        return exprGenerator.visit(arrExpr, visArg);
    }

    @Override
    public Object visit(AbsNewExpr newExpr, Stack<Frame> visArg) {
        return exprGenerator.visit(newExpr, visArg);
    }

    @Override
    public Object visit(AbsDelExpr delExpr, Stack<Frame> visArg) {
        delExpr.expr.accept(this, visArg);
        return exprGenerator.visit(delExpr, visArg);
    }

    @Override
    public Object visit(AbsFunName funName, Stack<Frame> visArg) {
        funName.args.accept(this, visArg);
        return exprGenerator.visit(funName, visArg);
    }

    @Override
    public Object visit(AbsCastExpr castExpr, Stack<Frame> visArg) {
        castExpr.expr.accept(this, visArg);
        return exprGenerator.visit(castExpr, visArg);
    }

    @Override
    public Object visit(AbsBlockExpr blockExpr, Stack<Frame> visArg){
        blockExpr.decls.accept(this, visArg);
        ImcStmt stmts = (ImcStmt) blockExpr.stmts.accept(this, visArg);
        blockExpr.expr.accept(this, visArg);
        ImcExpr expr = ImcGen.exprImCode.get(blockExpr.expr);
        ImcGen.exprImCode.put(blockExpr, new ImcSEXPR(stmts, expr));
        return null;
    }

    /** STATMENTS **/
    @Override
    public Object visit(AbsExprStmt exprStmt, Stack<Frame> visArg){
        exprStmt.expr.accept(this, visArg);
        return stmtGenerator.visit(exprStmt, visArg);
    }

    @Override
    public Object visit(AbsAssignStmt assignStmt, Stack<Frame> visArg) {
        assignStmt.dst.accept(this, visArg);
        assignStmt.src.accept(this, visArg);
        return stmtGenerator.visit(assignStmt, visArg);
    }

    @Override
    public Object visit(AbsStmts stmts, Stack<Frame> visArg) {
        Vector<ImcStmt> allStmts = new Vector<>();
        for(AbsStmt stmt : stmts.stmts()){
            stmt.accept(this, visArg);
            allStmts.add(ImcGen.stmtImCode.get(stmt));
        }
        return new ImcSTMTS(allStmts);
    }

    @Override
    public Object visit(AbsIfStmt ifStmt, Stack<Frame> visArg) {
        ifStmt.cond.accept(this, visArg);
        ifStmt.elseStmts.accept(this, visArg);
        ifStmt.thenStmts.accept(this, visArg);
        return stmtGenerator.visit(ifStmt, visArg);
    }

    @Override
    public Object visit(AbsWhileStmt whileStmt, Stack<Frame> visArg) {
        whileStmt.stmts.accept(this, visArg);
        whileStmt.cond.accept(this, visArg);
        return stmtGenerator.visit(whileStmt, visArg);
    }
}
