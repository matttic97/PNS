package compiler.phases.imcgen;

import compiler.common.report.Report;
import compiler.data.abstree.*;
import compiler.data.abstree.visitor.AbsVisitor;
import compiler.data.imcode.*;
import compiler.data.layout.*;
import compiler.data.type.SemCharType;
import compiler.data.type.SemType;
import compiler.phases.frames.Frames;
import compiler.phases.seman.SemAn;

import java.util.Stack;
import java.util.Vector;

/**
 * @author sliva
 */
public class ExprGenerator implements AbsVisitor<ImcExpr, Stack<Frame>> {

    @Override
    public ImcExpr visit(AbsAtomExpr atomExpr, Stack<Frame> visArg) {
        switch (atomExpr.type){
            case INT: {
                ImcExpr expr = new ImcCONST(Integer.parseInt(atomExpr.expr));
                ImcGen.exprImCode.put(atomExpr, expr);
                break;
            }
            case BOOL: {
                ImcExpr expr;
                if(atomExpr.expr.equals("true"))
                    expr = new ImcCONST(1);
                else
                    expr = new ImcCONST(0);
                ImcGen.exprImCode.put(atomExpr, expr);
                break;
            }
            case CHAR: {
                ImcExpr expr = new ImcCONST(atomExpr.expr.charAt(1));
                ImcGen.exprImCode.put(atomExpr, expr);
                break;
            }
            case PTR:
            case VOID: {
                ImcExpr expr = new ImcCONST(0);
                ImcGen.exprImCode.put(atomExpr, expr);
                break;
            }
        }
        return null;
    }

    @Override
    public ImcExpr visit(AbsVarName varName, Stack<Frame> visArg) {
        Access varAcces = Frames.accesses.get((AbsVarDecl) SemAn.declaredAt.get(varName));
        if (varAcces instanceof AbsAccess){
            ImcExpr address = new ImcNAME(((AbsAccess) varAcces).label);
            ImcGen.exprImCode.put(varName, new ImcMEM(address));
            return null;
        } else {
            RelAccess access = (RelAccess) varAcces;
            ImcExpr addr = new ImcTEMP(visArg.peek().FP);
            for (int i=access.depth; i<visArg.peek().depth; i++){
                addr = new ImcMEM(addr);
            }
            addr = new ImcBINOP(ImcBINOP.Oper.ADD, addr, new ImcCONST(access.offset));
            ImcGen.exprImCode.put(varName, new ImcMEM(addr));
            return null;
        }
    }

    @Override
    public ImcExpr visit(AbsBinExpr binExpr, Stack<Frame> visArg) {
        ImcExpr first = ImcGen.exprImCode.get(binExpr.fstExpr);
        ImcExpr second = ImcGen.exprImCode.get(binExpr.sndExpr);
        ImcBINOP.Oper oper = null;
        switch (binExpr.oper){
            case ADD: {
                oper = ImcBINOP.Oper.ADD;
                break;
            }
            case SUB: {
                oper = ImcBINOP.Oper.SUB;
                break;
            }
            case NEQ: {
                oper = ImcBINOP.Oper.NEQ;
                break;
            }
            case LTH: {
                oper = ImcBINOP.Oper.LTH;
                break;
            }
            case LEQ: {
                oper = ImcBINOP.Oper.LEQ;
                break;
            }
            case GTH: {
                oper = ImcBINOP.Oper.GTH;
                break;
            }
            case GEQ: {
                oper = ImcBINOP.Oper.GEQ;
                break;
            }
            case EQU: {
                oper = ImcBINOP.Oper.EQU;
                break;
            }
            case MOD: {
                oper = ImcBINOP.Oper.MOD;
                break;
            }
            case MUL: {
                oper = ImcBINOP.Oper.MUL;
                break;
            }
            case DIV: {
                oper = ImcBINOP.Oper.DIV;
                break;
            }
        }
        ImcBINOP binop = new ImcBINOP(oper, first, second);
        ImcGen.exprImCode.put(binExpr, binop);
        return null;
    }

