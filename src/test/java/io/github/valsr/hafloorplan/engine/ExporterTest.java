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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeLight;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;

import io.github.valsr.hafloorplan.plan.DateSchedule;
import io.github.valsr.hafloorplan.plan.ExportConfig;
import io.github.valsr.hafloorplan.plan.ExportPlan;
import io.github.valsr.hafloorplan.plan.ExportPlanner;
import io.github.valsr.hafloorplan.plan.HomeSummary;
import io.github.valsr.hafloorplan.plan.Json;
import io.github.valsr.hafloorplan.plan.LightCap;
import io.github.valsr.hafloorplan.plan.RenderJob;
import io.github.valsr.hafloorplan.plan.TimeSchedule;

class ExporterTest {
    private static final LocalDate DATE = LocalDate.parse("2026-06-21");

    @TempDir
    Path tempDir;
    private Home home;
    private FakeRenderBackend backend;
    private HomeSummary summary;
    private File output;

    @BeforeEach
    void setUp() {
        this.home = TestHomes.twoLevels();
        this.backend = new FakeRenderBackend();
        this.summary = HomeInspector.summarize(this.home, this.backend);
        this.output = this.tempDir.resolve("out").toFile();
    }

    private String lightId(String name) {
        for (HomeSummary.Light light : this.summary.getLights()) {
            if (name.equals(light.name)) {
                return light.id;
            }
        }
        throw new IllegalArgumentException(name);
    }

    /** Both floors from their cameras, 1 date x 2 times, both lights turned on in the home. */
    private ExportConfig.Builder config() {
        return ExportConfig.builder()
                .floors(Arrays.asList(
                        new ExportConfig.Floor(this.summary.getFloors().get(0).id, this.summary.getCameras().get(0).id),
                        new ExportConfig.Floor(this.summary.getFloors().get(1).id, this.summary.getCameras().get(1).id)))
                .dates(new DateSchedule(DATE, DATE, 1))
                .times(new TimeSchedule(LocalTime.parse("00:00"), LocalTime.parse("12:00"), 720))
                .lightIds(Arrays.asList(lightId("Kitchen lamp"), lightId("Desk")))
                .width(8).height(6)
                .rendererClassName(FakeRenderBackend.RENDERER)
                .outputDir(this.output);
    }

    private Exporter exporter(ExportConfig config) {
        return new Exporter(this.home, config, this.backend, "test 1.0");
    }

    private File file(String path) {
        return new File(this.output, path);
    }

    @Test
    void writesPlannedFiles() throws Exception {
        ExportConfig config = config().build();
        assertTrue(exporter(config).run(null));

        ExportPlan plan = ExportPlanner.plan(config, this.summary);
        assertEquals(8, plan.getJobs().size());
        for (RenderJob job : plan.getJobs()) {
            BufferedImage image = ImageIO.read(file(job.path));
            assertNotNull(image, job.path);
            assertEquals(8, image.getWidth());
            assertEquals(6, image.getHeight());
        }
        Object manifest = Json.parse(new String(Files.readAllBytes(file("manifest.json").toPath()), StandardCharsets.UTF_8));
        assertEquals("test 1.0", ((Map<?, ?>)manifest).get("generator"));
        assertTrue(((Map<?, ?>)manifest).containsKey("nightSunElevation"));
    }

    @Test
    void overlayIsDifferenceAgainstNightBase() throws Exception {
        exporter(config().build()).run(null);

        BufferedImage overlay = ImageIO.read(file("ground-floor/lights/kitchen-lamp.png"));
        assertTrue(overlay.getColorModel().hasAlpha());
        int argb = overlay.getRGB(3, 3);
        assertTrue(argb >>> 24 > 0 && argb >>> 24 < 255, Integer.toHexString(argb));
        BufferedImage night = ImageIO.read(file("ground-floor/night.png"));
        assertFalse(night.getColorModel().hasAlpha());
        assertEquals(0xFF282828, night.getRGB(3, 3));
        assertFalse(ImageIO.read(file("ground-floor/base/2026-06-21_1200.png")).getColorModel().hasAlpha());
    }

