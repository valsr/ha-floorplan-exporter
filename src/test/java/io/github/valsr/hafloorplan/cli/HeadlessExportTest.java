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
package io.github.valsr.hafloorplan.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.eteks.sweethome3d.io.HomeFileRecorder;

import io.github.valsr.hafloorplan.engine.FakeRenderBackend;
import io.github.valsr.hafloorplan.plan.Instructions;
import io.github.valsr.hafloorplan.sample.SampleHomeFactory;

class HeadlessExportTest {
    private static final String REST = "\"floors\":\"*\","
            + "\"dates\":{\"start\":\"2026-06-21\",\"end\":\"2026-06-21\",\"intervalDays\":1},"
            + "\"times\":{\"start\":\"12:00\",\"end\":\"12:00\",\"intervalMinutes\":60},"
            + "\"width\":8,\"height\":6";
    private static final InputStream NO_INPUT = new ByteArrayInputStream(new byte [0]);

    @TempDir
    Path tempDir;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();
    private FakeRenderBackend backend;

    @BeforeEach
    void setUp() {
        this.backend = new FakeRenderBackend();
    }

    private File write(String path, String text) throws Exception {
        File file = this.tempDir.resolve(path).toFile();
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private File writeHome() throws Exception {
        File home = this.tempDir.resolve("h.sh3d").toFile();
        new HomeFileRecorder().writeHome(SampleHomeFactory.create(), home.getPath());
        return home;
    }

    private int run(String... args) {
        return HeadlessExport.run(args, NO_INPUT, new PrintStream(this.out), new PrintStream(this.err), this.backend);
    }

    private File path(String path) {
        return this.tempDir.resolve(path).toFile();
    }

    @Test
    void relativePathsResolveAgainstInstructionsFile() throws Exception {
        File job = write("a/job.json", "{\"version\":1,\"home\":\"../h.sh3d\",\"output\":\"out\"," + REST + "}");
        Instructions instructions = HeadlessExport.load(new String [] {job.getPath()}, NO_INPUT, path("elsewhere"));
        assertEquals(path("h.sh3d").getPath(), instructions.getHome());
        assertEquals(path("a/out").getPath(), instructions.getOutput());
    }

    @Test
    void commandLineOverridesAndUsesWorkingDir() throws Exception {
        File job = write("a/job.json", "{\"version\":1,\"home\":\"../h.sh3d\",\"output\":\"out\"," + REST + "}");
        Instructions instructions = HeadlessExport.load(
                new String [] {job.getPath(), "--home", "rel.sh3d", "--output", "o"}, NO_INPUT, path("w"));
        assertEquals(path("w/rel.sh3d").getPath(), instructions.getHome());
        assertEquals(path("w/o").getPath(), instructions.getOutput());
    }

    @Test
    void readsStandardInput() throws Exception {
        String json = "{\"version\":1,\"home\":\"h.sh3d\",\"output\":\"out\"," + REST + "}";
        Instructions instructions = HeadlessExport.load(new String [] {"-"},
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), path("w"));
        assertEquals(path("w/h.sh3d").getPath(), instructions.getHome());
        assertEquals(path("w/out").getPath(), instructions.getOutput());
        assertEquals(8, instructions.getWidth());
    }

    @Test
    void badArgumentsExitWith2() throws Exception {
        assertEquals(2, run());
        assertTrue(this.err.toString().contains("Usage"), this.err.toString());
        assertEquals(2, run(path("missing.json").getPath()));
        assertEquals(2, run(write("bad.json", "{\"version\":1,").getPath()));
        assertEquals(2, run(write("nohome.json", "{\"version\":1,\"output\":\"out\"," + REST + "}").getPath()));
        assertEquals(2, run(write("nooutput.json", "{\"version\":1,\"home\":\"h.sh3d\"," + REST + "}").getPath()));
        assertEquals(2, run(write("j.json", "{\"version\":1,\"output\":\"out\"," + REST + "}").getPath(), "--bogus", "x"));
        assertEquals(2, run(write("nofile.json", "{\"version\":1,\"home\":\"gone.sh3d\",\"output\":\"out\"," + REST + "}").getPath()));
        assertTrue(this.backend.sessions.isEmpty());
    }

    @Test
    void unknownReferenceExitsWith2BeforeRendering() throws Exception {
        writeHome();
        File job = write("job.json", "{\"version\":1,\"home\":\"h.sh3d\",\"output\":\"out\",\"lights\":[\"Chandelier\"]," + REST + "}");
        assertEquals(2, run(job.getPath()));
        assertTrue(this.err.toString().contains("Unknown light \"Chandelier\""), this.err.toString());
        assertTrue(this.backend.sessions.isEmpty());
    }

    @Test
    void renderFailureExitsWith1() throws Exception {
        writeHome();
        this.backend.failOnRender = 0;
        File job = write("job.json", "{\"version\":1,\"home\":\"h.sh3d\",\"output\":\"out\"," + REST + "}");
        assertEquals(1, run(job.getPath()));
        assertTrue(this.err.toString().contains("Fake render failure"), this.err.toString());
    }

    @Test
    void validRunPrintsOneLinePerJob() throws Exception {
        writeHome();
        File job = write("job.json", "{\"version\":1,\"home\":\"h.sh3d\",\"output\":\"out\","
                + "\"renderer\":\"fake\"," + REST + "}");
        assertEquals(0, run(job.getPath()), this.err.toString());

        String [] lines = this.out.toString().trim().split("\\R");
        // Per floor: 1 base, 1 night base, 1 light
        assertEquals(6, lines.length, this.out.toString());
        assertEquals("[1/6] ground-floor/base/2026-06-21_1200.png", lines [0]);
        assertEquals("[6/6] first-floor/lights/desk.png", lines [5]);
        assertTrue(path("out/manifest.json").isFile());
        assertTrue(new String(Files.readAllBytes(path("out/manifest.json").toPath()), StandardCharsets.UTF_8)
                .contains("\"generator\": \"ha-floorplan-exporter 0.1.0\""));
    }

    @Test
    void missingDisplayIsExplained() throws Exception {
        writeHome();
        this.backend = new FakeRenderBackend() {
            @Override
            public java.util.List<io.github.valsr.hafloorplan.plan.HomeSummary.Renderer> availableRenderers() {
                // What Java 3D throws when its classes load without a display
                throw new ExceptionInInitializerError(new java.awt.HeadlessException());
            }
        };
        File job = write("job.json", "{\"version\":1,\"home\":\"h.sh3d\",\"output\":\"out\"," + REST + "}");
        assertEquals(1, run(job.getPath()));
        assertTrue(this.err.toString().contains("display"), this.err.toString());
    }

    @Test
    void interruptedExportExitsWith1() throws Exception {
        writeHome();
        // What the shutdown hook does when the process is interrupted
        this.backend.onRender = () -> HeadlessExport.abortRunningExport();
        File job = write("job.json", "{\"version\":1,\"home\":\"h.sh3d\",\"output\":\"out\"," + REST + "}");
        assertEquals(1, run(job.getPath()));
        assertTrue(this.err.toString().contains("cancelled"), this.err.toString());
        assertEquals(1, this.backend.sessions.get(0).closeCount);
        assertFalse(path("out/manifest.json").exists());
    }

    @Test
    void loadRejectsMissingOptionValue() {
        assertThrows(IllegalArgumentException.class,
                () -> HeadlessExport.load(new String [] {"-", "--home"}, NO_INPUT, path("w")));
    }
}
