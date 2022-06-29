/**
 * @author sliva 
 */
package compiler.phases.seman;

import compiler.common.report.*;
import compiler.data.abstree.*;
import compiler.data.abstree.visitor.*;
import compiler.data.type.*;
import compiler.data.type.property.*;

import java.util.concurrent.Semaphore;

/**
 * Type resolving: the result is stored in {@link SemAn#declaresType},
 * {@link SemAn#isType}, and {@link SemAn#isOfType}.
 * 
 * @author sliva
 */
public class TypeResolver extends AbsFullVisitor<SemType, TypeResolver.Phase> {

	/**
	 * Different phases of type resolving.
	 * 
	 * @author sliva
	 */
	public enum Phase{
		AddNamedTypes, ResolveTyp, MapTypes, CheckTypes
	}

	@Override
	public SemType visit(AbsSource source, TypeResolver.Phase visArg) {
		super.visit(source, Phase.AddNamedTypes);
		super.visit(source, Phase.ResolveTyp);
		super.visit(source, Phase.MapTypes);
		super.visit(source, Phase.CheckTypes);
		return null;
	}

	@Override
	public SemType visit(AbsTypDecl decl, TypeResolver.Phase visArg) {
		if (visArg == Phase.AddNamedTypes) {
			SemAn.declaresType.put(decl, new SemNamedType(decl.name));
		} else if (visArg == Phase.ResolveTyp) {
			SemType type = decl.type.accept(this, visArg);
			SemNamedType named = SemAn.declaresType.get(decl);
			named.define(type);
		} else if (visArg == Phase.MapTypes) {
			SemType type = decl.type.accept(this, visArg);
			if (type.isInfinite()){
				throw getError(decl, "Declaration is infinite.");
			}
		}
		return null;
	}

	@Override
	public SemType visit(AbsVarDecl decl, TypeResolver.Phase visArg) {
		if (visArg == Phase.ResolveTyp) {
			decl.type.accept(this, visArg);
		}
		if (visArg == Phase.MapTypes) {
			SemType type = decl.type.accept(this, visArg).actualType();
			if (type instanceof SemVoidType) {
				throw getError(decl, "A variable cannot be of type VOID.");
			}
		}
		return null;
	}

	@Override
	public SemType visit(AbsFunDecl decl, TypeResolver.Phase visArg) {
		if (visArg == Phase.ResolveTyp) {
			decl.parDecls.accept(this, visArg);
			decl.type.accept(this, visArg);
		} else if (visArg == Phase.MapTypes) {
			SemType returnType = decl.type.accept(this, visArg).actualType();
			if (!(returnType instanceof SemVoidType || returnType instanceof SemIntType
					|| returnType instanceof SemCharType || returnType instanceof SemBoolType
					|| returnType instanceof SemPtrType)) {
				throw getError(decl, "Only types INT,BOOL,VOID,CHAR and PTR are allowed as return types.");
			}
			decl.parDecls.accept(this, visArg);
		}
		return null;
	}

	@Override
	public SemType visit(AbsFunDef decl, TypeResolver.Phase visArg) {
		if (visArg == Phase.AddNamedTypes || visArg == Phase.ResolveTyp) {
			decl.type.accept(this, visArg);
			decl.parDecls.accept(this, visArg);
			decl.value.accept(this, visArg);
		} else if (visArg == Phase.MapTypes) {
			SemType returnType = decl.type.accept(this, visArg).actualType();
			if (!(returnType instanceof FunResultType)) {
				throw getError(decl, "Only types INT,BOOL,VOID,CHAR and PTR are allowed as return types.");
			}
			decl.parDecls.accept(this, visArg);
			decl.value.accept(this, visArg);
		} else if (visArg == Phase.CheckTypes) {
			SemType returnType = decl.value.accept(this, visArg).actualType();
			SemType type = SemAn.isType.get(decl.type).actualType();
			if (!returnType.matches(type)) {
				throw getError(decl, "Returned type and actual return type don't match.");
			}
		}
		return null;
	}

	@Override
	public SemType visit(AbsParDecl parDecl, TypeResolver.Phase visArg) {
		if (visArg == Phase.MapTypes || visArg == Phase.ResolveTyp) {
			parDecl.type.accept(this, visArg);
		} else if (visArg == Phase.CheckTypes) {
			return SemAn.isType.get(parDecl.type);
		}
		return null;
	}

