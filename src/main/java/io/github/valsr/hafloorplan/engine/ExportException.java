package io.github.valsr.hafloorplan.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thrown when an export can't start or fails, with every problem found.
 */
public class ExportException extends Exception {
    private static final long serialVersionUID = 1L;

    private final List<String> problems;

    public ExportException(List<String> problems) {
        this(problems, null);
    }

    public ExportException(List<String> problems, Throwable cause) {
        super(join(problems), cause);
        this.problems = Collections.unmodifiableList(new ArrayList<String>(problems));
    }

    public List<String> getProblems() {
        return this.problems;
    }

    private static String join(List<String> problems) {
        StringBuilder message = new StringBuilder();
        for (String problem : problems) {
            if (message.length() > 0) {
                message.append('\n');
            }
            message.append(problem);
        }
        return message.toString();
    }
}
