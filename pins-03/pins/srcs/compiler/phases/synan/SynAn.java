/**
 * @author sliva
 */
package compiler.phases.synan;

import compiler.common.report.*;
import compiler.data.symbol.*;
import compiler.data.dertree.*;
import compiler.phases.*;
import compiler.phases.lexan.*;

/**
 * Syntax analysis.
 * 
 * @author sliva
 */
public class SynAn extends Phase {

	/** The derivation tree of the program being compiled. */
	public static DerTree derTree = null;

	/** The lexical analyzer used by this syntax analyzer. */
	private final LexAn lexAn;

	/**
	 * Constructs a new phase of syntax analysis.
	 */
	public SynAn() {
		super("synan");
		lexAn = new LexAn();
	}

	@Override
	public void close() {
		lexAn.close();
		super.close();
	}

	/**
	 * The parser.
	 * 
	 * This method constructs a derivation tree of the program in the source file.
	 * It calls method {@link #parseSource()} that starts a recursive descent parser
	 * implementation of an LL(1) parsing algorithm.
	 */
	public void parser() {
		currSymb = lexAn.lexer();
		derTree = parseSource();
		if (currSymb.token != Symbol.Term.EOF)
			throw new Report.Error(currSymb, "Unexpected '" + currSymb + "' at the end of a program.");
	}

	/** The lookahead buffer (of length 1). */
	private Symbol currSymb = null;

	/**
	 * Appends the current symbol in the lookahead buffer to a derivation tree node
	 * (typically the node of the derivation tree that is currently being expanded
	 * by the parser) and replaces the current symbol (just added) with the next
	 * input symbol.
	 * 
	 * @param node The node of the derivation tree currently being expanded by the
	 *             parser.
	 */
	private void add(DerNode node) {
		if (currSymb == null)
			throw new Report.InternalError();
		node.add(new DerLeaf(currSymb));
		currSymb = lexAn.lexer();
	}

	/**
	 * If the current symbol is the expected terminal, appends the current symbol in
	 * the lookahead buffer to a derivation tree node (typically the node of the
	 * derivation tree that is currently being expanded by the parser) and replaces
	 * the current symbol (just added) with the next input symbol. Otherwise,
	 * produces the error message.
	 * 
	 * @param node     The node of the derivation tree currently being expanded by
	 *                 the parser.
	 * @param token    The expected terminal.
	 * @param errorMsg The error message.
	 */
	private void add(DerNode node, Symbol.Term token, String errorMsg) {
		if (currSymb == null)
			throw new Report.InternalError();
		if (currSymb.token == token) {
			node.add(new DerLeaf(currSymb));
			currSymb = lexAn.lexer();
		} else
			throw new Report.Error(currSymb, errorMsg);
	}

	private DerNode parseSource() {
		DerNode node = new DerNode(DerNode.Nont.Source);
		node.add(parseDecls());
		return node;
	}

	// TODO
	private DerNode parseDecls(){
		DerNode node = new DerNode(DerNode.Nont.Decls);
		node.add(parseDecl());
		node.add(parseDeclsRest());
		return node;
	}

	private DerNode parseDecl(){
		DerNode node = new DerNode(DerNode.Nont.Decl);
		switch (currSymb.token){
			case TYP:
			case VAR: {
				add(node);
				add(node, Symbol.Term.IDENTIFIER, getErrorString(Symbol.Term.IDENTIFIER));
				add(node, Symbol.Term.COLON, getErrorString(Symbol.Term.COLON));
				node.add(parseType());
				break;
			}
			case FUN:{
				add(node);
				add(node, Symbol.Term.IDENTIFIER, getErrorString(Symbol.Term.IDENTIFIER));
				add(node, Symbol.Term.LPARENTHESIS, getErrorString(Symbol.Term.LPARENTHESIS));
				node.add(parseParDeclsEps());
				add(node, Symbol.Term.RPARENTHESIS, getErrorString(Symbol.Term.RPARENTHESIS));
				add(node, Symbol.Term.COLON, getErrorString(Symbol.Term.COLON));
				node.add(parseType());
				node.add(parseBodyEps());
				break;
			}
			default: {
				throw getError("Declaration");
			}
		}
		return node;
	}

