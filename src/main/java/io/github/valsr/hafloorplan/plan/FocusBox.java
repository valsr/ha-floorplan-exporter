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

import java.util.Objects;

/**
 * The rectangle of an image the walls of a floor occupy, in pixels: from <code>left</code> included
 * to <code>right</code> excluded, and from <code>top</code> to <code>bottom</code>.
 */
public final class FocusBox {
    public final int left;
    public final int top;
    public final int right;
    public final int bottom;

    public FocusBox(int left, int top, int right, int bottom) {
        if (left >= right || top >= bottom) {
            throw new IllegalArgumentException("Empty focus box " + left + "-" + right + " x " + top + "-" + bottom);
        }
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    /** Returns <code>true</code> if the box lies inside an image of the given size. */
    public boolean fits(int width, int height) {
        return this.left >= 0 && this.top >= 0 && this.right <= width && this.bottom <= height;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof FocusBox)) {
            return false;
        }
        FocusBox other = (FocusBox)obj;
        return this.left == other.left && this.top == other.top
                && this.right == other.right && this.bottom == other.bottom;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.left, this.top, this.right, this.bottom);
    }

    @Override
    public String toString() {
        return this.left + "-" + this.right + " x " + this.top + "-" + this.bottom;
    }
}
