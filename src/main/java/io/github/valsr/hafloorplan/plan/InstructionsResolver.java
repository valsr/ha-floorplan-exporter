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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Matches the references of instructions with the floors, cameras, lights and renderers of a home.
 */
public final class InstructionsResolver {
    private InstructionsResolver() {
    }

    /**
     * The outcome of {@link InstructionsResolver#resolve}: a config holding what could be resolved
     * and a problem for each reference that couldn't.
     */
    public static final class Resolution {
        private final ExportConfig config;
        private final List<String> problems;

        Resolution(ExportConfig config, List<String> problems) {
            this.config = config;
            this.problems = Collections.unmodifiableList(problems);
        }

        public ExportConfig getConfig() {
            return this.config;
        }

        public List<String> getProblems() {
            return this.problems;
        }
    }

    /**
     * Resolves <code>instructions</code> against <code>summary</code>. References matching nothing or more than
     * one object are left out of the returned config and reported as problems, never thrown.
     * @param baseDir the folder a relative output path starts from
     */
    public static Resolution resolve(Instructions instructions, HomeSummary summary, File baseDir) {
        List<String> problems = new ArrayList<String>();
        Map<String, String> floorNames = new LinkedHashMap<String, String>();
        for (HomeSummary.Floor floor : summary.getFloors()) {
            floorNames.put(floor.id, floor.name);
        }
        Map<String, String> cameraNames = new LinkedHashMap<String, String>();
        for (HomeSummary.Camera camera : summary.getCameras()) {
            cameraNames.put(camera.id, camera.name);
        }
        Map<String, String> lightNames = new LinkedHashMap<String, String>();
        for (HomeSummary.Light light : summary.getLights()) {
            lightNames.put(light.id, light.name);
        }

        List<ExportConfig.Floor> floors = new ArrayList<ExportConfig.Floor>();
        if (instructions.isAllFloors()) {
            for (String floorId : floorNames.keySet()) {
                floors.add(new ExportConfig.Floor(floorId, null));
            }
        } else {
            List<String> floorIds = new ArrayList<String>();
            for (Instructions.Floor floor : instructions.getFloors()) {
                String levelId = find(floor.level, floorNames, "floor", problems);
                if (levelId == null) {
                    continue;
                } else if (floorIds.contains(levelId)) {
                    problems.add("Floor \"" + floorNames.get(levelId) + "\" is listed more than once");
                    continue;
                }
                String cameraId = floor.camera != null ? find(floor.camera, cameraNames, "camera", problems) : null;
                floorIds.add(levelId);
                floors.add(new ExportConfig.Floor(levelId, cameraId));
            }
        }

        List<String> lightIds = new ArrayList<String>();
        if (instructions.isAllLights()) {
            lightIds.addAll(lightNames.keySet());
        } else {
            for (Ref light : instructions.getLights()) {
                String lightId = find(light, lightNames, "light", problems);
                if (lightId != null && !lightIds.contains(lightId)) {
                    lightIds.add(lightId);
                }
            }
        }

        File outputDir = null;
        if (instructions.getOutput() != null) {
            outputDir = new File(instructions.getOutput());
            if (!outputDir.isAbsolute()) {
                outputDir = new File(baseDir, instructions.getOutput());
            }
        }

        String rendererClassName = findRenderer(instructions.getRenderer(), summary, problems);
        LightCap capLight = instructions.getCapLight();
        HomeSummary.Renderer renderer = summary.renderer(rendererClassName);
        if (capLight != LightCap.OFF && renderer != null && !renderer.supportsLightCap) {
            problems.add("capLight needs a renderer that can hide objects from the camera only, like a recent Blender GPU"
                    + " renderer; the installed " + renderer.displayName + " cannot");
            capLight = LightCap.OFF;
        }

        ExportConfig config = ExportConfig.builder()
                .floors(floors)
                .dates(instructions.getDates())
                .times(instructions.getTimes())
                .lightIds(lightIds)
                .width(instructions.getWidth())
                .height(instructions.getHeight())
                .rendererClassName(rendererClassName)
                .quality(instructions.getQuality())
                .hideCeilings(instructions.isHideCeilings())
                .isolateLevel(instructions.isIsolateLevel())
                .capLight(capLight)
                .noiseThreshold(instructions.getNoiseThreshold())
                .outputDir(outputDir)
                .build();
        return new Resolution(config, problems);
    }