	private DerNode parseDeclsRest(){
		DerNode node = new DerNode(DerNode.Nont.DeclsRest);
		switch (currSymb.token){
			case TYP:
			case VAR:
			case FUN:{
				node.add(parseDecls());
				break;
			}
			case EOF:
			case RBRACE: {
				break;
			}
			default: throw getError("Declaration");
		}
		return node;
	}

	private DerNode parseParDeclsEps(){
		DerNode node = new DerNode(DerNode.Nont.ParDeclsEps);
		switch (currSymb.token){
			case RPARENTHESIS: {
				break;
			}
			default: {
				node.add(parseParDecls());
				break;
			}
		}
		return node;
	}

	private DerNode parseParDecls(){
		DerNode node = new DerNode(DerNode.Nont.ParDecls);
		node.add(parseParDecl());
		node.add(parseParDeclsRest());
		return node;
	}

	private DerNode parseParDecl(){
		DerNode node = new DerNode(DerNode.Nont.ParDecl);
		if(currSymb.token == Symbol.Term.IDENTIFIER){
				add(node);
				add(node, Symbol.Term.COLON, getErrorString(Symbol.Term.COLON));
				node.add(parseType());
				return node;
			}
		else
			throw getError("Parameter declaration");
	}

	private DerNode parseParDeclsRest(){
		DerNode node = new DerNode(DerNode.Nont.ParDeclsRest);
		switch(currSymb.token){
			case RPARENTHESIS: break;
			case COMMA: {
				add(node);
				node.add(parseParDecls());
				break;
			}
			default: throw getError("Parameter declaration");
		}
		return node;
	}

	private DerNode parseBodyEps(){
		DerNode node = new DerNode(DerNode.Nont.BodyEps);
		switch(currSymb.token){
			case TYP:
			case VAR:
			case FUN:
			case RBRACE:
			case EOF: {
				break;
			}
			case ASSIGN: {
				add(node);
				node.add(parseStmts());
				add(node, Symbol.Term.COLON, getErrorString(Symbol.Term.COLON));
				node.add(parseRelExpr());
				node.add(parseWhereEps());
				break;
			}
			default: throw getError("BodyEps (Function body)");
		}
		return node;
	}

	private DerNode parseType(){
		DerNode node = new DerNode(DerNode.Nont.Type);
		switch(currSymb.token){
			case IDENTIFIER:
			case VOID:
			case INT:
			case CHAR:
			case BOOL: {
				add(node);
				break;
			}
			case LPARENTHESIS: {
				add(node);
				node.add(parseType());
				add(node, Symbol.Term.RPARENTHESIS, getErrorString(Symbol.Term.RPARENTHESIS));
				break;
			}
			case ARR: {
				add(node);
				add(node, Symbol.Term.LBRACKET, getErrorString(Symbol.Term.LBRACKET));
				node.add(parseRelExpr());
				add(node, Symbol.Term.RBRACKET, getErrorString(Symbol.Term.RBRACKET));
				node.add(parseType());
				break;
			}
			case PTR: {
				add(node);
				node.add(parseType());
				break;
			}
			default: throw getError("Type");
		}
		return node;
	}

	private DerNode parseRelExpr(){
		DerNode node = new DerNode(DerNode.Nont.RelExpr);
		node.add(parseAddExpr());
		node.add(parseRelExprRest());
		return node;
	}

	private DerNode parseRelExprRest(){
		DerNode node = new DerNode(DerNode.Nont.RelExprRest);
		switch (currSymb.token){
			case TYP:
			case COLON:
			case VAR:
			case FUN:
			case RPARENTHESIS:
			case COMMA:
			case ASSIGN:
			case RBRACKET:
			case LBRACE:
			case RBRACE:
			case THEN:
			case END:
			case DO:
			case SEMIC:
			case ELSE:
			case EOF: {
				break;
			}
			case EQU:
			case NEQ:
			case LEQ:
			case GEQ:
			case LTH:
			case GTH:{
				add(node);
				node.add(parseAddExpr());
				break;
			}
			default: {
				throw getError("Relational Expression");
			}
		}
		return node;
	}