    @Test
    void sessionsFollowSceneState() throws Exception {
        exporter(config().build()).run(null);

        assertEquals(4, this.backend.sessions.size());
        Map<String, Float> allOff = new LinkedHashMap<String, Float>();
        allOff.put("Kitchen lamp", 0f);
        allOff.put("Unlit", 0f);
        allOff.put("Desk", 0f);
        Map<String, Float> kitchenOn = new LinkedHashMap<String, Float>(allOff);
        kitchenOn.put("Kitchen lamp", 0.5f);
        Map<String, Float> deskOn = new LinkedHashMap<String, Float>(allOff);
        deskOn.put("Desk", 0.8f);
        List<Map<String, Float>> expectedPowers = Arrays.asList(allOff, kitchenOn, allOff, deskOn);
        int [] expectedRenders = {3, 1, 3, 1};
        for (int i = 0; i < 4; i++) {
            FakeRenderBackend.Session session = this.backend.sessions.get(i);
            assertEquals(expectedPowers.get(i), session.lightPowers, "session " + i);
            assertEquals(expectedRenders [i], session.cameraTimes.size(), "session " + i);
            assertEquals(1, session.closeCount, "session " + i);
            assertEquals(FakeRenderBackend.RENDERER, session.rendererClassName);
        }
    }

    @Test
    void baseRendersUseScheduledTimesAndFloorCamera() throws Exception {
        exporter(config().build()).run(null);

        FakeRenderBackend.Session ground = this.backend.sessions.get(0);
        assertEquals(SceneConfigurer.cameraTime(DATE, LocalTime.parse("00:00")), ground.cameraTimes.get(0));
        assertEquals(SceneConfigurer.cameraTime(DATE, LocalTime.parse("12:00")), ground.cameraTimes.get(1));
        assertEquals(1782043200000L, ground.cameraTimes.get(1));
        // Lights are rendered at the same night time as their night base
        assertEquals(ground.cameraTimes.get(2), this.backend.sessions.get(1).cameraTimes.get(0));
        assertEquals(900f, ground.cameraHeights.get(0));
        assertEquals(1200f, this.backend.sessions.get(2).cameraHeights.get(0));
    }

    @Test
    void currentViewIsUsedWithoutStoredCamera() throws Exception {
        ExportConfig config = config()
                .floors(Arrays.asList(new ExportConfig.Floor(this.summary.getFloors().get(0).id, null))).build();
        exporter(config).run(null);
        assertEquals(this.home.getCamera().getZ(), this.backend.sessions.get(0).cameraHeights.get(0));
    }

    private List<List<Boolean>> levelsVisible() {
        List<List<Boolean>> visible = new ArrayList<List<Boolean>>();
        for (FakeRenderBackend.Session session : this.backend.sessions) {
            visible.add(new ArrayList<Boolean>(session.levelsVisible.values()));
        }
        return visible;
    }

    @Test
    void levelsUpToTheFloorAreVisible() throws Exception {
        exporter(config().build()).run(null);
        assertEquals(Arrays.asList(Arrays.asList(true, false), Arrays.asList(true, false),
                Arrays.asList(true, true), Arrays.asList(true, true)), levelsVisible());
    }

    @Test
    void isolateLevelShowsOnlyTheFloor() throws Exception {
        exporter(config().isolateLevel(true).build()).run(null);
        assertEquals(Arrays.asList(Arrays.asList(true, false), Arrays.asList(true, false),
                Arrays.asList(false, true), Arrays.asList(false, true)), levelsVisible());
    }

    @Test
    void hidesCeilingsOfTheFloorOnly() throws Exception {
        exporter(config().build()).run(null);
        assertEquals(false, this.backend.sessions.get(0).ceilingsVisible.get("Kitchen"));
        assertEquals(true, this.backend.sessions.get(0).ceilingsVisible.get("Office"));
        assertEquals(true, this.backend.sessions.get(2).ceilingsVisible.get("Kitchen"));
        assertEquals(false, this.backend.sessions.get(2).ceilingsVisible.get("Office"));

        this.backend.sessions.clear();
        exporter(config().hideCeilings(false).build()).run(null);
        assertEquals(true, this.backend.sessions.get(0).ceilingsVisible.get("Kitchen"));
    }

