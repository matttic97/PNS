package compiler.phases.imcgen;

import compiler.common.report.Report;
import compiler.data.abstree.*;
import compiler.data.abstree.visitor.AbsVisitor;
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

    private void getError(AbsStmt node, String errorMsg){
        throw new Report.Error(node, "[ImcGen] " + errorMsg);
    }

}