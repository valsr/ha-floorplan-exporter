package io.github.valsr.hafloorplan.engine;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import javax.imageio.ImageIO;

import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.Home;

import io.github.valsr.hafloorplan.plan.ExportConfig;
import io.github.valsr.hafloorplan.plan.ExportPlan;
import io.github.valsr.hafloorplan.plan.ExportPlanner;
import io.github.valsr.hafloorplan.plan.HomeSummary;
import io.github.valsr.hafloorplan.plan.ManifestWriter;
import io.github.valsr.hafloorplan.plan.RenderJob;

/**
 * Renders the images of an export in a folder, from a copy of the home that is never shown to the user.
 */
public final class Exporter {
    private static final String MANIFEST_FILE = "manifest.json";

    private final Home home;
    private final ExportConfig config;
    private final RenderBackend backend;
    private final String generator;
    private volatile boolean cancelled;
    private volatile RenderSession session;

    /**
     * @param home the home to export, left unchanged
     * @param generator the name and version written in the manifest
     */
    public Exporter(Home home, ExportConfig config, RenderBackend backend, String generator) {
        this.home = home;
        this.config = config;
        this.backend = backend;
        this.generator = generator;
    }

    /**
     * Returns the name and version of this program, as written in manifests.
     */
    public static String generatorName() {
        Properties descriptor = new Properties();
        try (InputStream in = Exporter.class.getResourceAsStream("/ApplicationPlugin.properties")) {
            if (in != null) {
                descriptor.load(in);
            }
        } catch (IOException ex) {
            // Version unknown
        }
        return "ha-floorplan-exporter " + descriptor.getProperty("version", "unknown");
    }

    /**
     * Asks the running export to stop. May be called from any thread.
     */
    public void cancel() {
        this.cancelled = true;
        RenderSession session = this.session;
        if (session != null) {
            session.stop();
        }
    }

