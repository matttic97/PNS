/**
 * @author sliva
 */
package compiler.phases.lexan;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Queue;

import compiler.common.report.*;
import compiler.data.symbol.*;
import compiler.phases.*;

/**
 * Lexical analysis.
 * 
 * @author sliva
 */
public class LexAn extends Phase {

	/** The name of the source file. */
	private final String srcFileName;

	/** The source file reader. */
	private final BufferedReader srcFile;

	private int rLocation = 1;
	private int srLocation = 1;
	private int scLocation = 0;
	private int erLocation = 1;
	private int ecLocation = 1;
	private int cLocation = 0;

	/**
	 * Constructs a new phase of lexical analysis.
	 */
	public LexAn() {
		super("lexan");
		srcFileName = compiler.Main.cmdLineArgValue("--src-file-name");
		try {
			srcFile = new BufferedReader(new FileReader(srcFileName));

		} catch (IOException ___) {
			throw new Report.Error("Cannot open source file '" + srcFileName + "'.");
		}

		current = getNextChar();
	}

	@Override
	public void close() {
		try {
			srcFile.close();
		} catch (IOException ___) {
			Report.warning("Cannot close source file '" + this.srcFileName + "'.");
		}
		super.close();
	}

	/**
	 * The lexer.
	 * 
	 * This method returns the next symbol from the source file. To perform the
	 * lexical analysis of the entire source file, this method must be called until
	 * it returns EOF. This method calls {@link #lexify()}, logs its result if
	 * requested, and returns it.
	 * 
	 * @return The next symbol from the source file or EOF if no symbol is available
	 *         any more.
	 */
	public Symbol lexer() {
		Symbol symb = lexify();
		if (symb.token != Symbol.Term.EOF)
			symb.log(logger);
		return symb;
	}

	/**
	 * Performs the lexical analysis of the source file.
	 * 
	 * This method returns the next symbol from the source file. To perform the
	 * lexical analysis of the entire source file, this method must be called until
	 * it returns EOF.
	 * 
	 * @return The next symbol from the source file or EOF if no symbol is available
	 *         any more.
	 */

	private String lex;	//tle bomo dodajali chare, in sestavljali lexeme
	private int current = -1;	//tle bomo hranili char, prebran iz daoteke

	private Symbol lexify() {
		// TODO

		/** Najprej preverimo, ce je EOF */
		if (isEOF())
			return new Symbol(Symbol.Term.EOF, "EOF", getLocation());

		/** določimo zacetno pozicijo, ter "resetiramo" lexeme*/
		scLocation = cLocation;
		srLocation = rLocation;
		lex = "";

		/** preverimo za kaksno vrsto simbola je prebran char kandidat */
		// za int konstanto
		if ('0' <= current && current <= '9') {
			return isIntConst();
		}
		// za char konstanto
		else if (current == '\'') { // za
			return isCharConst();
		}
		// za identifier
		else if (('a' <= current && current <= 'z') ||
				('A' <= current && current <= 'Z')) {
			return isIdentifier();
		}
		// za belo besedilo
		else if (current == ' ' || current == '\t' || current == '\r' || current == '\n'){
			current = getNextChar();
			if (isEOF())
				return new Symbol(Symbol.Term.EOF, "EOF", getLocation());

			// beremo belo besedilo dokler ne pridemo do novega znaka ali EOF
			while (current == ' ' || current == '\t' || current == '\r' || current == '\n'){
				current = getNextChar();
				if (isEOF())
					return new Symbol(Symbol.Term.EOF, "EOF", getLocation());
			}

			/** ko najdemo nov znak, moramo na novo poklicati lexify, da bere naprej */
			return lexify();
		}
		// za komentar
		else if (current == '#') {
			return isComment();
		}
		// ce ni nic od zgornjega je najbr simbol ali pa neprepoznan znak
		else {
			return isSymbol();
		}

	}

	/** beremo znak po znak */
	private char getNextChar(){
		try
		{
			setLocation();
			return (char)this.srcFile.read();
		}
		catch (Exception ex)
		{
			return 0;
		}
	}

	/** nastavimo zacetno pozicijo */
	private void setLocation(){

		ecLocation = cLocation;
		erLocation = rLocation;

		switch (getCurrent()) {
			case "\n":
				cLocation = 1; rLocation += 1;
				break;
			case "\t":
				cLocation += 4;
				break;
			default:
				cLocation++;
				break;
		}
	}

