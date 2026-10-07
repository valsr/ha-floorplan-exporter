package io.github.valsr.hafloorplan.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.valsr.hafloorplan.plan.DateSchedule;
import io.github.valsr.hafloorplan.plan.ExportConfig;
import io.github.valsr.hafloorplan.plan.HomeSummary;
import io.github.valsr.hafloorplan.plan.Instructions;
import io.github.valsr.hafloorplan.plan.InstructionsJson;
import io.github.valsr.hafloorplan.plan.InstructionsResolver;
import io.github.valsr.hafloorplan.plan.Quality;
import io.github.valsr.hafloorplan.plan.Ref;
import io.github.valsr.hafloorplan.plan.TimeSchedule;

class ExportDialogTest {
    private static final String SUNFLOW = "com.eteks.sweethome3d.j3d.PhotoRenderer";
    private static final String BLENDER = "sh3d.gpurenderer.BlenderRenderer";
    private static final File HOME_FILE = new File("/homes/house.sh3d");

    @TempDir
    Path tempDir;

    @BeforeEach
    void needsDisplay() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
    }

    private static HomeSummary summary() {
        return new HomeSummary(
                Arrays.asList(new HomeSummary.Floor("f1", "Ground"), new HomeSummary.Floor("f2", "First")),
                Arrays.asList(new HomeSummary.Light("l1", "Lamp", "f1"), new HomeSummary.Light("l2", "Lamp", "f1"),
                        new HomeSummary.Light("l3", "Desk", "f2")),
                Arrays.asList(new HomeSummary.Camera("c1", "Top ground"), new HomeSummary.Camera("c2", "Top first")),
                Arrays.asList(new HomeSummary.Renderer(SUNFLOW, "SunFlow"),
                        new HomeSummary.Renderer(BLENDER, "Blender Cycles (GPU)")));
    }

    private static ExportDialog dialog() {
        return new ExportDialog(null, summary(), HOME_FILE, false);
    }

    private static ExportConfig.Builder config() {
        return ExportConfig.builder()
                .floors(Arrays.asList(new ExportConfig.Floor("f2", "c2")))
                .dates(new DateSchedule(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-03-01"), 30))
                .times(new TimeSchedule(LocalTime.parse("06:00"), LocalTime.parse("18:00"), 360))
                .lightIds(Arrays.asList("l3"))
                .width(640).height(480)
                .rendererClassName(BLENDER).quality(Quality.HIGH)
                .hideCeilings(false).isolateLevel(true).noiseThreshold(9)
                .outputDir(new File("/data/out"));
    }

    @Test
    void firstOpenSelectsEverything() {
        Instructions instructions = dialog().getInstructions();
        assertEquals(2, instructions.getFloors().size());
        assertEquals(null, instructions.getFloors().get(0).camera);
        assertEquals(3, instructions.getLights().size());
        assertEquals(new DateSchedule(LocalDate.now(), LocalDate.now(), 1), instructions.getDates());
        assertEquals(new TimeSchedule(LocalTime.parse("00:00"), LocalTime.parse("23:00"), 240), instructions.getTimes());
        assertEquals(1920, instructions.getWidth());
        assertEquals(1080, instructions.getHeight());
        assertEquals(SUNFLOW, instructions.getRenderer());
        assertEquals(Quality.LOW, instructions.getQuality());
        assertTrue(instructions.isHideCeilings());
        assertFalse(instructions.isIsolateLevel());
        assertEquals(HOME_FILE.getAbsolutePath(), instructions.getHome());
    }

    @Test
    void stateRoundTrips() {
        Instructions instructions = InstructionsResolver.toInstructions(config().build(), summary(), HOME_FILE.getAbsolutePath());
        ExportDialog dialog = dialog();
        dialog.setInstructions(instructions);
        assertTrue(dialog.getLoadWarnings().isEmpty());
        assertEquals(instructions, dialog.getInstructions());
        assertTrue(dialog.getValidationErrors().isEmpty(), dialog.getValidationErrors().toString());
    }

    @Test
    void loadDropsUnknownReferences() {
        Instructions instructions = Instructions.builder()
                .floors(Arrays.asList(new Instructions.Floor(Ref.of("Attic"), null),
                        new Instructions.Floor(Ref.of("First"), Ref.of("Top first"))))
                .dates(new DateSchedule(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-01"), 1))
                .times(new TimeSchedule(LocalTime.parse("12:00"), LocalTime.parse("12:00"), 60))
                .lights(Arrays.asList(Ref.of("Chandelier"), Ref.of("Desk")))
                .build();
        ExportDialog dialog = dialog();
        dialog.setInstructions(instructions);

        assertEquals(2, dialog.getLoadWarnings().size(), dialog.getLoadWarnings().toString());
        Instructions state = dialog.getInstructions();
        assertEquals(Collections.singletonList(new Instructions.Floor(new Ref("f2", "First"), new Ref("c2", "Top first"))),
                state.getFloors());
        assertEquals(Collections.singletonList(new Ref("l3", "Desk")), state.getLights());
    }

    @Test
    void validation() {
        ExportDialog dialog = dialog();
        dialog.setInstructions(InstructionsResolver.toInstructions(config().build(), summary(), null));
        assertTrue(dialog.exportButton.isEnabled());
        assertTrue(dialog.saveButton.isEnabled());

        dialog.outputField.setText("");
        assertEquals(1, dialog.getValidationErrors().size());
        assertFalse(dialog.exportButton.isEnabled());
        assertFalse(dialog.saveButton.isEnabled());

        dialog.outputField.setText("/data/out");
        dialog.endDateField.setText("2025-12-31");
        assertEquals(1, dialog.getValidationErrors().size());
        assertTrue(dialog.getValidationErrors().get(0).contains("before"), dialog.getValidationErrors().get(0));
        assertFalse(dialog.exportButton.isEnabled());

        dialog.endDateField.setText("tomorrow");
        assertEquals(1, dialog.getValidationErrors().size());
        assertTrue(dialog.getValidationErrors().get(0).contains("YYYY-MM-DD"), dialog.getValidationErrors().get(0));
    }

    @Test
    void noFloorTickedIsAnError() {
        ExportDialog dialog = dialog();
        dialog.outputField.setText("/data/out");
        assertTrue(dialog.getValidationErrors().isEmpty());
        dialog.setInstructions(InstructionsResolver.toInstructions(
                config().floors(Collections.<ExportConfig.Floor>emptyList()).build(), summary(), null));
        assertEquals(1, dialog.getValidationErrors().size());
    }

    @Test
    void summaryCountsRenders() {
        ExportDialog dialog = dialog();
        dialog.setInstructions(InstructionsResolver.toInstructions(config()
                .floors(Arrays.asList(new ExportConfig.Floor("f1", null), new ExportConfig.Floor("f2", null)))
                .dates(new DateSchedule(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-02"), 1))
                .times(new TimeSchedule(LocalTime.parse("00:00"), LocalTime.parse("16:00"), 480))
                .lightIds(Arrays.asList("l1", "l2", "l3")).build(), summary(), null));
        assertEquals("12 base renders + 3 light renders", dialog.getSummaryText());
    }

    @Test
    void allAndNoneButtonsTickLights() {
        ExportDialog dialog = dialog();
        dialog.noLightsButton.doClick();
        assertTrue(dialog.getInstructions().getLights().isEmpty());
        dialog.allLightsButton.doClick();
        assertEquals(3, dialog.getInstructions().getLights().size());
    }

    @Test
    void hideOtherLevelsDisabledWithoutLevels() {
        HomeSummary noLevels = new HomeSummary(
                Arrays.asList(new HomeSummary.Floor(HomeSummary.DEFAULT_FLOOR_ID, "Home")),
                Collections.<HomeSummary.Light>emptyList(), Collections.<HomeSummary.Camera>emptyList(),
                summary().getRenderers());
        ExportDialog dialog = new ExportDialog(null, noLevels, null, false);
        assertFalse(dialog.isolateCheckBox.isEnabled());
        assertFalse(dialog.getInstructions().isIsolateLevel());
        assertEquals(null, dialog.getInstructions().getHome());
    }

    @Test
    void savedFileHoldsTheDialogState() throws Exception {
        ExportDialog dialog = dialog();
        dialog.setInstructions(InstructionsResolver.toInstructions(config().build(), summary(), null));
        File file = this.tempDir.resolve("job.json").toFile();
        dialog.saveInstructions(file);

        Instructions saved = InstructionsJson.parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        assertEquals(dialog.getInstructions(), saved);
        assertEquals(HOME_FILE.getAbsolutePath(), saved.getHome());
        assertEquals(new Ref("l3", "Desk"), saved.getLights().get(0));
    }

    @Test
    void loadingAFileReplacesTheState() throws Exception {
        ExportDialog source = dialog();
        source.setInstructions(InstructionsResolver.toInstructions(config().build(), summary(), null));
        File file = this.tempDir.resolve("job.json").toFile();
        source.saveInstructions(file);

        ExportDialog dialog = dialog();
        assertTrue(dialog.loadInstructions(file).isEmpty());
        assertEquals(source.getInstructions(), dialog.getInstructions());
    }

    @Test
    void loadingABrokenFileChangesNothing() throws Exception {
        File file = this.tempDir.resolve("broken.json").toFile();
        Files.write(file.toPath(), "{\"version\": 1,".getBytes(StandardCharsets.UTF_8));
        ExportDialog dialog = dialog();
        Instructions before = dialog.getInstructions();

        assertEquals(1, dialog.loadInstructions(file).size());
        assertEquals(before, dialog.getInstructions());
    }
}