    @Override
    public ImcExpr visit(AbsUnExpr unExpr, Stack<Frame> visArg) {
        unExpr.subExpr.accept(this, visArg);
        ImcExpr expr = ImcGen.exprImCode.get(unExpr.subExpr);
        switch (unExpr.oper) {
            case ADD:
                ImcGen.exprImCode.put(unExpr, expr);
                break;
            case SUB:
                ImcGen.exprImCode.put(unExpr, new ImcUNOP(ImcUNOP.Oper.NEG, expr));
                break;
            case NOT:
                ImcGen.exprImCode.put(unExpr, new ImcUNOP(ImcUNOP.Oper.NOT, expr));
                break;
            case ADDR:
                ImcGen.exprImCode.put(unExpr, expr);
                break;
            case DATA:
                ImcGen.exprImCode.put(unExpr, new ImcMEM(expr));
                break;
            default:
                break;
        }
        return null;
    }

    @Override
    public ImcExpr visit(AbsArrExpr arrExpr, Stack<Frame> visArg) {
        //if(!(SemAn.isAddr.get(arrExpr)))
          //  getError(arrExpr, "Array expression must have address on the right side.");

        ImcExpr array = ((ImcMEM)(ImcGen.exprImCode.get(arrExpr.array))).addr;
        ImcExpr index = ImcGen.exprImCode.get(arrExpr.index);
        ImcCONST arrSize = new ImcCONST(SemAn.isOfType.get(arrExpr).size());
        ImcBINOP indexValue = new ImcBINOP(ImcBINOP.Oper.MUL, index, arrSize);
        ImcMEM arrayAdr = new ImcMEM(new ImcBINOP(ImcBINOP.Oper.ADD, array, indexValue));
        ImcGen.exprImCode.put(arrExpr, arrayAdr);
        return null;
    }

    @Override
    public ImcExpr visit(AbsNewExpr newExpr, Stack<Frame> visArg) {
        Vector<ImcExpr> imcExprs = new Vector<>();
        imcExprs.add(new ImcCONST(0));
        imcExprs.add(new ImcCONST(SemAn.isOfType.get(newExpr).size()));
        ImcCALL call = new ImcCALL(new Label("new"), imcExprs);
        ImcGen.exprImCode.put(newExpr, call);
        return null;
    }

    @Override
    public ImcExpr visit(AbsDelExpr delExpr, Stack<Frame> visArg) {
        Vector<ImcExpr> imcExprs = new Vector<>();
        imcExprs.add(new ImcCONST(0));
        imcExprs.add(ImcGen.exprImCode.get(delExpr.expr));
        ImcCALL call = new ImcCALL(new Label("del"), imcExprs);
        ImcGen.exprImCode.put(delExpr, call);
        return null;
    }

    @Override
    public ImcExpr visit(AbsCastExpr castExpr, Stack<Frame> visArg){
        SemType type = SemAn.isType.get(castExpr.type).actualType();
        ImcExpr expr = ImcGen.exprImCode.get(castExpr.expr);
        if (type instanceof SemCharType){
            ImcGen.exprImCode.put(castExpr, new ImcBINOP(ImcBINOP.Oper.MOD, expr, new ImcCONST(256)));
        } else {
            ImcGen.exprImCode.put(castExpr, expr);
        }
        return null;
    }

    @Override
    public ImcExpr visit(AbsFunName funName, Stack<Frame> visArg) {
        Vector<ImcExpr> imcExprs = new Vector<>();
        Frame frame = Frames.frames.get((AbsFunDecl)SemAn.declaredAt.get(funName));
        if(frame == null || frame.depth == 1)
            imcExprs.add(new ImcCONST(0));
        else{
            ImcExpr expr = new ImcTEMP(visArg.peek().FP);
            for(int i=frame.depth; i<visArg.peek().depth; i++){
                expr = new ImcMEM(expr);
            }
            imcExprs.add(expr);
        }
        for(AbsExpr arg : funName.args.args()){
            imcExprs.add(ImcGen.exprImCode.get(arg));
        }
        ImcGen.exprImCode.put(funName, new ImcCALL(frame.label, imcExprs));

        return null;
    }

    private void getError(AbsExpr node, String errorMsg){
        throw new Report.Error(node, "[ImcGen] " + errorMsg);
    }
}
