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

import java.awt.HeadlessException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import com.eteks.sweethome3d.io.HomeFileRecorder;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.RecorderException;

import io.github.valsr.hafloorplan.engine.ExportException;
import io.github.valsr.hafloorplan.engine.ExportListener;
import io.github.valsr.hafloorplan.engine.Exporter;
import io.github.valsr.hafloorplan.engine.HomeInspector;
import io.github.valsr.hafloorplan.engine.RenderBackend;
import io.github.valsr.hafloorplan.engine.Sh3dRenderBackend;
import io.github.valsr.hafloorplan.plan.Instructions;
import io.github.valsr.hafloorplan.plan.InstructionsException;
import io.github.valsr.hafloorplan.plan.InstructionsJson;
import io.github.valsr.hafloorplan.plan.InstructionsResolver;
import io.github.valsr.hafloorplan.plan.RenderJob;

/**
 * Exports a home from the command line, following an instructions file.
 */
public final class HeadlessExport {
    private static final String USAGE = "Usage: HeadlessExport <instructions.json | -> [--home <home.sh3d>] [--output <dir>]";

    private static final int EXIT_OK = 0;
    private static final int EXIT_EXPORT_FAILED = 1;
    private static final int EXIT_BAD_INPUT = 2;

    private static volatile Exporter runningExporter;

    private HeadlessExport() {
    }

    /**
     * Stops the export in progress, if any, and frees its renderer.
     */
    static void abortRunningExport() {
        Exporter exporter = runningExporter;
        if (exporter != null) {
            exporter.abort();
        }
    }

    public static void main(String [] args) {
        // Ctrl-C is the only way to cancel: don't leave a renderer process or its temporary files behind
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() {
                abortRunningExport();
            }
        }));
        // Exit explicitly because Java 3D and AWT may leave threads running
        System.exit(run(args, System.in, System.out, System.err, new Sh3dRenderBackend()));
    }

    /**
     * Runs the export described by <code>args</code> and returns the exit code: 0 if it succeeded,
     * 1 if it failed while rendering, 2 if the arguments, the instructions or the home can't be used.
     */
    public static int run(String [] args, InputStream in, PrintStream out, PrintStream err, RenderBackend backend) {
        try {
            return export(args, in, out, err, backend);
        } catch (LinkageError ex) {
            // Java 3D classes, needed by all renderers to build the scene, don't load without a display
            if (ex.getCause() instanceof HeadlessException) {
                err.println("Java 3D needs a display: run with DISPLAY set (xvfb-run is enough) and without java.awt.headless");
                return EXIT_EXPORT_FAILED;
            }
            throw ex;
        }
    }

    private static int export(String [] args, InputStream in, final PrintStream out, PrintStream err, RenderBackend backend) {
        Instructions instructions;
        try {
            instructions = load(args, in, new File("").getAbsoluteFile());
        } catch (InstructionsException ex) {
            for (String problem : ex.getProblems()) {
                err.println(problem);
            }
            return EXIT_BAD_INPUT;
        } catch (IllegalArgumentException ex) {
            err.println(ex.getMessage());
            err.println(USAGE);
            return EXIT_BAD_INPUT;
        } catch (IOException ex) {
            err.println("Can't read instructions: " + ex.getMessage());
            return EXIT_BAD_INPUT;
        }

        Home home;
        try {
            if (!new File(instructions.getHome()).isFile()) {
                err.println("Home file " + instructions.getHome() + " not found");
                return EXIT_BAD_INPUT;
            }
            home = new HomeFileRecorder().readHome(instructions.getHome());
        } catch (RecorderException ex) {
            err.println("Can't read home " + instructions.getHome() + ": " + ex.getMessage());
            return EXIT_BAD_INPUT;
        }

        // Unlike the dialog, refuse instructions which don't fully match the home
        InstructionsResolver.Resolution resolution = InstructionsResolver.resolve(
                instructions, HomeInspector.summarize(home, backend), new File(instructions.getOutput()).getParentFile());
        if (!resolution.getProblems().isEmpty()) {
            for (String problem : resolution.getProblems()) {
                err.println(problem);
            }
            return EXIT_BAD_INPUT;
        }

        Exporter exporter = new Exporter(home, resolution.getConfig(), backend, Exporter.generatorName());
        runningExporter = exporter;
        try {
            boolean completed = exporter.run(new ExportListener() {
                @Override
                public void jobStarted(int index, int total, RenderJob job) {
                }

                @Override
                public void jobFinished(int index, int total, RenderJob job) {
                    out.println("[" + (index + 1) + "/" + total + "] " + job.path);
                }
            });
            if (!completed) {
                err.println("Export cancelled, no manifest written");
                return EXIT_EXPORT_FAILED;
            }
            return EXIT_OK;
        } catch (ExportException ex) {
            for (String problem : ex.getProblems()) {
                err.println(problem);
            }
            // Without a cause, the settings were refused before any image was rendered
            return ex.getCause() == null ? EXIT_BAD_INPUT : EXIT_EXPORT_FAILED;
        } finally {
            runningExporter = null;
        }
    }

    /**
     * Returns the instructions designated by <code>args</code>, with the absolute paths of the home and of
     * the output folder. Paths given in the instructions start from the folder of the instructions file,
     * paths given as arguments, or in instructions read from <code>in</code>, from <code>workingDir</code>.
     * @throws IllegalArgumentException if arguments are wrong or designate no home or no output folder
     * @throws InstructionsException if the instructions are invalid
     */
    public static Instructions load(String [] args, InputStream in, File workingDir) throws IOException {
        String instructionsArg = null;
        String homeArg = null;
        String outputArg = null;
        for (int i = 0; i < args.length; i++) {
            if (args [i].equals("--home") || args [i].equals("--output")) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Missing value after " + args [i]);
                } else if (args [i].equals("--home")) {
                    homeArg = args [++i];
                } else {
                    outputArg = args [++i];
                }
            } else if (args [i].startsWith("--")) {
                throw new IllegalArgumentException("Unknown option " + args [i]);
            } else if (instructionsArg == null) {
                instructionsArg = args [i];
            } else {
                throw new IllegalArgumentException("Unexpected argument " + args [i]);
            }
        }
        if (instructionsArg == null) {
            throw new IllegalArgumentException("Missing instructions file");
        }

        String json;
        File baseDir;
        if (instructionsArg.equals("-")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte [] buffer = new byte [8192];
            for (int size; (size = in.read(buffer)) != -1; ) {
                bytes.write(buffer, 0, size);
            }
            json = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
            baseDir = workingDir;
        } else {
            File file = resolve(workingDir, instructionsArg);
            json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            baseDir = file.getParentFile();
        }
        Instructions instructions = InstructionsJson.parse(json);

        if (homeArg != null) {
            instructions = instructions.withHome(resolve(workingDir, homeArg).getPath());
        } else if (instructions.getHome() != null) {
            instructions = instructions.withHome(resolve(baseDir, instructions.getHome()).getPath());
        } else {
            throw new IllegalArgumentException("No home: use --home or \"home\" in the instructions");
        }
        if (outputArg != null) {
            instructions = instructions.withOutput(resolve(workingDir, outputArg).getPath());
        } else if (instructions.getOutput() != null) {
            instructions = instructions.withOutput(resolve(baseDir, instructions.getOutput()).getPath());
        } else {
            throw new IllegalArgumentException("No output folder: use --output or \"output\" in the instructions");
        }
        return instructions;
    }

    private static File resolve(File baseDir, String path) {
        File file = new File(path);
        if (!file.isAbsolute()) {
            file = new File(baseDir, path);
        }
        return file.toPath().normalize().toFile();
    }
}
