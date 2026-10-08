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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class ExportPlannerTest {
    /** Two floors, two lights called Kitchen lamp on the ground floor, Desk on the first one and a stray light. */
    static HomeSummary summary() {
        return new HomeSummary(
                Arrays.asList(new HomeSummary.Floor("f1", "Ground floor"), new HomeSummary.Floor("f2", "First floor")),
                Arrays.asList(new HomeSummary.Light("k1", "Kitchen lamp", "f1"),
                        new HomeSummary.Light("k2", "Kitchen lamp", "f1"),
                        new HomeSummary.Light("d1", "Desk", "f2"),
                        new HomeSummary.Light("s1", "Stray", null)),
                Arrays.asList(new HomeSummary.Camera("c1", "Top ground")),
                Arrays.asList(new HomeSummary.Renderer("sh3d.gpurenderer.BlenderRenderer", "Blender Cycles (GPU)")));
    }

    /** Both floors, 2 dates x 3 times. */
    static ExportConfig.Builder config() {
        return ExportConfig.builder()
                .floors(Arrays.asList(new ExportConfig.Floor("f1", "c1"), new ExportConfig.Floor("f2", null)))
                .dates(new DateSchedule(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"), 30))
                .times(new TimeSchedule(LocalTime.parse("00:00"), LocalTime.parse("08:00"), 240))
                .rendererClassName("sh3d.gpurenderer.BlenderRenderer")
                .outputDir(new File("/out"));
    }

    private static List<String> kinds(List<RenderJob> jobs) {
        List<String> kinds = new ArrayList<String>();
        for (RenderJob job : jobs) {
            kinds.add(job.floorId + ":" + job.kind);
        }
        return kinds;
    }

    @Test
    void ordersJobs() {
        List<RenderJob> jobs = ExportPlanner.plan(config().lightIds(Arrays.asList("k1", "k2")).build(), summary()).getJobs();

        List<String> expected = new ArrayList<String>(Collections.nCopies(6, "f1:BASE"));
        expected.add("f1:NIGHT_BASE");
        expected.addAll(Collections.nCopies(2, "f1:LIGHT"));
        expected.addAll(Collections.nCopies(6, "f2:BASE"));
        assertEquals(expected, kinds(jobs));
        assertEquals(15, jobs.size());

        // Date major, time minor
        assertEquals(LocalDate.parse("2026-01-01"), jobs.get(1).date);
        assertEquals(LocalTime.parse("04:00"), jobs.get(1).time);
        assertEquals(LocalDate.parse("2026-01-31"), jobs.get(3).date);
        assertEquals(LocalTime.parse("00:00"), jobs.get(3).time);
        assertNull(jobs.get(6).date);
        assertEquals("k1", jobs.get(7).lightId);
        assertEquals("k2", jobs.get(8).lightId);
    }

    @Test
    void paths() {
        ExportPlan plan = ExportPlanner.plan(config().lightIds(Arrays.asList("k1", "k2")).build(), summary());
        List<RenderJob> jobs = plan.getJobs();
        assertEquals("ground-floor/base/2026-01-01_0000.png", jobs.get(0).path);
        assertEquals("ground-floor/base/2026-01-31_0800.png", jobs.get(5).path);
        assertEquals("ground-floor/night.png", jobs.get(6).path);
        assertEquals("ground-floor/lights/kitchen-lamp.png", jobs.get(7).path);
        assertEquals("ground-floor/lights/kitchen-lamp-2.png", jobs.get(8).path);
        assertEquals("first-floor", plan.floorSlug("f2"));
        assertEquals("kitchen-lamp-2", plan.lightSlug("k2"));
    }

    @Test
    void skipsLightsOffSelectedFloors() {
        ExportPlan plan = ExportPlanner.plan(config()
                .floors(Arrays.asList(new ExportConfig.Floor("f1", null)))
                .lightIds(Arrays.asList("d1", "s1")).build(), summary());
        assertEquals(Collections.nCopies(6, "f1:BASE"), kinds(plan.getJobs()));
        assertEquals(Arrays.asList("d1", "s1"), plan.getSkippedLightIds());
    }

    @Test
    void skipsLightsTurnedOff() {
        HomeSummary summary = new HomeSummary(
                Arrays.asList(new HomeSummary.Floor("f1", "Ground floor")),
                Arrays.asList(new HomeSummary.Light("on", "Lamp", "f1", 0.5f), new HomeSummary.Light("off", "Lamp", "f1", 0f)),
                Collections.<HomeSummary.Camera>emptyList(), summary().getRenderers());
        ExportPlan plan = ExportPlanner.plan(config()
                .floors(Arrays.asList(new ExportConfig.Floor("f1", null)))
                .lightIds(Arrays.asList("off", "on")).build(), summary);

        // A light without power adds nothing to the scene: no overlay to render
        List<RenderJob> jobs = plan.getJobs();
        assertEquals(8, jobs.size());
        assertEquals("on", jobs.get(7).lightId);
        assertEquals("ground-floor/lights/lamp.png", jobs.get(7).path);
        assertEquals(Arrays.asList("off"), plan.getSkippedLightIds());
    }

    @Test
    void onlyLightsTurnedOffMeansNoNightBase() {
        HomeSummary summary = new HomeSummary(
                Arrays.asList(new HomeSummary.Floor("f1", "Ground floor")),
                Arrays.asList(new HomeSummary.Light("off", "Lamp", "f1", 0f)),
                Collections.<HomeSummary.Camera>emptyList(), summary().getRenderers());
        ExportPlan plan = ExportPlanner.plan(config()
                .floors(Arrays.asList(new ExportConfig.Floor("f1", null)))
                .lightIds(Arrays.asList("off")).build(), summary);
        assertEquals(Collections.nCopies(6, "f1:BASE"), kinds(plan.getJobs()));
    }

    @Test
    void noLightsNoNightBase() {
        ExportPlan plan = ExportPlanner.plan(config().build(), summary());
        assertEquals(12, plan.getJobs().size());
        for (RenderJob job : plan.getJobs()) {
            assertEquals(RenderJob.Kind.BASE, job.kind);
        }
        assertTrue(plan.getSkippedLightIds().isEmpty());
    }
}