    @Test
    void originalHomeUntouched() throws Exception {
        List<HomeLight> lights = new ArrayList<HomeLight>();
        FakeRenderBackend.addLights(this.home.getFurniture(), lights);
        Level selectedLevel = this.home.getSelectedLevel();

        exporter(config().isolateLevel(true).build()).run(null);

        assertEquals(0.5f, lights.get(0).getPower());
        assertEquals(0.8f, lights.get(2).getPower());
        for (Level level : this.home.getLevels()) {
            assertTrue(level.isVisible());
        }
        for (Room room : this.home.getRooms()) {
            assertTrue(room.isCeilingVisible());
        }
        assertTrue(selectedLevel == this.home.getSelectedLevel());
        assertFalse(this.home.isModified());
    }

    @Test
    void validationReportsEverythingBeforeRendering() throws Exception {
        File notADirectory = this.tempDir.resolve("file.txt").toFile();
        Files.write(notADirectory.toPath(), new byte [0]);
        ExportConfig config = config()
                .lightIds(Arrays.asList("no-such-light"))
                .rendererClassName("no.such.Renderer")
                .outputDir(notADirectory).build();

        ExportException ex = assertThrows(ExportException.class, () -> exporter(config).run(null));
        assertEquals(3, ex.getProblems().size(), ex.getProblems().toString());
        assertTrue(this.backend.sessions.isEmpty());
    }

    @Test
    void validationRejectsUnknownFloorCameraAndMissingOutput() {
        ExportConfig config = config()
                .floors(Arrays.asList(new ExportConfig.Floor("no-such-level", null),
                        new ExportConfig.Floor(this.summary.getFloors().get(0).id, "no-such-camera")))
                .outputDir(null).build();
        ExportException ex = assertThrows(ExportException.class, () -> exporter(config).run(null));
        assertEquals(3, ex.getProblems().size(), ex.getProblems().toString());
    }

    @Test
    void createsMissingOutputDir() throws Exception {
        this.output = this.tempDir.resolve("a/b/c").toFile();
        assertTrue(exporter(config().build()).run(null));
        assertTrue(file("manifest.json").isFile());
    }

    @Test
    void failureLeavesNoManifestAndClosesSession() {
        this.backend.failOnRender = 1;
        ExportException ex = assertThrows(ExportException.class, () -> exporter(config().build()).run(null));
        assertNotNull(ex.getCause());
        assertTrue(file("ground-floor/base/2026-06-21_0000.png").isFile());
        assertFalse(file("manifest.json").exists());
        assertEquals(1, this.backend.sessions.size());
        assertEquals(1, this.backend.sessions.get(0).closeCount);
    }

    @Test
    void cancelStopsAfterCurrentJob() throws Exception {
        Exporter exporter = exporter(config().build());
        this.backend.onRender = exporter::cancel;

        assertFalse(exporter.run(null));

        assertEquals(1, this.backend.sessions.size());
        assertTrue(this.backend.sessions.get(0).stopped);
        assertEquals(1, this.backend.sessions.get(0).cameraTimes.size());
        assertEquals(1, this.backend.sessions.get(0).closeCount);
        assertFalse(file("manifest.json").exists());
    }

    @Test
    void failedReExportRemovesThePreviousManifest() throws Exception {
        assertTrue(exporter(config().build()).run(null));
        assertTrue(file("manifest.json").isFile());

        this.backend.failOnRender = this.backend.sessions.get(0).cameraTimes.size() + 5 + 1;
        assertThrows(ExportException.class, () -> exporter(config().build()).run(null));
        // A manifest marks a complete export, and the folder now mixes images of two runs
        assertFalse(file("manifest.json").exists());
    }

    @Test
    void failureWhileCancellingIsACancel() throws Exception {
        Exporter exporter = exporter(config().build());
        this.backend.onRender = exporter::cancel;
        // Some renderers throw an exception when they're stopped
        this.backend.failOnRender = 0;
        assertFalse(exporter.run(null));
    }