	private DerNode parseAddExpr(){
		DerNode node = new DerNode(DerNode.Nont.AddExpr);
		node.add(parseMulExpr());
		node.add(parseAddExprRest());
		return node;
	}

	private DerNode parseAddExprRest(){
		DerNode node = new DerNode(DerNode.Nont.AddExprRest);
		switch (currSymb.token){
			case TYP:
			case COLON:
			case VAR:
			case FUN:
			case RPARENTHESIS:
			case COMMA:
			case ASSIGN:
			case RBRACKET:
			case LBRACE:
			case RBRACE:
			case THEN:
			case END:
			case DO:
			case SEMIC:
			case ELSE:
			case EOF:
			case EQU:
			case NEQ:
			case LEQ:
			case GEQ:
			case LTH:
			case GTH: {
				break;
			}
			case ADD:
			case SUB: {
				add(node);
				node.add(parseMulExpr());
				node.add(parseAddExprRest());
				break;
			}
			default: {
				throw getError("Additive Expression");
			}
		}
		return node;
	}

	private DerNode parseMulExpr(){
		DerNode node = new DerNode(DerNode.Nont.MulExpr);
		node.add(parsePrefExpr());
		node.add(parseMulExprRest());
		return node;
	}

	private DerNode parseMulExprRest(){
		DerNode node = new DerNode(DerNode.Nont.MulExprRest);
		switch (currSymb.token){
			case TYP:
			case COLON:
			case VAR:
			case FUN:
			case RPARENTHESIS:
			case COMMA:
			case ASSIGN:
			case RBRACKET:
			case EQU:
			case NEQ:
			case LEQ:
			case GEQ:
			case LTH:
			case GTH:
			case ADD:
			case SUB:
			case LBRACE:
			case RBRACE:
			case THEN:
			case END:
			case DO:
			case SEMIC:
			case ELSE:
			case EOF: {
				break;
			}
			case MUL:
			case DIV:
			case MOD:{
				add(node);
				node.add(parsePrefExpr());
				node.add(parseMulExprRest());
				break;
			}
			default: {
				throw getError("Multiplicative Expression");
			}
		}
		return node;
	}

	private DerNode parsePrefExpr(){
		DerNode node = new DerNode(DerNode.Nont.PrefExpr);
		switch (currSymb.token){
			case IDENTIFIER:
			case LPARENTHESIS:
			case CHARCONST:
			case BOOLCONST:
			case INTCONST:
			case PTRCONST:
			case VOIDCONST: {
				node.add(parsePstfExpr());
				break;
			}
			case ADD:
			case SUB:
			case DATA:
			case ADDR: {
				add(node);
				node.add(parsePrefExpr());
				break;
			}
			case NEW: {
				add(node);
				add(node, Symbol.Term.LPARENTHESIS, getErrorString(Symbol.Term.LPARENTHESIS));
				node.add(parseType());
				add(node, Symbol.Term.RPARENTHESIS, getErrorString(Symbol.Term.RPARENTHESIS));
				break;
			}
			case DEL: {
				add(node);
				add(node, Symbol.Term.LPARENTHESIS, getErrorString(Symbol.Term.LPARENTHESIS));
				node.add(parseRelExpr());
				add(node, Symbol.Term.RPARENTHESIS, getErrorString(Symbol.Term.RPARENTHESIS));
				break;
			}
			default: {
				throw getError("Prefix Expression");
			}
		}
		return node;
	}

	private DerNode parsePstfExpr(){
		DerNode node = new DerNode(DerNode.Nont.PstfExpr);
		node.add(parseExpr());
		node.add(parsePstfExpRest());
		return node;
	}

