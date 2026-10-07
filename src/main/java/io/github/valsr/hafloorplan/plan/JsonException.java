package io.github.valsr.hafloorplan.plan;

/**
 * Thrown when a text isn't valid JSON, with the position of the problem.
 */
public class JsonException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final int line;
    private final int column;

    public JsonException(String problem, int line, int column) {
        super(problem + " at line " + line + ", column " + column);
        this.line = line;
        this.column = column;
    }

    /**
     * Returns the line of the problem, starting at 1.
     */
    public int getLine() {
        return this.line;
    }

    /**
     * Returns the column of the problem, starting at 1.
     */
    public int getColumn() {
        return this.column;
    }
}
