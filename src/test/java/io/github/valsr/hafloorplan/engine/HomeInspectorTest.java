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
package io.github.valsr.hafloorplan.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeLight;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Wall;

import io.github.valsr.hafloorplan.plan.HomeSummary;

class HomeInspectorTest {
    /** Returns the distinct values of coordinate <code>index</code> of the corners. */
    private static Set<Double> values(List<double []> corners, int index) {
        Set<Double> values = new TreeSet<Double>();
        for (double [] corner : corners) {
            values.add(corner [index]);
        }
        return values;
    }

    private static Set<Double> set(double ... values) {
        Set<Double> set = new TreeSet<Double>();
        for (double value : values) {
            set.add(value);
        }
        return set;
    }

    private static Wall wall(Home home, Level level, Float height) {
        Wall wall = new Wall(0, 0, 400, 0, 20, 0);
        // addWall puts the wall at the selected level
        home.addWall(wall);
        wall.setLevel(level);
        wall.setHeight(height);
        return wall;
    }

    private static Level level(Home home, String name, float elevation) {
        Level level = new Level(name, elevation, 12, 250);
        home.addLevel(level);
        return level;
    }

    @Test
    void wallCornersOfRaisedLevel() {
        Home home = new Home();
        Level level = level(home, "First floor", 300);
        wall(home, level, 250f);

        List<double []> corners = HomeInspector.getWallCorners(home, level.getId());

        assertEquals(8, corners.size());
        assertEquals(set(0, 400), values(corners, 0));
        assertEquals(set(-10, 10), values(corners, 1));
        assertEquals(set(300, 550), values(corners, 2));
    }

    @Test
    void wallCornersUseHomeWallHeight() {
        Home home = new Home(260);
        Level level = level(home, "First floor", 300);
        wall(home, level, null);

        assertEquals(set(300, 560), values(HomeInspector.getWallCorners(home, level.getId()), 2));
    }

    @Test
    void slopedWallUsesHigherEnd() {
        Home home = new Home();
        Level level = level(home, "First floor", 300);
        wall(home, level, 250f).setHeightAtEnd(300f);

        assertEquals(set(300, 600), values(HomeInspector.getWallCorners(home, level.getId()), 2));
    }

    @Test
    void otherLevelsLeftOut() {
        Home home = new Home();
        Level ground = level(home, "Ground floor", 0);
        Level first = level(home, "First floor", 262);
        wall(home, ground, 250f);

        assertTrue(HomeInspector.getWallCorners(home, first.getId()).isEmpty());
        assertEquals(8, HomeInspector.getWallCorners(home, ground.getId()).size());
    }

    @Test
    void wallRisingFromALowerLevelCountsFromTheFloor() {
        Home home = new Home();
        Level ground = level(home, "Ground floor", 0);
        Level first = level(home, "First floor", 262);
        // An outer wall drawn once on the ground floor, two storeys high, and a ground floor wall under the first one
        wall(home, ground, 600f);
        wall(home, ground, 250f);

        List<double []> corners = HomeInspector.getWallCorners(home, first.getId());

        assertEquals(8, corners.size());
        assertEquals(set(262, 600), values(corners, 2));
    }

    @Test
    void homeWithoutLevels() {
        Home home = new Home();
        wall(home, null, 250f);
        Wall other = new Wall(0, 0, 0, 300, 10, 250f);
        home.addWall(other);

        List<double []> corners = HomeInspector.getWallCorners(home, HomeSummary.DEFAULT_FLOOR_ID);

        assertEquals(16, corners.size());
        assertEquals(set(0, 250), values(corners, 2));
    }
    private static HomeSummary.Light lightNamed(HomeSummary summary, String name) {
        for (HomeSummary.Light light : summary.getLights()) {
            if (name.equals(light.name)) {
                return light;
            }
        }
        return null;
    }

    @Test
    void summarizesLevelsLightsAndCameras() {
        Home home = TestHomes.twoLevels();
        HomeSummary summary = HomeInspector.summarize(home, new FakeRenderBackend());

        assertEquals(2, summary.getFloors().size());
        assertEquals("Ground floor", summary.getFloors().get(0).name);
        assertEquals(home.getLevels().get(0).getId(), summary.getFloors().get(0).id);
        assertEquals("First floor", summary.getFloors().get(1).name);
        assertEquals(3, summary.getLights().size());
        assertEquals(summary.getFloors().get(1).id, lightNamed(summary, "Desk").floorId);
        assertEquals(2, summary.getCameras().size());
        assertEquals("Top ground", summary.getCameras().get(0).name);
        assertEquals(home.getStoredCameras().get(0).getId(), summary.getCameras().get(0).id);
        assertEquals(1, summary.getRenderers().size());
        assertEquals(FakeRenderBackend.RENDERER, summary.getRenderers().get(0).className);
    }

    @Test
    void findsLightsInsideGroups() {
        HomeSummary summary = HomeInspector.summarize(TestHomes.twoLevels(), new FakeRenderBackend());
        HomeSummary.Light kitchenLamp = lightNamed(summary, "Kitchen lamp");
        assertNotNull(kitchenLamp);
        assertEquals(summary.getFloors().get(0).id, kitchenLamp.floorId);
    }

    @Test
    void recordsThePowerOfLights() {
        HomeSummary summary = HomeInspector.summarize(TestHomes.twoLevels(), new FakeRenderBackend());
        assertEquals(0.5f, lightNamed(summary, "Kitchen lamp").power);
        assertEquals(0f, lightNamed(summary, "Unlit").power);
    }

    @Test
    void levelLessLightHasNoFloor() {
        Home home = TestHomes.twoLevels();
        HomeLight stray = TestHomes.light("Stray", 0.5f);
        home.addPieceOfFurniture(stray);
        stray.setLevel(null);

        HomeSummary summary = HomeInspector.summarize(home, new FakeRenderBackend());
        assertNotNull(lightNamed(summary, "Stray"));
        assertNull(lightNamed(summary, "Stray").floorId);
    }

    @Test
    void homeWithoutLevelsHasDefaultFloor() {
        Home home = new Home();
        home.addRoom(TestHomes.room("Room", 0, 0, 500, 400));
        home.addPieceOfFurniture(TestHomes.light("Lamp", 0.5f));

        HomeSummary summary = HomeInspector.summarize(home, new FakeRenderBackend());
        assertEquals(1, summary.getFloors().size());
        assertEquals(HomeSummary.DEFAULT_FLOOR_ID, summary.getFloors().get(0).id);
        assertEquals("Home", summary.getFloors().get(0).name);
        assertEquals(HomeSummary.DEFAULT_FLOOR_ID, lightNamed(summary, "Lamp").floorId);
    }

    @Test
    void unnamedStoredCamerasAreLeftOut() {
        Home home = TestHomes.twoLevels();
        home.setStoredCameras(java.util.Arrays.asList(new Camera(0, 0, 100, 0, 0, 1), TestHomes.topCamera("Named", 500)));
        HomeSummary summary = HomeInspector.summarize(home, new FakeRenderBackend());
        assertEquals(1, summary.getCameras().size());
        assertEquals("Named", summary.getCameras().get(0).name);
    }
}