	/** Vrne "sestavljeno" lokacijo */
	private Locatable getLocation(){
		return new Location(srLocation, scLocation, erLocation, ecLocation);
	}

	/** Vrne trenuten znak kot String */
	private String getCurrent(){
		return "" + (char)current;
	}

	private String getInvalidChar(){
		return "[Lexan] Invalid char " + "['" + getCurrent() + "', " + current + "].";
	}

	private boolean isEOF(){
		if(current == -1 || current == 65535)
			return true;
		return false;
	}

	/**
	 * Metode za preverjanje konstant, kljucnih besed ter simbolov
	 *
	 * */
	private Symbol isIntConst(){
		lex += (char)current;
		current = getNextChar();

		if (isEOF())
			return new Symbol(Symbol.Term.EOF, "EOF", getLocation());

		while((current >= '0' && current <= '9')){
			lex += (char)current;
			current = getNextChar();

			if (isEOF())
				return new Symbol(Symbol.Term.EOF, "EOF", getLocation());
		}

		return new Symbol(Symbol.Term.INTCONST, lex, getLocation());
	}

	private Symbol isCharConst(){
		lex += (char)current;
		current = getNextChar();

		if(32 <= current && current <= 126){
			lex += (char)current;
			current = getNextChar();

			if(current == '\''){
				lex += (char)current;
				current = getNextChar();

				return new Symbol(Symbol.Term.CHARCONST, lex, getLocation());
			}
			else
				throw new Report.Error(getLocation(), "Char not closed");

		}
		else{
			ecLocation++;
			if(isEOF())
				throw new Report.Error(getLocation(), "Char not closed");
			throw new Report.Error(getLocation(), getInvalidChar());
		}

	}

	private Symbol isIdentifier(){
		lex += (char)current ;
		current = getNextChar();

		if(isEOF())
			return new Symbol(Symbol.Term.IDENTIFIER, lex, getLocation());

		while((current >= '0' && current <= '9') || (current >= 'a' && current <= 'z')
				|| (current >= 'A' && current <= 'Z') || current == '_'){
			lex += (char)current;
			current = getNextChar();

			if(isEOF()){
				return checkReserved();
			}
		}

		return checkReserved();
	}

	private Symbol checkReserved(){
		switch(lex) {
			/** kljucne besede */
			case "arr":
				return new Symbol(Symbol.Term.ARR, lex, getLocation());
			case "bool":
				return new Symbol(Symbol.Term.BOOL, lex, getLocation());
			case "char":
				return new Symbol(Symbol.Term.CHAR, lex, getLocation());
			case "del":
				return new Symbol(Symbol.Term.DEL, lex, getLocation());
			case "do":
				return new Symbol(Symbol.Term.DO, lex, getLocation());
			case "else":
				return new Symbol(Symbol.Term.ELSE, lex, getLocation());
			case "end":
				return new Symbol(Symbol.Term.END, lex, getLocation());
			case "fun":
				return new Symbol(Symbol.Term.FUN, lex, getLocation());
			case "if":
				return new Symbol(Symbol.Term.IF, lex, getLocation());
			case "int":
				return new Symbol(Symbol.Term.INT, lex, getLocation());
			case "new":
				return new Symbol(Symbol.Term.NEW, lex, getLocation());
			case "ptr":
				return new Symbol(Symbol.Term.PTR, lex, getLocation());
			case "then":
				return new Symbol(Symbol.Term.THEN, lex, getLocation());
			case "typ":
				return new Symbol(Symbol.Term.TYP, lex, getLocation());
			case "var":
				return new Symbol(Symbol.Term.VAR, lex, getLocation());
			case "void":
				return new Symbol(Symbol.Term.VOID, lex, getLocation());
			case "where":
				return new Symbol(Symbol.Term.WHERE, lex, getLocation());
			case "while":
				return new Symbol(Symbol.Term.WHILE, lex, getLocation());
			case "until":
				return new Symbol(Symbol.Term.UNTIL, lex, getLocation());

			/** konstante */
			case "none":
				return new Symbol(Symbol.Term.VOIDCONST, lex, getLocation());
			case "null":
				return new Symbol(Symbol.Term.PTRCONST, lex, getLocation());
			case "true":
				return new Symbol(Symbol.Term.BOOLCONST, lex, getLocation());
			case "false":
				return new Symbol(Symbol.Term.BOOLCONST, lex, getLocation());
			default: return new Symbol(Symbol.Term.IDENTIFIER, lex, getLocation());
		}
	}

