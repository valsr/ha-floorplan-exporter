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
package io.github.valsr.hafloorplan.sample;

import java.util.Arrays;

import com.eteks.sweethome3d.io.HomeFileRecorder;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeLight;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Wall;

import io.github.valsr.hafloorplan.engine.TestHomes;

/**
 * Builds the small home used to try an export from end to end:
 * SampleHomeFactory out.sh3d
 */
public final class SampleHomeFactory {
    private SampleHomeFactory() {
    }

    /**
     * Returns a home with a 5 m x 4 m "Ground floor" and a "First floor" covering only its left half,
     * so the ground floor shows beside it from above. Each has a light ("Kitchen lamp", "Desk")
     * and a stored camera looking straight down ("Top ground", "Top first").
     */
    public static Home create() {
        Home home = new Home();
        Level ground = new Level("Ground floor", 0, 12, 250);
        Level first = new Level("First floor", 262, 12, 250);
        home.addLevel(ground);
        home.addLevel(first);

        home.setSelectedLevel(ground);
        addRoom(home, "Kitchen", 500);
        addLight(home, "Kitchen lamp", 380, 200);

        home.setSelectedLevel(first);
        addRoom(home, "Office", 250);
        addLight(home, "Desk", 125, 200);

        home.setStoredCameras(Arrays.asList(TestHomes.topCamera("Top ground", 700), TestHomes.topCamera("Top first", 962)));
        home.setSelectedLevel(ground);
        return home;
    }

    private static void addRoom(Home home, String name, float width) {
        float [][] corners = {{0, 0}, {width, 0}, {width, 400}, {0, 400}};
        for (int i = 0; i < corners.length; i++) {
            float [] start = corners [i];
            float [] end = corners [(i + 1) % corners.length];
            home.addWall(new Wall(start [0], start [1], end [0], end [1], 10, 250));
        }
        home.addRoom(TestHomes.room(name, 0, 0, width, 400));
    }

    private static void addLight(Home home, String name, float x, float y) {
        HomeLight light = TestHomes.light(name, 0.5f);
        light.setX(x);
        light.setY(y);
        home.addPieceOfFurniture(light);
    }

    public static void main(String [] args) throws Exception {
        new HomeFileRecorder().writeHome(create(), args [0]);
        System.exit(0);
    }
}
