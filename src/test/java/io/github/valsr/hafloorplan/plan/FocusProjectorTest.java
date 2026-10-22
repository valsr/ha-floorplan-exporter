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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class FocusProjectorTest {
    private static final double RIGHT_ANGLE = Math.PI / 2;

    private static double [] corner(double x, double y, double elevation) {
        return new double [] {x, y, elevation};
    }

    /** Projects through a camera at the origin looking along +y with yaw 0, a field of view of 90° and k = 500. */
    private static FocusBox ahead(double margin, double [] ... corners) {
        return FocusProjector.project(0, 0, 0, 0, 0, RIGHT_ANGLE, 1000, 1000, Arrays.asList(corners), margin);
    }

    @Test
    void pointStraightAheadIsCentred() {
        FocusBox box = FocusProjector.project(0, 0, 0, 0, 0, RIGHT_ANGLE, 100, 50, Arrays.asList(
                corner(-1, 100, 0), corner(1, 100, 0), corner(0, 100, 1), corner(0, 100, -1)), 0);
        assertEquals(new FocusBox(49, 24, 51, 26), box);
    }

    @Test
    void topCameraOrientation() {
        // Seen from above with yaw 0, the renderer shows the plan turned by 180°
        FocusBox plusX = FocusProjector.project(0, 0, 1000, 0, RIGHT_ANGLE, RIGHT_ANGLE, 1000, 1000,
                Arrays.asList(corner(100, 0, 0), corner(110, 1, 0)), 0);
        assertNotNull(plusX);
        assertTrue(plusX.right < 500, "plan +x is on the left: " + plusX);
        FocusBox plusY = FocusProjector.project(0, 0, 1000, 0, RIGHT_ANGLE, RIGHT_ANGLE, 1000, 1000,
                Arrays.asList(corner(0, 100, 0), corner(1, 110, 0)), 0);
        assertNotNull(plusY);
        assertTrue(plusY.bottom < 500, "plan +y is at the top: " + plusY);
    }

    @Test
    void yawRotatesTheView() {
        List<double []> corners = Arrays.asList(corner(-50, 300, 20), corner(80, 400, -30), corner(10, 250, 60));
        List<double []> rotated = new ArrayList<double []>();
        for (double [] c : corners) {
            // What is ahead along +y with yaw 0 is ahead along -x with yaw π/2
            rotated.add(corner(-c [1], c [0], c [2]));
        }
        FocusBox straight = FocusProjector.project(0, 0, 0, 0, 0, RIGHT_ANGLE, 1000, 1000, corners, 0);
        FocusBox turned = FocusProjector.project(0, 0, 0, RIGHT_ANGLE, 0, RIGHT_ANGLE, 1000, 1000, rotated, 0);
        assertNotNull(straight);
        assertEquals(straight, turned);
    }

    @Test
    void marginIsPercentOfBox() {
        // sx = 500 - x and sy = 500 - elevation at a depth of 500
        assertEquals(new FocusBox(100, 200, 300, 400), ahead(0, corner(400, 500, 300), corner(200, 500, 100)));
        assertEquals(new FocusBox(80, 180, 320, 420), ahead(10, corner(400, 500, 300), corner(200, 500, 100)));
    }

    @Test
    void roundsOutwards() {
        assertEquals(new FocusBox(10, 20, 31, 41), ahead(0, corner(489.8, 500, 479.3), corner(469.8, 500, 459.3)));
    }

    @Test
    void clipsToImage() {
        assertEquals(new FocusBox(0, 0, 1000, 1000), ahead(0, corner(600, 500, 600), corner(-600, 500, -600)));
    }

    @Test
    void nullWithoutCorners() {
        assertNull(FocusProjector.project(0, 0, 0, 0, 0, RIGHT_ANGLE, 1000, 1000,
                Collections.<double []>emptyList(), 0));
    }

    @Test
    void nullWhenACornerIsBehindTheCamera() {
        assertNull(ahead(0, corner(0, 500, 0), corner(0, -10, 0)));
    }

    @Test
    void nullWhenClippedBoxIsEmpty() {
        assertNull(ahead(0, corner(600, 500, 0), corner(700, 500, 10)));
    }

    @Test
    void invertedBoxIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new FocusBox(10, 0, 10, 5));
        assertThrows(IllegalArgumentException.class, () -> new FocusBox(0, 5, 10, 4));
    }

    @Test
    void fitsWithinImage() {
        assertTrue(new FocusBox(0, 0, 1920, 1080).fits(1920, 1080));
        assertFalse(new FocusBox(0, 0, 1921, 1080).fits(1920, 1080));
        assertFalse(new FocusBox(-1, 0, 100, 100).fits(1920, 1080));
    }
}