	private Symbol isComment(){
		for(int i=0; i<27; i++) {
			//while (current != '\n') {  // komentarje ignoriramo, zato beremo do nove vrstice
			current = getNextChar();

			if(current == '\n') {
				current = getNextChar();
				return lexify();
			}

			if (isEOF())
				return new Symbol(Symbol.Term.EOF, "EOF", getLocation());
			//}
		}
			// na novo zacnemo preverjati drugo vrstico
			current = getNextChar();
			if(current != '\n')
				throw new Report.Error( getLocation(), "Comment must not be longer then 10 chars.");
			else
				return lexify();

	}

	private Symbol isSymbol(){
		switch(current)
		{
			/** simboli */
			case '+':{
				current = getNextChar();
				return new Symbol(Symbol.Term.ADD, "+", getLocation());
			}
			case '-': {
				current = getNextChar();
				return new Symbol(Symbol.Term.SUB, "-", getLocation());
			}
			case '*': {
				current = getNextChar();
				return new Symbol(Symbol.Term.MUL, "*", getLocation());
			}
			case '/': {
				current = getNextChar();
				return new Symbol(Symbol.Term.DIV, "/", getLocation());
			}
			case '%': {
				current = getNextChar();
				return new Symbol(Symbol.Term.MOD, "%", getLocation());
			}
			case '$': {
				current = getNextChar();
				return new Symbol(Symbol.Term.ADDR, "$", getLocation());
			}
			case '@': {
				current = getNextChar();
				return new Symbol(Symbol.Term.DATA, "@", getLocation());
			}
			case ',': {
				current = getNextChar();
				return new Symbol(Symbol.Term.COMMA, ",", getLocation());
			}
			case ':': {
				current = getNextChar();
				return new Symbol(Symbol.Term.COLON, ":", getLocation());
			}
			case ';': {
				current = getNextChar();
				return new Symbol(Symbol.Term.SEMIC, ";", getLocation());
			}
			case '[': {
				current = getNextChar();
				return new Symbol(Symbol.Term.LBRACKET, "[", getLocation());
			}
			case ']': {
				current = getNextChar();
				return new Symbol(Symbol.Term.RBRACKET, "]", getLocation());
			}
			case '(': {
				current = getNextChar();
				return new Symbol(Symbol.Term.LPARENTHESIS, "(", getLocation());
			}
			case ')': {
				current = getNextChar();
				return new Symbol(Symbol.Term.RPARENTHESIS, ")", getLocation());
			}
			case '{': {
				current = getNextChar();
				return new Symbol(Symbol.Term.LBRACE, "{", getLocation());
			}
			case '}': {
				current = getNextChar();
				return new Symbol(Symbol.Term.RBRACE, "}", getLocation());
			}
			case '=': {
				current = getNextChar();
				if(current == '='){
					current = getNextChar();
					return new Symbol(Symbol.Term.EQU, "==", getLocation());
				}
				else
					return new Symbol(Symbol.Term.ASSIGN, "=", getLocation());
			}
			case '!': {
				current = getNextChar();
				if(current == '='){
					current = getNextChar();
					return new Symbol(Symbol.Term.NEQ, "!=", getLocation());
				}
				else{
					ecLocation++;
					throw new Report.Error(getLocation(), getInvalidChar());
				}

			}
			case '<': {
				current = getNextChar();
				if(current == '='){
					current = getNextChar();
					return new Symbol(Symbol.Term.LEQ, "<=", getLocation());
				}
				else
					return new Symbol(Symbol.Term.LTH, "<", getLocation());
			}
			case '>': {
				current = getNextChar();
				if(current == '='){
					current = getNextChar();
					return new Symbol(Symbol.Term.GEQ, ">=", getLocation());
				}
				else
					return new Symbol(Symbol.Term.GTH, ">", getLocation());
			}

			default:{
				ecLocation++;
				throw new Report.Error(getLocation(), getInvalidChar());
			}
		}
	}

}