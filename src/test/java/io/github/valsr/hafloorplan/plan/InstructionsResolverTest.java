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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class InstructionsResolverTest {
    static final String SUNFLOW = "com.eteks.sweethome3d.j3d.PhotoRenderer";
    static final String BLENDER = "sh3d.gpurenderer.BlenderRenderer";
    private static final File BASE = new File("/data/job");

    /** Two floors, two lights called Lamp on the ground floor and Desk on the first one. */
    static HomeSummary summary() {
        return new HomeSummary(
                Arrays.asList(new HomeSummary.Floor("f1", "Ground"), new HomeSummary.Floor("f2", "First")),
                Arrays.asList(new HomeSummary.Light("l1", "Lamp", "f1"), new HomeSummary.Light("l2", "Lamp", "f1"),
                        new HomeSummary.Light("l3", "Desk", "f2")),
                Arrays.asList(new HomeSummary.Camera("c1", "Top ground"), new HomeSummary.Camera("c2", "Top first")),
                Arrays.asList(new HomeSummary.Renderer(SUNFLOW, "SunFlow"),
                        new HomeSummary.Renderer(BLENDER, "Blender Cycles (GPU)", true, true)));
    }

    static Instructions.Builder builder() {
        return Instructions.builder()
                .floors(Collections.singletonList(new Instructions.Floor(Ref.of("Ground"), null)))
                .dates(new DateSchedule(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-02"), 1))
                .times(new TimeSchedule(LocalTime.parse("00:00"), LocalTime.parse("12:00"), 360))
                .lights(Collections.<Ref>emptyList())
                .output("/out");
    }

    private static InstructionsResolver.Resolution resolve(Instructions.Builder builder) {
        return InstructionsResolver.resolve(builder.build(), summary(), BASE);
    }

    private static List<String> lightIds(Ref... lights) {
        InstructionsResolver.Resolution resolution = resolve(builder().lights(Arrays.asList(lights)));
        assertTrue(resolution.getProblems().isEmpty(), resolution.getProblems().toString());
        return resolution.getConfig().getLightIds();
    }

    @Test
    void resolvesById() {
        assertEquals(Arrays.asList("l2"), lightIds(new Ref("l2", null)));
        assertEquals(Arrays.asList("l3"), lightIds(Ref.of("l3")));
    }

    @Test
    void resolvesByName() {
        assertEquals(Arrays.asList("l3"), lightIds(Ref.of("Desk")));
        InstructionsResolver.Resolution resolution = resolve(builder().floors(Collections.singletonList(
                new Instructions.Floor(Ref.of("First"), Ref.of("Top first")))));
        assertTrue(resolution.getProblems().isEmpty());
        assertEquals(Arrays.asList(new ExportConfig.Floor("f2", "c2")), resolution.getConfig().getFloors());
    }

    @Test
    void fallsBackToNameWhenIdUnknown() {
        assertEquals(Arrays.asList("l3"), lightIds(new Ref("gone", "Desk")));
    }

    @Test
    void idWinsOverName() {
        assertEquals(Arrays.asList("l1"), lightIds(new Ref("l1", "Desk")));
    }

    @Test
    void ambiguousNameIsAProblem() {
        InstructionsResolver.Resolution resolution = resolve(builder().lights(Arrays.asList(Ref.of("Lamp"))));
        assertTrue(resolution.getConfig().getLightIds().isEmpty());
        assertEquals(1, resolution.getProblems().size());
        assertTrue(resolution.getProblems().get(0).contains("Ambiguous"), resolution.getProblems().get(0));
    }

    @Test
    void unknownReferencesAreDroppedAndListed() {
        InstructionsResolver.Resolution resolution = resolve(builder()
                .floors(Arrays.asList(new Instructions.Floor(Ref.of("Attic"), null),
                        new Instructions.Floor(Ref.of("Ground"), Ref.of("Nowhere"))))
                .lights(Arrays.asList(Ref.of("Chandelier"), Ref.of("Desk"))));
        assertEquals(3, resolution.getProblems().size(), resolution.getProblems().toString());
        assertTrue(resolution.getProblems().contains("Unknown light \"Chandelier\""), resolution.getProblems().toString());
        assertEquals(Arrays.asList(new ExportConfig.Floor("f1", null)), resolution.getConfig().getFloors());
        assertEquals(Arrays.asList("l3"), resolution.getConfig().getLightIds());
    }

    @Test
    void floorListedTwiceIsKeptOnce() {
        InstructionsResolver.Resolution resolution = resolve(builder().floors(Arrays.asList(
                new Instructions.Floor(Ref.of("Ground"), null), new Instructions.Floor(Ref.of("f1"), Ref.of("c1")))));
        assertEquals(1, resolution.getProblems().size());
        assertEquals(Arrays.asList(new ExportConfig.Floor("f1", null)), resolution.getConfig().getFloors());
    }

    @Test
    void wildcards() {
        InstructionsResolver.Resolution resolution = resolve(builder().allFloors().allLights());
        assertTrue(resolution.getProblems().isEmpty());
        assertEquals(Arrays.asList(new ExportConfig.Floor("f1", null), new ExportConfig.Floor("f2", null)),
                resolution.getConfig().getFloors());
        assertEquals(Arrays.asList("l1", "l2", "l3"), resolution.getConfig().getLightIds());
    }

    @Test
    void rendererMatching() {
        for (String name : new String[] {"BlenderRenderer", "blender cycles (gpu)", BLENDER}) {
            InstructionsResolver.Resolution resolution = resolve(builder().renderer(name));
            assertTrue(resolution.getProblems().isEmpty(), name);
            assertEquals(BLENDER, resolution.getConfig().getRendererClassName(), name);
        }
        assertEquals(SUNFLOW, resolve(builder()).getConfig().getRendererClassName());

        InstructionsResolver.Resolution unknown = resolve(builder().renderer("Nope"));
        assertEquals(1, unknown.getProblems().size());
        assertEquals(SUNFLOW, unknown.getConfig().getRendererClassName());
    }

    @Test
    void capLightNeedsARendererAbleToCap() {
        InstructionsResolver.Resolution blender = resolve(builder().renderer(BLENDER).capLight(LightCap.SUN));
        assertTrue(blender.getProblems().isEmpty());
        assertEquals(LightCap.SUN, blender.getConfig().getCapLight());

        InstructionsResolver.Resolution sunFlow = resolve(builder().renderer(SUNFLOW).capLight(LightCap.ALL));
        assertEquals(1, sunFlow.getProblems().size());
        assertTrue(sunFlow.getProblems().get(0).contains("capLight needs a renderer"), sunFlow.getProblems().get(0));
        assertTrue(sunFlow.getProblems().get(0).contains("SunFlow"), sunFlow.getProblems().get(0));
        assertEquals(LightCap.OFF, sunFlow.getConfig().getCapLight());

        assertEquals(LightCap.SUN,
                InstructionsResolver.toInstructions(blender.getConfig(), summary(), null).getCapLight());
    }

    @Test
    void exposureNeedsARendererWithExposure() {
        InstructionsResolver.Resolution blender = resolve(builder().renderer(BLENDER).exposure(2));
        assertTrue(blender.getProblems().isEmpty());
        assertEquals(2.0, blender.getConfig().getExposure());
        assertEquals(2.0, InstructionsResolver.toInstructions(blender.getConfig(), summary(), null).getExposure());

        InstructionsResolver.Resolution sunFlow = resolve(builder().renderer(SUNFLOW).exposure(2));
        assertEquals(1, sunFlow.getProblems().size());
        assertTrue(sunFlow.getProblems().get(0).contains("exposure needs a renderer"), sunFlow.getProblems().get(0));
        assertEquals(0.0, sunFlow.getConfig().getExposure());

        assertTrue(resolve(builder().renderer(SUNFLOW).exposure(0)).getProblems().isEmpty());
    }

    @Test
    void noRendererAvailableIsAProblem() {
        HomeSummary noRenderer = new HomeSummary(summary().getFloors(), summary().getLights(),
                summary().getCameras(), Collections.<HomeSummary.Renderer>emptyList());
        InstructionsResolver.Resolution resolution = InstructionsResolver.resolve(builder().build(), noRenderer, BASE);
        assertEquals(Arrays.asList("No renderer available"), resolution.getProblems());
        assertNull(resolution.getConfig().getRendererClassName());
    }

    @Test
    void relativeOutputUsesBaseDir() {
        assertEquals(new File("/data/job/out"), resolve(builder().output("out")).getConfig().getOutputDir());
        assertEquals(new File("/abs/out"), resolve(builder().output("/abs/out")).getConfig().getOutputDir());
        assertNull(resolve(builder().output(null)).getConfig().getOutputDir());
    }

    @Test
    void toInstructionsRoundTrips() {
        ExportConfig config = resolve(builder()
                .floors(Arrays.asList(new Instructions.Floor(Ref.of("Ground"), Ref.of("Top ground")),
                        new Instructions.Floor(Ref.of("First"), null)))
                .lights(Arrays.asList(new Ref("l2", null), Ref.of("Desk")))
                .width(640).height(480).renderer(BLENDER).quality(Quality.HIGH)
                .hideCeilings(false).isolateLevel(true).noiseThreshold(9)).getConfig();

        Instructions instructions = InstructionsResolver.toInstructions(config, summary(), "/h.sh3d");
        assertEquals("/h.sh3d", instructions.getHome());
        assertEquals(new Ref("l2", "Lamp"), instructions.getLights().get(0));

        InstructionsResolver.Resolution again = InstructionsResolver.resolve(instructions, summary(), new File("/other"));
        assertTrue(again.getProblems().isEmpty(), again.getProblems().toString());
        ExportConfig copy = again.getConfig();
        assertEquals(config.getFloors(), copy.getFloors());
        assertEquals(config.getLightIds(), copy.getLightIds());
        assertEquals(config.getDates(), copy.getDates());
        assertEquals(config.getTimes(), copy.getTimes());
        assertEquals(640, copy.getWidth());
        assertEquals(480, copy.getHeight());
        assertEquals(BLENDER, copy.getRendererClassName());
        assertEquals(Quality.HIGH, copy.getQuality());
        assertEquals(false, copy.isHideCeilings());
        assertEquals(true, copy.isIsolateLevel());
        assertEquals(9, copy.getNoiseThreshold());
        assertEquals(new File("/out"), copy.getOutputDir());
    }
}
