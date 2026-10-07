/*
 * HA Floorplan Exporter, a Sweet Home 3D plugin
 * Copyright (c) 2026 valsr
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <https://www.gnu.org/licenses/>.
 */
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
