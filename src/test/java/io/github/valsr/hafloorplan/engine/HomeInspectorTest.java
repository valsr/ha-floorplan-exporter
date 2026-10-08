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

import org.junit.jupiter.api.Test;

import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeLight;

import io.github.valsr.hafloorplan.plan.HomeSummary;

class HomeInspectorTest {
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