	private DerNode parsePstfExpRest(){
		DerNode node = new DerNode(DerNode.Nont.PstfExprRest);
		switch (currSymb.token){
			case TYP:
			case COLON:
			case VAR:
			case FUN:
			case RPARENTHESIS:
			case COMMA:
			case ASSIGN:
			case RBRACKET:
			case EQU:
			case NEQ:
			case LEQ:
			case GEQ:
			case LTH:
			case GTH:
			case ADD:
			case SUB:
			case MUL:
			case DIV:
			case MOD:
			case LBRACE:
			case RBRACE:
			case THEN:
			case END:
			case DO:
			case SEMIC:
			case ELSE:
			case EOF:{
				break;
			}
			case LBRACKET: {
				add(node);
				node.add(parseRelExpr ());
				add(node, Symbol.Term.RBRACKET, getErrorString(Symbol.Term.RBRACKET));
				node.add(parsePstfExpRest());
				break;
			}
			default: {
				throw getError("Postfix Expression");
			}
		}
		return node;
	}

	private DerNode parseExpr(){
		DerNode node = new DerNode(DerNode.Nont.Expr);
		switch (currSymb.token){
			case IDENTIFIER:
			case CHARCONST:
			case BOOLCONST:
			case INTCONST:
			case PTRCONST:
			case VOIDCONST:	{
				node.add(parseAtomExpr());
				break;
			}
			case LPARENTHESIS: {
				add(node);
				node.add(parseRelExpr());
				node.add(parseCastEps());
				add(node, Symbol.Term.RPARENTHESIS, getErrorString(Symbol.Term.RPARENTHESIS));
				break;
			}
			default: {
				throw getError("Expression");
			}
		}
		return node;
	}

	private DerNode parseAtomExpr(){
		DerNode node = new DerNode(DerNode.Nont.AtomExpr);
		switch(currSymb.token){
			case IDENTIFIER: {
				add(node);
				node.add(parseCallEps());
				break;
			}
			case CHARCONST:
			case BOOLCONST:
			case INTCONST:
			case PTRCONST:
			case VOIDCONST:	{
				add(node);
				break;
			}
			default: {
				throw getError("Atom expression");
			}
		}
		return node;
	}

	private DerNode parseCallEps(){
		DerNode node = new DerNode(DerNode.Nont.CallEps);
		switch (currSymb.token){
			case TYP:
			case COLON:
			case VAR:
			case FUN:
			case RPARENTHESIS:
			case COMMA:
			case ASSIGN:
			case LBRACKET:
			case RBRACKET:
			case EQU:
			case NEQ:
			case LEQ:
			case GEQ:
			case LTH:
			case GTH:
			case ADD:
			case SUB:
			case MUL:
			case DIV:
			case MOD:
			case LBRACE:
			case RBRACE:
			case THEN:
			case END:
			case DO:
			case SEMIC:
			case ELSE:
			case EOF: {
				break;
			}
			case LPARENTHESIS: {
				add(node);
				node.add(parseArgsEps());
				add(node, Symbol.Term.RPARENTHESIS, getErrorString(Symbol.Term.RPARENTHESIS));
				break;
			}
			default: {
				throw getError("Function Call");
			}

		}
		return node;
	}

	private DerNode parseArgsEps(){
		DerNode node = new DerNode(DerNode.Nont.ArgsEps);
		if (currSymb.token.equals(Symbol.Term.RPARENTHESIS)){
			return node;
		} else {
			node.add(parseArgs());
			node.add(parseArgsRest());
			return node;
		}
	}

	private DerNode parseArgs(){
		DerNode node = new DerNode(DerNode.Nont.Args);
		node.add(parseRelExpr());
		return node;
	}

	private DerNode parseArgsRest(){
		DerNode node = new DerNode(DerNode.Nont.ArgsRest);
		switch (currSymb.token){
			case RPARENTHESIS: {
				break;
			}
			case COMMA: {
				add(node);
				node.add(parseArgs());
				node.add(parseArgsRest());
				break;
			}
			default: {
				throw getError("Arguments");
			}
		}
		return node;
	}

