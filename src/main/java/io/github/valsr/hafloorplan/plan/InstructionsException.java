package io.github.valsr.hafloorplan.plan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thrown when an instructions file can't be used, with every problem found in it.
 */
public class InstructionsException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final List<String> problems;

    public InstructionsException(List<String> problems) {
        super(join(problems));
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
