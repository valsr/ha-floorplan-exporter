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