	@Override
	public SemType visit(AbsAtomType atom, TypeResolver.Phase visArg) {
		if (visArg == Phase.ResolveTyp || visArg == Phase.MapTypes) {
			SemType type;
			switch (atom.type) {
				case VOID: {
					type = new SemVoidType();
					break;
				}
				case BOOL: {
					type = new SemBoolType();
					break;
				}
				case CHAR: {
					type = new SemCharType();
					break;
				}
				case INT: {
					type = new SemIntType();
					break;
				}
				default: {
					throw getError(atom, "Unknown atom type.");
				}
			}
			if (SemAn.isType.get(atom) == null) {
				SemAn.isType.put(atom, type);
			}
			return type;
		}
		return null;
	}

	@Override
	public SemType visit(AbsArrType arr, TypeResolver.Phase visArg) {
		if (visArg == Phase.ResolveTyp) {
			long length = -1;
			if (arr.len instanceof AbsAtomExpr && ((AbsAtomExpr) arr.len).type == AbsAtomExpr.Type.INT) {
				try {
					length = Long.parseLong(((AbsAtomExpr) arr.len).expr);
				} catch (Exception e) {
					throw getError(arr, "Problem parsing array length to a long.");
				}
			} else {
				throw getError(arr, "Array lenght should an integer constant.");
			}
			if (length < 1) {
				throw getError(arr, "Array length should be a positive integer.");
			}
			SemAn.isOfType.put(arr.len, new SemIntType());
			SemType type = arr.elemType.accept(this, visArg);

			SemType array = new SemArrType(length, type);
			SemAn.isType.put(arr, array);
			return array;
		} else if (visArg == Phase.MapTypes) {
			SemType type = arr.elemType.accept(this, visArg);
			if (type.actualType() instanceof SemVoidType) {
				throw getError(arr, "Array cannot be of type VOID.");
			}
			return SemAn.isType.get(arr);
		}
		return null;
	}

	@Override
	public SemType visit(AbsPtrType ptr, TypeResolver.Phase visArg) {
		if (visArg == Phase.ResolveTyp) {
			SemType typ = ptr.ptdType.accept(this, visArg);
			SemType type = new SemPtrType(typ);
			SemAn.isType.put(ptr, type);
			return type;
		} else if (visArg == Phase.MapTypes) {
			ptr.ptdType.accept(this, visArg);
			return SemAn.isType.get(ptr);
		}
		return null;
	}

	@Override
	public SemType visit(AbsAtomExpr expr, TypeResolver.Phase visArg) {
		if (visArg == Phase.CheckTypes) {
			switch (expr.type) {
				case VOID: {
					SemType type = new SemVoidType();
					SemAn.isOfType.put(expr, type);
					return type;
				}
				case PTR: {
					SemType type = new SemPtrType(new SemVoidType());
					SemAn.isOfType.put(expr, type);
					return type;
				}
				case BOOL: {
					SemType type = new SemBoolType();
					SemAn.isOfType.put(expr, type);
					return type;
				}
				case CHAR: {
					SemType type = new SemCharType();
					SemAn.isOfType.put(expr, type);
					return type;
				}
				case INT: {
					SemType type = new SemIntType();
					SemAn.isOfType.put(expr, type);
					return type;
				}
				default: {
					throw getError(expr, "Unrecognizable atom expression.");
				}
			}
		}
		return null;
	}

	@Override
	public SemType visit(AbsUnExpr unExpr, TypeResolver.Phase visArg) {
		if (visArg == Phase.AddNamedTypes || visArg == Phase.ResolveTyp || visArg == Phase.MapTypes) {
			unExpr.subExpr.accept(this, visArg);
		} else if (visArg == Phase.CheckTypes) {
			SemType subExpr = unExpr.subExpr.accept(this, visArg).actualType();
			switch (unExpr.oper){
				case ADD:
				case SUB: {
					if(subExpr instanceof SemIntType){
						SemType type = new SemIntType();
						SemAn.isOfType.put(unExpr, type);
						return type;
					}
					else
						throw getError(unExpr, "UnExpr must be INT.");
				}
				case ADDR: {
					SemType type = new SemPtrType(subExpr);
					SemAn.isOfType.put(unExpr, type);
					return type;
				}
				case DATA: {
					if(subExpr.actualType() instanceof SemPtrType){
						SemType type = ((SemPtrType) subExpr).ptdType;
						SemAn.isOfType.put(unExpr, type);
						return type;
					}
					else
						getError(unExpr, "UnExpr must be a PTR.");
				}
				default: throw getError(unExpr, "Unknown unary operator.");
			}
		}
		return null;
	}

