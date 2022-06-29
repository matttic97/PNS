/**
 * @author sliva
 */
package compiler.phases.seman;

import compiler.common.report.*;
import compiler.data.abstree.*;
import compiler.data.abstree.visitor.*;

/**
 * Name resolving: the result is stored in {@link SemAn#declaredAt}.
 *
 * @author sliva
 */
public class NameResolver extends AbsFullVisitor<Object, Object> {

	/** Symbol table. */
	private final SymbTable symbTable = new SymbTable();

	// TODO
	@Override
	public Object visit(AbsSource source, Object visArg) {
		symbTable.newScope();
		source.decls.accept(this, visArg);
		symbTable.oldScope();

		return null;
	}

	public Object visit(AbsDecls decls, Object visArg) {
		for (AbsDecl decl : decls.decls()) {
			insert(decl.name, decl);
		}
		for (AbsDecl decl : decls.decls()) {
			symbTable.newScope();
			decl.accept(this, visArg);
			symbTable.oldScope();
		}
		return null;
	}

	public Object visit(AbsParDecls decls, Object visArg) {
		for (AbsDecl decl : decls.parDecls()) {
			decl.accept(this, visArg);
		}
		symbTable.newScope();
		for (AbsDecl decl : decls.parDecls()) {
			insert(decl.name, decl);
		}
		return null;
	}

	@Override
	public Object visit(AbsVarName varName, Object visArg) {
		AbsDecl declLocation = find(varName.name, varName);
		SemAn.declaredAt.put(varName, declLocation);
		return null;
	}

	@Override
	public Object visit(AbsTypName typName, Object visArg) {
		AbsDecl declLocation = find(typName.name, typName);
		SemAn.declaredAt.put(typName, declLocation);
		return null;
	}

	@Override
	public Object visit(AbsFunName funName, Object visArg) {
		AbsDecl declLocation = find(funName.name, funName);
		SemAn.declaredAt.put(funName, declLocation);
		funName.args.accept(this, visArg);
		return null;
	}

	@Override
	public Object visit(AbsFunDef decl, Object visArg){
		insert(decl.name, decl);
		decl.type.accept(this, visArg);
		symbTable.newScope();
		decl.parDecls.accept(this, visArg);
		decl.value.accept(this, visArg);
		symbTable.oldScope();
		symbTable.oldScope();
		return null;
	}

	@Override
	public Object visit(AbsBlockExpr expr, Object visArg){
		symbTable.newScope();
		expr.decls.accept(this, visArg);
		expr.stmts.accept(this, visArg);
		expr.expr.accept(this, visArg);
		symbTable.oldScope();
		return null;
	}

	private void insert(String name, AbsDecl decl){
		try{
			symbTable.ins(name, decl);
		}
		catch (SymbTable.CannotInsNameException e){
			throw new Report.Error(decl, "[ SemAn ] Only one identifier with the same name allowed per scope.");
		}
	}

	private AbsDecl find(String name, AbsTree node){
		try {
			return symbTable.fnd(name);
		}
		catch (SymbTable.CannotFndNameException e){
			throw new Report.Error(node, "[ SemAn ] Identifier with this name could not be found in this scope or higher..");
		}
	}
}
