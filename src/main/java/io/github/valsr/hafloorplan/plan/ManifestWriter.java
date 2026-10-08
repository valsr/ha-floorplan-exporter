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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes <code>manifest.json</code>, the description of the exported images.
 */
public final class ManifestWriter {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter NIGHT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private ManifestWriter() {
    }

    /**
     * Returns the JSON text of the manifest.
     * @param nightTime the time lights were rendered at, or <code>null</code> if no light is exported
     * @param nightSunElevation the elevation of the sun in degrees at that time, or <code>null</code>
     */
    public static String write(ExportConfig config, HomeSummary summary, ExportPlan plan, String generator,
                               LocalDateTime nightTime, Double nightSunElevation) {
        Map<String, Object> manifest = new LinkedHashMap<String, Object>();
        manifest.put("generator", generator);
        manifest.put("width", config.getWidth());
        manifest.put("height", config.getHeight());
        manifest.put("renderer", config.getRendererClassName());
        HomeSummary.Renderer renderer = summary.renderer(config.getRendererClassName());
        manifest.put("rendererName", renderer != null ? renderer.displayName : null);
        manifest.put("quality", config.getQuality().name());
        manifest.put("hideCeilings", config.isHideCeilings());
        manifest.put("isolateLevel", config.isIsolateLevel());
        manifest.put("capLight", config.getCapLight().toText());
        manifest.put("exposure", config.getExposure());
        List<Object> dates = new ArrayList<Object>();
        for (LocalDate date : config.getDates().dates()) {
            dates.add(date.toString());
        }
        manifest.put("dates", dates);
        List<Object> times = new ArrayList<Object>();
        for (LocalTime time : config.getTimes().times()) {
            times.add(time.format(TIME_FORMAT));
        }
        manifest.put("times", times);
        if (nightTime != null) {
            manifest.put("nightTime", nightTime.format(NIGHT_TIME_FORMAT));
            manifest.put("nightSunElevation", nightSunElevation != null ? Math.round(nightSunElevation * 10) / 10. : null);
        }

        List<Object> floors = new ArrayList<Object>();
        for (ExportConfig.Floor floor : config.getFloors()) {
            Map<String, Object> floorObject = new LinkedHashMap<String, Object>();
            HomeSummary.Floor summaryFloor = summary.floor(floor.levelId);
            HomeSummary.Camera camera = floor.cameraId != null ? summary.camera(floor.cameraId) : null;
            floorObject.put("id", floor.levelId);
            floorObject.put("name", summaryFloor != null ? summaryFloor.name : null);
            floorObject.put("slug", plan.floorSlug(floor.levelId));
            floorObject.put("camera", camera != null ? camera.name : null);
            List<Object> base = new ArrayList<Object>();
            List<Object> lights = new ArrayList<Object>();
            String night = null;
            for (RenderJob job : plan.getJobs()) {
                if (!job.floorId.equals(floor.levelId)) {
                    continue;
                }
                Map<String, Object> image = new LinkedHashMap<String, Object>();
                switch (job.kind) {
                    case BASE:
                        image.put("date", job.date.toString());
                        image.put("time", job.time.format(TIME_FORMAT));
                        image.put("file", job.path);
                        base.add(image);
                        break;
                    case NIGHT_BASE:
                        night = job.path;
                        break;
                    default:
                        HomeSummary.Light light = summary.light(job.lightId);
                        image.put("id", job.lightId);
                        image.put("name", light != null ? light.name : null);
                        image.put("slug", plan.lightSlug(job.lightId));
                        image.put("file", job.path);
                        lights.add(image);
                }
            }
            floorObject.put("base", base);
            if (night != null) {
                floorObject.put("night", night);
            }
            floorObject.put("lights", lights);
            floors.add(floorObject);
        }
        manifest.put("floors", floors);

        List<Object> skippedLights = new ArrayList<Object>();
        for (String lightId : plan.getSkippedLightIds()) {
            Map<String, Object> skipped = new LinkedHashMap<String, Object>();
            HomeSummary.Light light = summary.light(lightId);
            skipped.put("id", lightId);
            skipped.put("name", light != null ? light.name : null);
            skipped.put("reason", light != null && light.power <= 0 ? "off" : "not on an exported floor");
            skippedLights.add(skipped);
        }
        manifest.put("skippedLights", skippedLights);
        return Json.write(manifest);
    }
}