	@Override
	public SemType visit(AbsBinExpr expr, TypeResolver.Phase visArg) {
		if (visArg == Phase.AddNamedTypes || visArg == Phase.ResolveTyp || visArg == Phase.MapTypes) {
			expr.fstExpr.accept(this, visArg);
			expr.sndExpr.accept(this, visArg);
		} else if (visArg == Phase.CheckTypes) {
			SemType typeFst = expr.fstExpr.accept(this, visArg).actualType();
			SemType typeScd = expr.sndExpr.accept(this, visArg).actualType();
			switch (expr.oper) {
				case ADD:
				case SUB:
				case MUL:
				case DIV:
				case MOD: {
					if (typeFst.matches(typeScd)) {
						if (typeFst instanceof SemIntType || typeFst instanceof SemCharType) {
							SemType type = new SemIntType();
							SemAn.isOfType.put(expr, type);
							return type;
						} else {
							throw getError(expr, "Type of expression should be INT or CHAR.");
						}
					} else {
						throw getError(expr, "Type of expression should match.");
					}
				}
				case EQU:
				case NEQ: {
					if (typeFst.matches(typeScd)) {
						if ((typeFst instanceof SemBoolType || typeFst instanceof SemCharType
								|| typeFst instanceof SemIntType || typeScd instanceof SemPtrType)) {
							SemType type = new SemBoolType();
							SemAn.isOfType.put(expr, type);
							return type;
						} else {
							throw getError(expr, "Type of expression should be BOOL, INT, CHAR OR PTR.");
						}
					} else {
						throw getError(expr, "Types of expressions should match.");
					}
				}
				case LEQ:
				case GEQ:
				case LTH:
				case GTH: {
					if (typeFst.matches(typeScd)) {
						if (typeFst instanceof SemCharType || typeFst instanceof SemIntType
								|| typeFst instanceof SemPtrType) {
							SemType type = new SemBoolType();
							SemAn.isOfType.put(expr, type);
							return type;
						} else {
							throw getError(expr, "Type of expression should be INT, CHAR OR PTR.");
						}
					} else {
						throw getError(expr, "Type of expression should match.");
					}
				}
				default: {
					throw getError(expr, "Invalid binary operator");
				}
			}
		}
		return null;
	}

	@Override
	public SemType visit(AbsNewExpr expr, TypeResolver.Phase visArg) {
		if (visArg == Phase.ResolveTyp) {
			expr.type.accept(this, visArg);
		} else if (visArg == Phase.MapTypes) {
			SemType type = expr.type.accept(this, visArg);
			if (type.actualType() instanceof SemVoidType) {
				throw getError(expr, "New expression cannot be of type VOID.");
			}
			SemType exprType = new SemPtrType(type);
			SemAn.isOfType.put(expr, exprType);
			return exprType;
		} else if (visArg == Phase.CheckTypes) {
			return SemAn.isOfType.get(expr);
		}
		return null;
	}

	@Override
	public SemType visit(AbsDelExpr expr, TypeResolver.Phase visArg) {
		if (visArg == Phase.ResolveTyp || visArg == Phase.MapTypes) {
			expr.expr.accept(this, visArg);
		} else if (visArg == Phase.CheckTypes) {
			SemType type = expr.expr.accept(this, visArg);
			if (type.actualType() instanceof SemVoidType) {
				throw getError(expr, "Del expression cannot be of type VOID.");
			}
			SemAn.isOfType.put(expr, type);
			return type;
		}
		return null;
	}

	@Override
	public SemType visit(AbsArrExpr arr, TypeResolver.Phase visArg) {
		if (visArg == Phase.AddNamedTypes || visArg == Phase.ResolveTyp || visArg == Phase.MapTypes) {
			arr.array.accept(this, visArg);
			arr.index.accept(this, visArg);
		} else if (visArg == Phase.CheckTypes) {
			SemType arrType = arr.array.accept(this, visArg).actualType();
			SemType indexType = arr.index.accept(this, visArg).actualType();
			if (!(arrType instanceof SemArrType)) {
				throw getError(arr, "Type of left side should be ARR.");
			}
			if (!(indexType instanceof SemIntType)) {
				throw getError(arr, "Index should be of type INT.");
			}
			SemType arrayType = ((SemArrType) arrType).elemType;
			SemAn.isOfType.put(arr, arrayType);
			return arrayType;
		}
		return null;
	}

