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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class InstructionsJsonTest {
    static final String SPEC_EXAMPLE = "{\n"
            + "  \"version\": 1,\n"
            + "  \"home\": \"house.sh3d\",\n"
            + "  \"output\": \"out\",\n"
            + "  \"floors\": [\n"
            + "    {\n"
            + "      \"level\": {\"id\": \"level-1\", \"name\": \"Ground floor\"},\n"
            + "      \"camera\": {\"id\": \"camera-1\", \"name\": \"Top ground\"}\n"
            + "    },\n"
            + "    {\"level\": \"First floor\", \"camera\": \"Top first\"}\n"
            + "  ],\n"
            + "  \"dates\": {\"start\": \"2026-01-01\", \"end\": \"2026-12-31\", \"intervalDays\": 30},\n"
            + "  \"times\": {\"start\": \"00:00\", \"end\": \"23:00\", \"intervalMinutes\": 240},\n"
            + "  \"lights\": \"*\",\n"
            + "  \"width\": 1920,\n"
            + "  \"height\": 1080,\n"
            + "  \"renderer\": \"sh3d.gpurenderer.BlenderRenderer\",\n"
            + "  \"quality\": \"LOW\",\n"
            + "  \"hideCeilings\": true,\n"
            + "  \"isolateLevel\": false,\n"
            + "  \"noiseThreshold\": 6\n"
            + "}\n";

    private static final String MINIMAL_REST = "\"floors\":\"*\","
            + "\"dates\":{\"start\":\"2026-01-01\",\"end\":\"2026-01-01\",\"intervalDays\":1},"
            + "\"times\":{\"start\":\"12:00\",\"end\":\"12:00\",\"intervalMinutes\":60}";

    /** Returns minimal valid instructions with <code>extra</code> members added. */
    private static String minimal(String extra) {
        return "{\"version\":1," + MINIMAL_REST + (extra.isEmpty() ? "" : "," + extra) + "}";
    }

    private static List<String> problems(String json) {
        return assertThrows(InstructionsException.class, () -> InstructionsJson.parse(json), json).getProblems();
    }

    private static void assertOneProblemNaming(String key, String json) {
        List<String> problems = problems(json);
        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.get(0).contains(key), problems.get(0));
    }

    @Test
    void parsesSpecExample() {
        Instructions instructions = InstructionsJson.parse(SPEC_EXAMPLE);

        assertEquals("house.sh3d", instructions.getHome());
        assertEquals("out", instructions.getOutput());
        assertFalse(instructions.isAllFloors());
        assertEquals(2, instructions.getFloors().size());
        assertEquals(new Ref("level-1", "Ground floor"), instructions.getFloors().get(0).level);
        assertEquals(new Ref("camera-1", "Top ground"), instructions.getFloors().get(0).camera);
        assertEquals(Ref.of("First floor"), instructions.getFloors().get(1).level);
        assertEquals(Ref.of("Top first"), instructions.getFloors().get(1).camera);
        assertTrue(instructions.isAllLights());
        assertEquals(new DateSchedule(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"), 30),
                instructions.getDates());
        assertEquals(new TimeSchedule(LocalTime.parse("00:00"), LocalTime.parse("23:00"), 240),
                instructions.getTimes());
        assertEquals("sh3d.gpurenderer.BlenderRenderer", instructions.getRenderer());
    }

    @Test
    void appliesDefaults() {
        Instructions instructions = InstructionsJson.parse(minimal(""));

        assertTrue(instructions.isAllFloors());
        assertTrue(instructions.isAllLights());
        assertNull(instructions.getHome());
        assertNull(instructions.getOutput());
        assertNull(instructions.getRenderer());
        assertEquals(1920, instructions.getWidth());
        assertEquals(1080, instructions.getHeight());
        assertEquals(Quality.LOW, instructions.getQuality());
        assertTrue(instructions.isHideCeilings());
        assertFalse(instructions.isIsolateLevel());
        assertEquals(6, instructions.getNoiseThreshold());
    }

    @Test
    void roundTrips() {
        Instructions example = InstructionsJson.parse(SPEC_EXAMPLE);
        assertEquals(example, InstructionsJson.parse(InstructionsJson.write(example)));

        Instructions built = Instructions.builder()
                .floors(Collections.singletonList(new Instructions.Floor(new Ref("l1", null), null)))
                .dates(new DateSchedule(LocalDate.parse("2026-06-21"), LocalDate.parse("2026-06-21"), 1))
                .times(new TimeSchedule(LocalTime.parse("08:30"), LocalTime.parse("20:30"), 90))
                .lights(Collections.<Ref>emptyList())
                .width(320).height(240).quality(Quality.HIGH)
                .hideCeilings(false).isolateLevel(true).noiseThreshold(0)
                .build();
        String json = InstructionsJson.write(built);
        assertEquals(built, InstructionsJson.parse(json));
        assertFalse(json.contains("\"home\""), json);
        assertFalse(json.contains("\"camera\""), json);
        assertFalse(json.contains("\"renderer\""), json);
    }

    @Test
    void writesWildcardsAndReferenceForms() {
        Instructions instructions = Instructions.builder()
                .allFloors()
                .dates(new DateSchedule(LocalDate.parse("2026-06-21"), LocalDate.parse("2026-06-21"), 1))
                .times(new TimeSchedule(LocalTime.parse("08:30"), LocalTime.parse("20:30"), 90))
                .lights(Arrays.asList(Ref.of("Lamp"), new Ref("l2", "Desk")))
                .build();
        String json = InstructionsJson.write(instructions);
        assertTrue(json.contains("\"floors\": \"*\""), json);
        assertTrue(json.contains("\"Lamp\""), json);
        assertTrue(json.contains("\"id\": \"l2\""), json);
        assertEquals(instructions, InstructionsJson.parse(json));
    }

    @Test
    void capLight() {
        assertEquals(LightCap.OFF, InstructionsJson.parse(minimal("")).getCapLight());
        assertEquals(LightCap.SUN, InstructionsJson.parse(minimal("\"capLight\":\"sun\"")).getCapLight());
        Instructions all = InstructionsJson.parse(minimal("\"capLight\":\"all\""));
        assertEquals(LightCap.ALL, all.getCapLight());
        assertTrue(InstructionsJson.write(all).contains("\"capLight\": \"all\""));
        assertEquals(all, InstructionsJson.parse(InstructionsJson.write(all)));
        assertFalse(all.equals(InstructionsJson.parse(minimal(""))));
        assertOneProblemNaming("capLight", minimal("\"capLight\":true"));
        assertOneProblemNaming("capLight", minimal("\"capLight\":\"moon\""));
    }

    @Test
    void exposure() {
        assertEquals(0.0, InstructionsJson.parse(minimal("")).getExposure());
        Instructions brighter = InstructionsJson.parse(minimal("\"exposure\":1.5"));
        assertEquals(1.5, brighter.getExposure());
        assertTrue(InstructionsJson.write(brighter).contains("\"exposure\": 1.5"));
        assertEquals(brighter, InstructionsJson.parse(InstructionsJson.write(brighter)));
        assertFalse(brighter.equals(InstructionsJson.parse(minimal(""))));
        assertEquals(-2.0, InstructionsJson.parse(minimal("\"exposure\":-2")).getExposure());
        assertOneProblemNaming("exposure", minimal("\"exposure\":\"bright\""));
        assertOneProblemNaming("exposure", minimal("\"exposure\":50"));
    }

    @Test
    void reportsAllProblemsTogether() {
        List<String> problems = problems("{\"version\":2,\"floors\":\"*\",\"isolateLevels\":true,\"width\":\"wide\","
                + "\"times\":{\"start\":\"12:00\",\"end\":\"12:00\",\"intervalMinutes\":60}}");
        assertEquals(4, problems.size(), problems.toString());
        for (String key : new String[] {"version", "isolateLevels", "width", "dates"}) {
            assertTrue(problems.toString().contains(key), key + " missing in " + problems);
        }
    }

    @Test
    void rejectsBadNumbers() {
        assertOneProblemNaming("width", minimal("\"width\":0"));
        assertOneProblemNaming("height", minimal("\"height\":-1"));
        assertOneProblemNaming("noiseThreshold", minimal("\"noiseThreshold\":300"));
        assertOneProblemNaming("width", minimal("\"width\":1920.5"));
        assertOneProblemNaming("times", "{\"version\":1,\"floors\":\"*\","
                + "\"dates\":{\"start\":\"2026-01-01\",\"end\":\"2026-01-01\",\"intervalDays\":1},"
                + "\"times\":{\"start\":\"12:00\",\"end\":\"12:00\",\"intervalMinutes\":0}}");
        assertOneProblemNaming("dates", "{\"version\":1,\"floors\":\"*\","
                + "\"dates\":{\"start\":\"2026-01-02\",\"end\":\"2026-01-01\",\"intervalDays\":1},"
                + "\"times\":{\"start\":\"12:00\",\"end\":\"12:00\",\"intervalMinutes\":60}}");
    }

    @Test
    void rejectsBadShapes() {
        String datesAndTimes = "\"dates\":{\"start\":\"2026-01-01\",\"end\":\"2026-01-01\",\"intervalDays\":1},"
                + "\"times\":{\"start\":\"12:00\",\"end\":\"12:00\",\"intervalMinutes\":60}";
        assertOneProblemNaming("floors", "{\"version\":1,\"floors\":[]," + datesAndTimes + "}");
        assertOneProblemNaming("level", "{\"version\":1,\"floors\":[{\"camera\":\"c\"}]," + datesAndTimes + "}");
        assertOneProblemNaming("level", "{\"version\":1,\"floors\":[{\"level\":{}}]," + datesAndTimes + "}");
        assertOneProblemNaming("lights", minimal("\"lights\":[{\"id\":1}]"));
        assertOneProblemNaming("quality", minimal("\"quality\":\"MEDIUM\""));
        assertOneProblemNaming("dates", "{\"version\":1,\"floors\":\"*\","
                + "\"dates\":{\"start\":\"01/02/2026\",\"end\":\"2026-01-01\",\"intervalDays\":1},"
                + "\"times\":{\"start\":\"12:00\",\"end\":\"12:00\",\"intervalMinutes\":60}}");
        assertEquals(1, problems("[]").size());
    }

    @Test
    void wrapsSyntaxErrors() {
        List<String> problems = problems("{\"version\":1,");
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("line 1"), problems.get(0));
    }
}
