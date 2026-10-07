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
import java.util.List;
import java.util.Objects;

/**
 * The settings of an export, with every floor, camera, light and renderer resolved to an id of the home.
 */
public final class ExportConfig {
    private final List<Floor> floors;
    private final DateSchedule dates;
    private final TimeSchedule times;
    private final List<String> lightIds;
    private final int width;
    private final int height;
    private final String rendererClassName;
    private final Quality quality;
    private final boolean hideCeilings;
    private final boolean isolateLevel;
    private final int noiseThreshold;
    private final File outputDir;

    private ExportConfig(Builder builder) {
        this.floors = Collections.unmodifiableList(new ArrayList<Floor>(builder.floors));
        this.dates = builder.dates;
        this.times = builder.times;
        this.lightIds = Collections.unmodifiableList(new ArrayList<String>(builder.lightIds));
        this.width = builder.width;
        this.height = builder.height;
        this.rendererClassName = builder.rendererClassName;
        this.quality = builder.quality;
        this.hideCeilings = builder.hideCeilings;
        this.isolateLevel = builder.isolateLevel;
        this.noiseThreshold = builder.noiseThreshold;
        this.outputDir = builder.outputDir;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<Floor> getFloors() {
        return this.floors;
    }

    public DateSchedule getDates() {
        return this.dates;
    }

    public TimeSchedule getTimes() {
        return this.times;
    }

    public List<String> getLightIds() {
        return this.lightIds;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    /** Returns the class name of the renderer, or <code>null</code> if the home offers none. */
    public String getRendererClassName() {
        return this.rendererClassName;
    }

    public Quality getQuality() {
        return this.quality;
    }

    public boolean isHideCeilings() {
        return this.hideCeilings;
    }

    public boolean isIsolateLevel() {
        return this.isolateLevel;
    }

    public int getNoiseThreshold() {
        return this.noiseThreshold;
    }

    /** Returns the output folder, or <code>null</code> if none was chosen yet. */
    public File getOutputDir() {
        return this.outputDir;
    }

    /**
     * A floor to export, seen from a stored camera.
     */
    public static final class Floor {
        public final String levelId;
        /** Id of a stored camera, or <code>null</code> for the current view of the home. */
        public final String cameraId;

        public Floor(String levelId, String cameraId) {
            this.levelId = levelId;
            this.cameraId = cameraId;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Floor && this.levelId.equals(((Floor)obj).levelId)
                    && Objects.equals(this.cameraId, ((Floor)obj).cameraId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.levelId, this.cameraId);
        }
    }

    public static final class Builder {
        private List<Floor> floors = Collections.emptyList();
        private DateSchedule dates;
        private TimeSchedule times;
        private List<String> lightIds = Collections.emptyList();
        private int width = 1920;
        private int height = 1080;
        private String rendererClassName;
        private Quality quality = Quality.LOW;
        private boolean hideCeilings = true;
        private boolean isolateLevel;
        private int noiseThreshold = 6;
        private File outputDir;

        private Builder() {
        }

        public Builder floors(List<Floor> floors) {
            this.floors = floors;
            return this;
        }

        public Builder dates(DateSchedule dates) {
            this.dates = dates;
            return this;
        }

        public Builder times(TimeSchedule times) {
            this.times = times;
            return this;
        }

        public Builder lightIds(List<String> lightIds) {
            this.lightIds = lightIds;
            return this;
        }

        public Builder width(int width) {
            this.width = width;
            return this;
        }

        public Builder height(int height) {
            this.height = height;
            return this;
        }

        public Builder rendererClassName(String rendererClassName) {
            this.rendererClassName = rendererClassName;
            return this;
        }

        public Builder quality(Quality quality) {
            this.quality = quality;
            return this;
        }

        public Builder hideCeilings(boolean hideCeilings) {
            this.hideCeilings = hideCeilings;
            return this;
        }

        public Builder isolateLevel(boolean isolateLevel) {
            this.isolateLevel = isolateLevel;
            return this;
        }

        public Builder noiseThreshold(int noiseThreshold) {
            this.noiseThreshold = noiseThreshold;
            return this;
        }

        public Builder outputDir(File outputDir) {
            this.outputDir = outputDir;
            return this;
        }

        public ExportConfig build() {
            if (this.dates == null || this.times == null) {
                throw new IllegalStateException("Dates and times are required");
            }
            return new ExportConfig(this);
        }
    }
}