	@Override
	public SemType visit(AbsFunName fun, TypeResolver.Phase visArg) {
		if (visArg == Phase.AddNamedTypes || visArg == Phase.ResolveTyp || visArg == Phase.MapTypes) {
			for (AbsExpr expr : fun.args.args()) {
				expr.accept(this, visArg);
			}
		} else if (visArg == Phase.CheckTypes) {
			AbsDecl decl = SemAn.declaredAt.get(fun);
			if (decl instanceof AbsFunDecl) {
				if (((AbsFunDecl) decl).parDecls.parDecls().size() != fun.args.args().size()) {
					throw getError(fun, "Function call does not have the same number of parameters as the function declaration.");
				}
				for (int i = 0; i < fun.args.args().size(); i++) {
					SemType argType = fun.args.arg(i).accept(this, visArg).actualType();
					if (!argType.matches(SemAn.isType.get(((AbsFunDecl) decl).parDecls.parDecl(i).type).actualType()) ||
							!(argType instanceof SemBoolType || argType instanceof SemCharType
									|| argType instanceof SemIntType || argType instanceof SemPtrType)) {
						throw getError(fun, "Function parameters in function call do not match the parameters in function declaration.");
					}
					SemAn.isOfType.put(fun.args.arg(i), argType);
				}
				SemType type = SemAn.isType.get(decl.type);
				if (!(type.actualType() instanceof SemBoolType || type.actualType() instanceof SemIntType
						|| type.actualType() instanceof SemCharType || type.actualType() instanceof SemVoidType || type.actualType() instanceof SemPtrType)) {
					throw getError(fun, "Return type can only be of type VOID, INT, CHAR, BOOL or PTR");
				}
				SemAn.isOfType.put(fun, type);
				return type;
			} else {
				throw getError(fun, "Function with name \"" + fun.name + "\" does not exist");
			}
		}
		return null;
	}

	@Override
	public SemType visit(AbsBlockExpr block, TypeResolver.Phase visArg) {
		if (visArg == Phase.AddNamedTypes || visArg == Phase.ResolveTyp || visArg == Phase.MapTypes) {
			block.decls.accept(this, visArg);
			block.expr.accept(this, visArg);
			block.stmts.accept(this, visArg);
		} else if (visArg == Phase.CheckTypes) {
			block.decls.accept(this, visArg);
			block.stmts.accept(this, visArg);
			SemType type = block.expr.accept(this, visArg);
			SemAn.isOfType.put(block, type);
			return type;
		}
		return null;
	}

	@Override
	public SemType visit(AbsCastExpr expr, TypeResolver.Phase visArg) {
		if (visArg == Phase.AddNamedTypes) {
			expr.expr.accept(this, visArg);
		} else if (visArg == Phase.ResolveTyp || visArg == Phase.MapTypes) {
			expr.expr.accept(this, visArg);
			expr.type.accept(this, visArg);
		} else if (visArg == Phase.CheckTypes) {
			SemType cast = expr.expr.accept(this, visArg).actualType();
			SemType castType = SemAn.isType.get(expr.type).actualType();
			if (!(cast instanceof SemCharType || cast instanceof SemIntType || cast instanceof SemPtrType)) {
				throw getError(expr, "Expression inside cast expression should be of type INT, CHAR or PTR");
			}
			if (!(castType instanceof SemCharType || castType instanceof SemIntType || castType instanceof SemPtrType)) {
				throw getError(expr, "Cast type cast expression should be of type INT, CHAR or PTR");
			}
			SemAn.isOfType.put(expr, castType);
			return castType;
		}
		return null;
	}

	@Override
	public SemType visit(AbsExprStmt stmt, TypeResolver.Phase visArg) {
		if (visArg == Phase.AddNamedTypes || visArg == Phase.ResolveTyp || visArg == Phase.MapTypes) {
			stmt.expr.accept(this, visArg);
		} else if (visArg == Phase.CheckTypes) {
			stmt.expr.accept(this, visArg);
			return new SemVoidType();
		}
		return null;
	}

	@Override
	public SemType visit(AbsAssignStmt stmt, TypeResolver.Phase visArg) {
		if (visArg == Phase.AddNamedTypes || visArg == Phase.ResolveTyp || visArg == Phase.MapTypes) {
			stmt.dst.accept(this, visArg);
			stmt.src.accept(this, visArg);
		} else if (visArg == Phase.CheckTypes) {
			SemType dstType = stmt.dst.accept(this, visArg).actualType();
			SemType srcType = stmt.src.accept(this, visArg).actualType();
			if (dstType.matches(srcType)) {
				if (dstType instanceof SemBoolType || dstType instanceof SemIntType
						|| dstType instanceof SemPtrType || dstType instanceof SemCharType) {
					return new SemVoidType();
				} else {
					throw getError(stmt, "Destination and source expressions should be of type INT, CHAR, BOOL or PTR");
				}
			} else {
				throw getError(stmt, "Destination and Source expressions don't match.");
			}
		}
		return null;
	}