	private DerNode parseCastEps(){
		DerNode node = new DerNode(DerNode.Nont.CastEps);
		switch (currSymb.token){
			case RPARENTHESIS: {
				break;
			}
			case COLON: {
				add(node);
				node.add(parseType());
				break;
			}
			default: {
				throw getError("Cast Expression");
			}
		}
		return node;
	}

	private DerNode parseWhereEps(){
		DerNode node = new DerNode(DerNode.Nont.WhereEps);
		switch (currSymb.token){
			case TYP:
			case VAR:
			case FUN:
			case RBRACE:
			case EOF: {
				break;
			}
			case LBRACE: {
				add(node);
				add(node, Symbol.Term.WHERE, getErrorString(Symbol.Term.WHERE));
				node.add(parseDecls());
				add(node, Symbol.Term.RBRACE, getErrorString(Symbol.Term.RBRACE));
				break;
			}
			default: {
				throw getError("{Where}");
			}
		}
		return node;
	}

	private DerNode parseStmts(){
		DerNode node = new DerNode(DerNode.Nont.Stmts);
		node.add(parseStmt());
		node.add(parseStmtsRest());
		return node;
	}

	private DerNode parseStmt(){
		DerNode node = new DerNode(DerNode.Nont.Stmt);
		switch (currSymb.token){
			case IDENTIFIER:
			case LPARENTHESIS:
			case ADD:
			case SUB:
			case DATA:
			case ADDR:
			case NEW:
			case DEL:
			case VOIDCONST:
			case BOOLCONST:
			case INTCONST:
			case PTRCONST:
			case CHARCONST: {
				node.add(parseRelExpr());
				node.add(parseAssignEps());
				break;
			}
			case IF:{
				add(node);
				node.add(parseRelExpr());
				add(node,Symbol.Term.THEN, getErrorString(Symbol.Term.THEN));
				node.add(parseStmts());
				node.add(parseElseEps());
				add(node,Symbol.Term.END, getErrorString(Symbol.Term.END));
				break;
			}
			case WHILE: {
				add(node);
				node.add(parseRelExpr());
				add(node, Symbol.Term.DO, getErrorString(Symbol.Term.DO));
				node.add(parseStmts());
				add(node, Symbol.Term.END, getErrorString(Symbol.Term.END));
				break;
			}
			default: {
				throw getError("Statement");
			}
		}
		return node;
	}

	private DerNode parseStmtsRest(){
		DerNode node = new DerNode(DerNode.Nont.StmtsRest);
		switch (currSymb.token){
			case COLON:
			case END:
			case ELSE: {
				break;
			}
			case SEMIC: {
				add(node);
				node.add(parseStmts());
				break;
			}
			default: {
				throw getError("Statement");
			}
		}
		return node;
	}

	private DerNode parseAssignEps(){
		DerNode node = new DerNode(DerNode.Nont.AssignEps);
		switch (currSymb.token){
			case COLON:
			case END:
			case ELSE:
			case SEMIC: {
				break;
			}
			case ASSIGN:{
				add(node);
				node.add(parseRelExpr());
				break;
			}
			default: {
				throw getError("Assign Statement");
			}
		}
		return node;
	}

	private DerNode parseElseEps(){
		DerNode node = new DerNode(DerNode.Nont.ElseEps);
		switch (currSymb.token){
			case END: {
				break;
			}
			case ELSE:{
				add(node);
				node.add(parseStmts());
				break;
			}
			default: {
				throw getError("Else");
			}
		}
		return node;
	}

	private String getErrorString(Symbol.Term token){
		return String.format("[Synan] Got symbol %s instead of %s.", currSymb.token, token);
	}

	private Report.Error getError(String string){
		return new Report.Error(
				currSymb, String.format("[Synan] Didn't expect symbol '%s' in '%s'.", currSymb, string));
	}

}
