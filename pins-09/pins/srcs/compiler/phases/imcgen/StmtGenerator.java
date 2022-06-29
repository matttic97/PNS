package compiler.phases.imcgen;

import compiler.common.report.Report;
import compiler.data.abstree.*;
import compiler.data.abstree.visitor.AbsVisitor;
import compiler.data.dertree.DerLeaf;
import compiler.data.imcode.*;
import compiler.data.layout.Frame;
import compiler.data.layout.Label;
import compiler.phases.seman.SemAn;

import java.util.Stack;
import java.util.Vector;

/**
 * @author sliva
 */
public class StmtGenerator implements AbsVisitor<ImcStmt, Stack<Frame>> {

    @Override
    public ImcStmt visit(AbsAssignStmt assignStmt, Stack<Frame> visArg) {
        if(!(SemAn.isAddr.get(assignStmt.dst)))
            getError(assignStmt, "Assign destination must be an address.");
        ImcExpr dest = ImcGen.exprImCode.get(assignStmt.dst);
        ImcExpr src = ImcGen.exprImCode.get(assignStmt.src);
        ImcGen.stmtImCode.put(assignStmt, new ImcMOVE(dest, src));
        return null;
    }

    @Override
    public ImcStmt visit(AbsExprStmt exprStmt, Stack<Frame> visArg) {
        ImcGen.stmtImCode.put(exprStmt, new ImcESTMT(ImcGen.exprImCode.get(exprStmt.expr)));
        return null;
    }

    @Override
    public ImcStmt visit(AbsIfStmt ifStmt, Stack<Frame> visArg) {
        ImcExpr cond = ImcGen.exprImCode.get(ifStmt.cond);
        Label posLabel = new Label();
        Label negLabel = new Label();
        Vector<ImcStmt> stmts = new Vector<>();
        stmts.add(new ImcCJUMP(cond, posLabel, negLabel));
        stmts.add(new ImcLABEL(posLabel));
        for(AbsStmt stmt : ifStmt.thenStmts.stmts()){
            ImcStmt imStmt = ImcGen.stmtImCode.get(stmt);
            stmts.add(imStmt);
        }
        if(ifStmt.elseStmts.numStmts() != 0){
            Label newL = new Label();
            stmts.add(new ImcJUMP(newL));
            stmts.add(new ImcLABEL(negLabel));
            for(AbsStmt stmt : ifStmt.elseStmts.stmts()){
                ImcStmt imStmt = ImcGen.stmtImCode.get(stmt);
                stmts.add(imStmt);
            }
            stmts.add(new ImcLABEL(newL));
        } else {
            stmts.add(new ImcLABEL(negLabel));
            for(AbsStmt stmt : ifStmt.elseStmts.stmts()){
                ImcStmt imStmt = ImcGen.stmtImCode.get(stmt);
                stmts.add(imStmt);
            }
        }
        ImcGen.stmtImCode.put(ifStmt, new ImcSTMTS(stmts));
        return null;
    }

    @Override
    public ImcStmt visit(AbsWhileStmt whileStmt, Stack<Frame> visArg) {
        Vector<ImcStmt> stmts = new Vector<>();
        Label newL = new Label();
        stmts.add(new ImcLABEL(newL));
        Label posLabel = new Label();
        Label negLabel = new Label();

        ImcExpr cond = ImcGen.exprImCode.get(whileStmt.cond);
        stmts.add(new ImcCJUMP(cond, posLabel, negLabel));
        stmts.add(new ImcLABEL(posLabel));
        for(AbsStmt stmt : whileStmt.stmts.stmts()){
            ImcStmt imStmt = ImcGen.stmtImCode.get(stmt);
            stmts.add(imStmt);
        }
        stmts.add(new ImcJUMP(newL));
        stmts.add(new ImcLABEL(negLabel));
        ImcGen.stmtImCode.put(whileStmt, new ImcSTMTS(stmts));
        return null;
    }

    @Override
    public ImcStmt visit(AbsUntilStmt untilStmt, Stack<Frame> visArg) {
        Vector<ImcStmt> imcExprs = new Vector<>();

        ImcExpr cond = ImcGen.exprImCode.get(untilStmt.cond);


        Vector<ImcStmt> allStmtsThen = new Vector<>();
        for(AbsStmt stmt : untilStmt.stmts.stmts())
        {
            stmt.accept(this, visArg);
            allStmtsThen.add(ImcGen.stmtImCode.get(stmt));
        }
        ImcStmt thenStmts = new ImcSTMTS(allStmtsThen);  //ImcSTMTS / ImcESTMT

        Label firstLabel = new Label();
        imcExprs.add(new ImcLABEL(firstLabel));  // L0
        Label posLabel = new Label();
        imcExprs.add(new ImcLABEL(posLabel));  // L1

        imcExprs.add(thenStmts);
        imcExprs.add(new ImcJUMP(firstLabel));

        Label negLabel = new Label();
        imcExprs.add(new ImcLABEL(negLabel));   // 2

        imcExprs.insertElementAt(new ImcCJUMP(cond, negLabel, posLabel), 1);


        ImcGen.stmtImCode.put(untilStmt, new ImcSTMTS(imcExprs));

        return null;
    }

    private void getError(AbsStmt node, String errorMsg){
        throw new Report.Error(node, "[ImcGen] " + errorMsg);
    }

}