    @Test
    void abortStopsAndClosesTheOpenSession() throws Exception {
        Exporter exporter = exporter(config().build());
        this.backend.onRender = exporter::abort;

        assertFalse(exporter.run(null));

        assertEquals(1, this.backend.sessions.size());
        assertTrue(this.backend.sessions.get(0).stopped);
        assertEquals(1, this.backend.sessions.get(0).closeCount);
        assertFalse(file("manifest.json").exists());
    }

    @Test
    void lightTurnedOffInTheHomeIsNotRendered() throws Exception {
        ExportConfig config = config()
                .lightIds(Arrays.asList(lightId("Kitchen lamp"), lightId("Unlit"), lightId("Desk"))).build();
        assertTrue(exporter(config).run(null));

        assertEquals(4, this.backend.sessions.size());
        assertFalse(file("ground-floor/lights/unlit.png").exists());
        String manifest = new String(Files.readAllBytes(file("manifest.json").toPath()), StandardCharsets.UTF_8);
        Map<?, ?> skipped = (Map<?, ?>)((List<?>)((Map<?, ?>)Json.parse(manifest)).get("skippedLights")).get(0);
        assertEquals("Unlit", skipped.get("name"));
        assertEquals("off", skipped.get("reason"));
    }

    @Test
    void capLightReachesEverySession() throws Exception {
        this.backend.supportsLightCap = true;
        exporter(config().capLight(LightCap.SUN).build()).run(null);
        assertEquals(4, this.backend.sessions.size());
        for (FakeRenderBackend.Session session : this.backend.sessions) {
            assertEquals(LightCap.SUN, session.capLight);
        }
        assertTrue(new String(Files.readAllBytes(file("manifest.json").toPath()), StandardCharsets.UTF_8)
                .contains("\"capLight\": \"sun\""));

        this.backend.sessions.clear();
        exporter(config().build()).run(null);
        assertEquals(LightCap.OFF, this.backend.sessions.get(0).capLight);
    }

    @Test
    void capLightNeedsASupportingRenderer() {
        ExportException ex = assertThrows(ExportException.class,
                () -> exporter(config().capLight(LightCap.ALL).build()).run(null));
        assertEquals(1, ex.getProblems().size());
        assertTrue(ex.getProblems().get(0).contains("cannot block light"), ex.getProblems().get(0));
        assertTrue(this.backend.sessions.isEmpty());
    }

    @Test
    void exposureReachesEverySession() throws Exception {
        this.backend.supportsExposure = true;
        exporter(config().exposure(1.5).build()).run(null);
        assertEquals(4, this.backend.sessions.size());
        for (FakeRenderBackend.Session session : this.backend.sessions) {
            assertEquals(1.5, session.exposure);
        }
        assertTrue(new String(Files.readAllBytes(file("manifest.json").toPath()), StandardCharsets.UTF_8)
                .contains("\"exposure\": 1.5"));
    }

    @Test
    void exposureNeedsASupportingRenderer() throws Exception {
        ExportException ex = assertThrows(ExportException.class,
                () -> exporter(config().exposure(-1).build()).run(null));
        assertEquals(1, ex.getProblems().size());
        assertTrue(ex.getProblems().get(0).contains("exposure"), ex.getProblems().get(0));
        assertTrue(this.backend.sessions.isEmpty());

        // No exposure is fine with any renderer
        assertTrue(exporter(config().exposure(0).build()).run(null));
    }

    @Test
    void listenerSeesEveryJob() throws Exception {
        List<String> events = new ArrayList<String>();
        exporter(config().build()).run(new ExportListener() {
            @Override
            public void jobStarted(int index, int total, RenderJob job) {
                events.add("start " + index + "/" + total + " " + job.kind);
            }

            @Override
            public void jobFinished(int index, int total, RenderJob job) {
                events.add("end " + index + "/" + total);
            }
        });
        assertEquals(16, events.size());
        assertEquals("start 0/8 BASE", events.get(0));
        assertEquals("end 0/8", events.get(1));
        assertEquals("start 3/8 LIGHT", events.get(6));
        assertEquals("end 7/8", events.get(15));
    }
}