    /**
     * Returns the id of the object <code>ref</code> designates among <code>namesById</code>,
     * or <code>null</code> after adding a problem.
     */
    private static String find(Ref ref, Map<String, String> namesById, String kind, List<String> problems) {
        if (ref.id != null && namesById.containsKey(ref.id)) {
            return ref.id;
        }
        String match = null;
        int count = 0;
        if (ref.name != null) {
            for (Map.Entry<String, String> entry : namesById.entrySet()) {
                if (ref.name.equals(entry.getValue())) {
                    match = entry.getKey();
                    count++;
                }
            }
        }
        if (count == 1) {
            return match;
        } else if (count > 1) {
            problems.add("Ambiguous " + kind + " name \"" + ref.name + "\" (" + count + " matches)");
        } else {
            problems.add("Unknown " + kind + " \"" + ref + "\"");
        }
        return null;
    }

    private static String findRenderer(String name, HomeSummary summary, List<String> problems) {
        List<HomeSummary.Renderer> renderers = summary.getRenderers();
        if (renderers.isEmpty()) {
            problems.add("No renderer available");
            return null;
        } else if (name == null) {
            return renderers.get(0).className;
        }
        for (HomeSummary.Renderer renderer : renderers) {
            String simpleName = renderer.className.substring(renderer.className.lastIndexOf('.') + 1);
            if (name.equalsIgnoreCase(renderer.className) || name.equalsIgnoreCase(simpleName)
                    || name.toLowerCase(Locale.ROOT).equals(renderer.displayName.toLowerCase(Locale.ROOT))) {
                return renderer.className;
            }
        }
        StringBuilder available = new StringBuilder();
        for (HomeSummary.Renderer renderer : renderers) {
            available.append(available.length() > 0 ? ", " : "").append(renderer.displayName);
        }
        problems.add("Unknown renderer \"" + name + "\" (available: " + available + ")");
        return renderers.get(0).className;
    }

    /**
     * Returns instructions designating the same objects as <code>config</code>, each with its id and its name.
     * @param homePath the path of the home file written in the instructions, or <code>null</code>
     */
    public static Instructions toInstructions(ExportConfig config, HomeSummary summary, String homePath) {
        List<Instructions.Floor> floors = new ArrayList<Instructions.Floor>();
        for (ExportConfig.Floor floor : config.getFloors()) {
            HomeSummary.Floor level = summary.floor(floor.levelId);
            HomeSummary.Camera camera = floor.cameraId != null ? summary.camera(floor.cameraId) : null;
            floors.add(new Instructions.Floor(new Ref(floor.levelId, level != null ? level.name : null),
                    floor.cameraId != null ? new Ref(floor.cameraId, camera != null ? camera.name : null) : null));
        }
        List<Ref> lights = new ArrayList<Ref>();
        for (String lightId : config.getLightIds()) {
            HomeSummary.Light light = summary.light(lightId);
            lights.add(new Ref(lightId, light != null ? light.name : null));
        }
        return Instructions.builder()
                .home(homePath)
                .output(config.getOutputDir() != null ? config.getOutputDir().getAbsolutePath() : null)
                .floors(floors)
                .dates(config.getDates())
                .times(config.getTimes())
                .lights(lights)
                .width(config.getWidth())
                .height(config.getHeight())
                .renderer(config.getRendererClassName())
                .quality(config.getQuality())
                .hideCeilings(config.isHideCeilings())
                .isolateLevel(config.isIsolateLevel())
                .capLight(config.getCapLight())
                .noiseThreshold(config.getNoiseThreshold())
                .build();
    }
}