    /**
     * Renders all the images then writes the manifest, a file missing if the export fails or is cancelled.
     * @param listener notified of each image, or <code>null</code>
     * @return <code>false</code> if the export was cancelled
     * @throws ExportException if the settings can't be used with the home, without rendering anything,
     *     or if an image couldn't be rendered or written
     */
    public boolean run(ExportListener listener) throws ExportException {
        Home clone = this.home.clone();
        HomeSummary summary = HomeInspector.summarize(clone, this.backend);
        validate(summary);

        ExportPlan plan = ExportPlanner.plan(this.config, summary);
        List<RenderJob> jobs = plan.getJobs();
        SceneConfigurer scene = new SceneConfigurer(clone);
        LocalDateTime nightTime = null;
        for (RenderJob job : jobs) {
            if (job.kind == RenderJob.Kind.LIGHT && nightTime == null) {
                nightTime = scene.nightTime(this.config.getDates().getStart());
            }
        }

        String floorId = null;
        Camera camera = null;
        BufferedImage nightBase = null;
        try {
            for (int i = 0; i < jobs.size(); i++) {
                RenderJob job = jobs.get(i);
                if (this.cancelled) {
                    return false;
                }
                if (listener != null) {
                    listener.jobStarted(i, jobs.size(), job);
                }

                // A session shows one state of the home: one for a floor with its lights off, one per light
                boolean floorChanged = !job.floorId.equals(floorId);
                if (floorChanged || job.kind == RenderJob.Kind.LIGHT) {
                    closeSession();
                    if (floorChanged) {
                        floorId = job.floorId;
                        scene.showFloor(floorId, this.config.isIsolateLevel(), this.config.isHideCeilings());
                        camera = scene.camera(getCameraId(floorId));
                    }
                    scene.setLights(job.lightId);
                    this.session = this.backend.open(clone, this.config.getRendererClassName(), this.config.getQuality());
                    if (this.cancelled) {
                        // Cancelled while the session was opening
                        return false;
                    }
                }

                camera.setTime(job.kind == RenderJob.Kind.BASE
                        ? SceneConfigurer.cameraTime(job.date, job.time)
                        : SceneConfigurer.cameraTime(nightTime.toLocalDate(), nightTime.toLocalTime()));
                BufferedImage image = this.session.render(camera, this.config.getWidth(), this.config.getHeight());
                if (this.cancelled) {
                    // The interrupted image isn't complete
                    return false;
                }
                File file = new File(this.config.getOutputDir(), job.path);
                if (job.kind == RenderJob.Kind.LIGHT) {
                    write(OverlayDiff.diff(nightBase, image, this.config.getNoiseThreshold()), file);
                } else {
                    BufferedImage opaqueImage = toOpaque(image);
                    if (job.kind == RenderJob.Kind.NIGHT_BASE) {
                        nightBase = opaqueImage;
                    }
                    write(opaqueImage, file);
                }

                if (listener != null) {
                    listener.jobFinished(i, jobs.size(), job);
                }
            }
            closeSession();

            String manifest = ManifestWriter.write(this.config, summary, plan, this.generator, nightTime,
                    nightTime != null ? Double.valueOf(scene.sunElevation(nightTime)) : null);
            Files.write(new File(this.config.getOutputDir(), MANIFEST_FILE).toPath(),
                    manifest.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException ex) {
            throw failure(ex);
        } catch (RuntimeException ex) {
            throw failure(ex);
        } finally {
            closeSession();
        }
    }

    private ExportException failure(Exception ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : ex.toString();
        return new ExportException(Collections.singletonList("Export failed: " + message), ex);
    }

    private void closeSession() {
        RenderSession session = this.session;
        this.session = null;
        if (session != null) {
            session.close();
        }
    }

    private String getCameraId(String floorId) {
        for (ExportConfig.Floor floor : this.config.getFloors()) {
            if (floor.levelId.equals(floorId)) {
                return floor.cameraId;
            }
        }
        return null;
    }

    /**
     * Checks the settings against the home and prepares the output folder.
     * @throws ExportException with everything that prevents the export
     */
    private void validate(HomeSummary summary) throws ExportException {
        List<String> problems = new ArrayList<String>();
        if (this.config.getFloors().isEmpty()) {
            problems.add("No floor to export");
        }
        for (ExportConfig.Floor floor : this.config.getFloors()) {
            if (summary.floor(floor.levelId) == null) {
                problems.add("Unknown floor " + floor.levelId);
            }
            if (floor.cameraId != null && summary.camera(floor.cameraId) == null) {
                problems.add("Unknown point of view " + floor.cameraId);
            }
        }
        for (String lightId : this.config.getLightIds()) {
            if (summary.light(lightId) == null) {
                problems.add("Unknown light " + lightId);
            }
        }
        if (this.config.getRendererClassName() == null) {
            problems.add("No renderer available");
        } else if (summary.renderer(this.config.getRendererClassName()) == null) {
            String problem = "Renderer " + this.config.getRendererClassName() + " is not available";
            if (this.config.getRendererClassName().contains("gpurenderer")) {
                problem += " (is the gpu-renderer agent loaded and Blender installed?)";
            }
            problems.add(problem);
        }
        File outputDir = this.config.getOutputDir();
        if (outputDir == null) {
            problems.add("No output folder");
        } else if (outputDir.exists() && !outputDir.isDirectory()) {
            problems.add("Output " + outputDir + " is not a folder");
        } else if (problems.isEmpty() && !outputDir.isDirectory() && !outputDir.mkdirs()) {
            problems.add("Can't create output folder " + outputDir);
        } else if (outputDir.isDirectory() && !outputDir.canWrite()) {
            problems.add("Can't write in output folder " + outputDir);
        }
        if (!problems.isEmpty()) {
            throw new ExportException(problems);
        }
    }

    private static BufferedImage toOpaque(BufferedImage image) {
        BufferedImage opaqueImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = opaqueImage.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return opaqueImage;
    }

    private static void write(BufferedImage image, File file) throws IOException {
        File folder = file.getParentFile();
        if (!folder.isDirectory() && !folder.mkdirs()) {
            throw new IOException("Can't create folder " + folder);
        }
        if (!ImageIO.write(image, "png", file)) {
            throw new IOException("Can't write " + file);
        }
    }
}
