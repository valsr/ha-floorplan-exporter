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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

@SuppressWarnings("unchecked")
class ManifestWriterTest {
    private static Map<String, Object> manifest(ExportConfig config, HomeSummary summary, boolean night) {
        ExportPlan plan = ExportPlanner.plan(config, summary);
        return (Map<String, Object>)Json.parse(ManifestWriter.write(config, summary, plan, "ha-floorplan-exporter 0.1.0",
                night ? LocalDateTime.parse("2026-01-01T00:00") : null, night ? Double.valueOf(-58.2) : null));
    }

    @Test
    void matchesPlan() {
        ExportConfig config = ExportPlannerTest.config().lightIds(Arrays.asList("k1", "k2", "s1"))
                .isolateLevel(true).hideCeilings(false).build();
        Map<String, Object> manifest = manifest(config, ExportPlannerTest.summary(), true);

        assertEquals("ha-floorplan-exporter 0.1.0", manifest.get("generator"));
        assertEquals(1920.0, manifest.get("width"));
        assertEquals(1080.0, manifest.get("height"));
        assertEquals("sh3d.gpurenderer.BlenderRenderer", manifest.get("renderer"));
        assertEquals("Blender Cycles (GPU)", manifest.get("rendererName"));
        assertEquals("LOW", manifest.get("quality"));
        assertEquals(Boolean.FALSE, manifest.get("hideCeilings"));
        assertEquals(Boolean.TRUE, manifest.get("isolateLevel"));
        assertEquals("off", manifest.get("capLight"));
        assertEquals(Arrays.asList("2026-01-01", "2026-01-31"), manifest.get("dates"));
        assertEquals(Arrays.asList("00:00", "04:00", "08:00"), manifest.get("times"));
        assertEquals("2026-01-01T00:00", manifest.get("nightTime"));
        assertEquals(-58.2, manifest.get("nightSunElevation"));

        List<Map<String, Object>> floors = (List<Map<String, Object>>)manifest.get("floors");
        assertEquals(2, floors.size());
        Map<String, Object> ground = floors.get(0);
        assertEquals("f1", ground.get("id"));
        assertEquals("Ground floor", ground.get("name"));
        assertEquals("ground-floor", ground.get("slug"));
        assertEquals("Top ground", ground.get("camera"));
        List<Map<String, Object>> base = (List<Map<String, Object>>)ground.get("base");
        assertEquals(6, base.size());
        assertEquals("2026-01-01", base.get(1).get("date"));
        assertEquals("04:00", base.get(1).get("time"));
        assertEquals("ground-floor/base/2026-01-01_0400.png", base.get(1).get("file"));
        assertEquals("ground-floor/night.png", ground.get("night"));
        List<Map<String, Object>> lights = (List<Map<String, Object>>)ground.get("lights");
        assertEquals("k2", lights.get(1).get("id"));
        assertEquals("Kitchen lamp", lights.get(1).get("name"));
        assertEquals("kitchen-lamp-2", lights.get(1).get("slug"));
        assertEquals("ground-floor/lights/kitchen-lamp-2.png", lights.get(1).get("file"));

        Map<String, Object> first = floors.get(1);
        assertTrue(first.containsKey("camera"));
        assertNull(first.get("camera"));
        assertFalse(first.containsKey("night"));
        assertTrue(((List<Object>)first.get("lights")).isEmpty());

        List<Map<String, Object>> skipped = (List<Map<String, Object>>)manifest.get("skippedLights");
        assertEquals(1, skipped.size());
        assertEquals("s1", skipped.get(0).get("id"));
        assertEquals("Stray", skipped.get(0).get("name"));
        assertEquals("not on an exported floor", skipped.get(0).get("reason"));
    }

    @Test
    void escapesNames() {
        HomeSummary summary = new HomeSummary(
                Arrays.asList(new HomeSummary.Floor("f1", "Ground floor")),
                Arrays.asList(new HomeSummary.Light("x", "6\" \\ spot", "f1")),
                Arrays.<HomeSummary.Camera>asList(),
                ExportPlannerTest.summary().getRenderers());
        ExportConfig config = ExportPlannerTest.config().floors(Arrays.asList(new ExportConfig.Floor("f1", null)))
                .lightIds(Arrays.asList("x")).build();
        Map<String, Object> floor = ((List<Map<String, Object>>)manifest(config, summary, true).get("floors")).get(0);
        assertEquals("6\" \\ spot", ((List<Map<String, Object>>)floor.get("lights")).get(0).get("name"));
    }

    @Test
    void omitsNightWithoutLights() {
        Map<String, Object> manifest = manifest(ExportPlannerTest.config().build(), ExportPlannerTest.summary(), false);
        assertFalse(manifest.containsKey("nightTime"));
        assertFalse(manifest.containsKey("nightSunElevation"));
        assertFalse(((List<Map<String, Object>>)manifest.get("floors")).get(0).containsKey("night"));
    }
}