	@Override
	public SemType visit(AbsIfStmt ifStmt, TypeResolver.Phase visArg) {
		if (visArg == Phase.AddNamedTypes || visArg == Phase.ResolveTyp || visArg == Phase.MapTypes) {
			ifStmt.cond.accept(this, visArg);
			ifStmt.thenStmts.accept(this, visArg);
			ifStmt.elseStmts.accept(this, visArg);
		} else if (visArg == Phase.CheckTypes) {
			for (AbsStmt stmt : ifStmt.thenStmts.stmts()) {
				if (!(stmt.accept(this, visArg) instanceof SemVoidType)) {
					throw getError(ifStmt, "Some of the stmts are not of type VOID.");
				}
			}
			for (AbsStmt stmt : ifStmt.elseStmts.stmts()) {
				if (!(stmt.accept(this, visArg) instanceof SemVoidType)) {
					throw getError(ifStmt, "Some of the stmts are not of type VOID.");
				}
			}
			if (!(ifStmt.cond.accept(this, visArg).actualType() instanceof SemBoolType)) {
				throw getError(ifStmt, "Condition should be of BOOL type.");
			}
			return new SemVoidType();
		}
		return null;
	}

	@Override
	public SemType visit(AbsWhileStmt whileStmt, TypeResolver.Phase visArg) {
		if (visArg == Phase.AddNamedTypes || visArg == Phase.ResolveTyp || visArg == Phase.MapTypes) {
			whileStmt.cond.accept(this, visArg);
			whileStmt.stmts.accept(this, visArg);
		} else if (visArg == Phase.CheckTypes) {
			for (AbsStmt stmt : whileStmt.stmts.stmts()) {
				if (!(stmt.accept(this, visArg) instanceof SemVoidType)) {
					throw getError(whileStmt, "Some of the stmts are not of type VOID.");
				}
			}
			if (!(whileStmt.cond.accept(this, visArg).actualType() instanceof SemBoolType)) {
				throw getError(whileStmt, "Condition should be of BOOL type.");
			}
			return new SemVoidType();
		}
		return null;
	}

	@Override
	public SemType visit(AbsUntilStmt untilStmt, TypeResolver.Phase visArg) {
		if (visArg == Phase.AddNamedTypes || visArg == Phase.ResolveTyp || visArg == Phase.MapTypes) {
			untilStmt.cond.accept(this, visArg);
			untilStmt.stmts.accept(this, visArg);
		} else if (visArg == Phase.CheckTypes) {
			for (AbsStmt stmt : untilStmt.stmts.stmts()) {
				if (!(stmt.accept(this, visArg) instanceof SemVoidType)) {
					throw getError(untilStmt, "Some of the stmts are not of type VOID.");
				}
			}
			if (!(untilStmt.cond.accept(this, visArg).actualType() instanceof SemBoolType)) {
				throw getError(untilStmt, "Condition should be of BOOL type.");
			}
			return new SemVoidType();
		}
		return null;
	}

	@Override
	public SemType visit(AbsTypName name, TypeResolver.Phase visArg) {
		if (visArg == Phase.ResolveTyp) {
			AbsDecl decl = SemAn.declaredAt.get(name);
			if (decl instanceof AbsTypDecl) {
				SemType type = SemAn.declaresType.get((AbsTypDecl) decl);
				SemAn.isType.put(name, type);
				return type;
			} else {
				throw getError(name, "\"" + name.name + "\" is not a type (type declaration).");
			}
		} else if (visArg == Phase.MapTypes) {
			return SemAn.isType.get(name);
		}
		return null;
	}

	@Override
	public SemType visit(AbsVarName name, TypeResolver.Phase visArg) {
		if (visArg == Phase.CheckTypes) {
			AbsDecl decl = SemAn.declaredAt.get(name);
			if (!(decl instanceof AbsVarDecl)) {
				throw getError(name, "There is no variable or parameter with this name.");
			}
			SemType type = SemAn.isType.get(decl.type);
			SemAn.isOfType.put(name, type);
			return type;
		}
		return null;
	}

	private Report.Error getError(AbsTree node, String error) {
		return new Report.Error(node, "[TypeResolver] " + error);
	}
}
