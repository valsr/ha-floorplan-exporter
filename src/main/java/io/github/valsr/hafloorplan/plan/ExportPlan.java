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

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The images of an export in the order they're rendered, and the names of their files.
 */
public final class ExportPlan {
    private final List<RenderJob> jobs;
    private final Map<String, String> floorSlugs;
    private final Map<String, String> lightSlugs;
    private final List<String> skippedLightIds;

    ExportPlan(List<RenderJob> jobs, Map<String, String> floorSlugs, Map<String, String> lightSlugs,
               List<String> skippedLightIds) {
        this.jobs = Collections.unmodifiableList(jobs);
        this.floorSlugs = floorSlugs;
        this.lightSlugs = lightSlugs;
        this.skippedLightIds = Collections.unmodifiableList(skippedLightIds);
    }

    public List<RenderJob> getJobs() {
        return this.jobs;
    }

    /** Returns the folder name of an exported floor. */
    public String floorSlug(String floorId) {
        return this.floorSlugs.get(floorId);
    }

    /** Returns the file name, without extension, of an exported light. */
    public String lightSlug(String lightId) {
        return this.lightSlugs.get(lightId);
    }

    /** Returns the selected lights that aren't exported because they aren't on a selected floor. */
    public List<String> getSkippedLightIds() {
        return this.skippedLightIds;
    }
}
