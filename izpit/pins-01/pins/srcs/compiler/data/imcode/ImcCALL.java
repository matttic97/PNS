/**
 * @author sliva
 */
package compiler.data.imcode;

import java.util.*;
import compiler.data.layout.*;
import compiler.data.imcode.visitor.*;

public class ImcCALL extends ImcExpr {

	public final Label label;

	private final Vector<ImcExpr> args;

	public ImcCALL(Label label, Vector<ImcExpr> args) {
		this.label = label;
		this.args = new Vector<ImcExpr>(args);
	}

	public Vector<ImcExpr> args() {
		return new Vector<ImcExpr>(args);
	}

	@Override
	public <Result, Arg> Result accept(ImcVisitor<Result, Arg> visitor, Arg accArg) {
		return visitor.visit(this, accArg);
	}

}
