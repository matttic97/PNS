/**
 * @author sliva
 */
package compiler.data.chunk;

import compiler.data.layout.*;

/**
 * A data chunk.
 * 
 * @author sliva
 */
public class DataChunk extends Chunk {

	/** The label where data is placed at. */
	public final Label label;

	/** The size of data. */
	public final long size;

	public DataChunk(AbsAccess absAccess) {
		this.label = absAccess.label;
		this.size = absAccess.size;
	}

}
