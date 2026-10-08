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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The content of an instructions file: the settings of an export where floors, cameras, lights
 * and the renderer are still references to resolve against a home.
 */
public final class Instructions {
    private final String home;
    private final String output;
    private final boolean allFloors;
    private final List<Floor> floors;
    private final DateSchedule dates;
    private final TimeSchedule times;
    private final boolean allLights;
    private final List<Ref> lights;
    private final int width;
    private final int height;
    private final String renderer;
    private final Quality quality;
    private final boolean hideCeilings;
    private final boolean isolateLevel;
    private final LightCap capLight;
    private final int noiseThreshold;

    private Instructions(Builder builder) {
        this.home = builder.home;
        this.output = builder.output;
        this.allFloors = builder.allFloors;
        this.floors = Collections.unmodifiableList(new ArrayList<Floor>(builder.floors));
        this.dates = builder.dates;
        this.times = builder.times;
        this.allLights = builder.allLights;
        this.lights = Collections.unmodifiableList(new ArrayList<Ref>(builder.lights));
        this.width = builder.width;
        this.height = builder.height;
        this.renderer = builder.renderer;
        this.quality = builder.quality;
        this.hideCeilings = builder.hideCeilings;
        this.isolateLevel = builder.isolateLevel;
        this.capLight = builder.capLight;
        this.noiseThreshold = builder.noiseThreshold;
    }

    /**
     * Returns a builder set to the defaults of the file format: all lights, 1920 x 1080, low quality,
     * ceilings hidden, other levels shown, noise threshold of 6.
     */
    public static Builder builder() {
        return new Builder();
    }

    private Builder toBuilder() {
        Builder builder = new Builder();
        builder.home = this.home;
        builder.output = this.output;
        builder.allFloors = this.allFloors;
        builder.floors = this.floors;
        builder.dates = this.dates;
        builder.times = this.times;
        builder.allLights = this.allLights;
        builder.lights = this.lights;
        builder.width = this.width;
        builder.height = this.height;
        builder.renderer = this.renderer;
        builder.quality = this.quality;
        builder.hideCeilings = this.hideCeilings;
        builder.isolateLevel = this.isolateLevel;
        builder.capLight = this.capLight;
        builder.noiseThreshold = this.noiseThreshold;
        return builder;
    }

    public Instructions withHome(String home) {
        return toBuilder().home(home).build();
    }

    public Instructions withAllLights() {
        return toBuilder().allLights().build();
    }

    public Instructions withOutput(String output) {
        return toBuilder().output(output).build();
    }

    /** Returns the path of the home file, or <code>null</code>. */
    public String getHome() {
        return this.home;
    }

    /** Returns the path of the output folder, or <code>null</code>. */
    public String getOutput() {
        return this.output;
    }

    /** Returns <code>true</code> if every floor is exported from the current view of the home. */
    public boolean isAllFloors() {
        return this.allFloors;
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

    public boolean isAllLights() {
        return this.allLights;
    }

    public List<Ref> getLights() {
        return this.lights;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    /** Returns the class name or the name of the renderer, or <code>null</code> for the first available one. */
    public String getRenderer() {
        return this.renderer;
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

    /** Returns what hidden ceilings and levels still block. */
    public LightCap getCapLight() {
        return this.capLight;
    }

    public int getNoiseThreshold() {
        return this.noiseThreshold;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Instructions)) {
            return false;
        }
        Instructions other = (Instructions)obj;
        return Objects.equals(this.home, other.home) && Objects.equals(this.output, other.output)
                && this.allFloors == other.allFloors && this.floors.equals(other.floors)
                && this.dates.equals(other.dates) && this.times.equals(other.times)
                && this.allLights == other.allLights && this.lights.equals(other.lights)
                && this.width == other.width && this.height == other.height
                && Objects.equals(this.renderer, other.renderer) && this.quality == other.quality
                && this.hideCeilings == other.hideCeilings && this.isolateLevel == other.isolateLevel
                && this.capLight == other.capLight && this.noiseThreshold == other.noiseThreshold;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.home, this.output, this.floors, this.dates, this.times, this.lights,
                this.width, this.height, this.renderer);
    }

    @Override
    public String toString() {
        return InstructionsJson.write(this);
    }

    /**
     * A floor to export and the stored camera it's seen from.
     */
    public static final class Floor {
        public final Ref level;
        /** The stored camera, or <code>null</code> for the current view of the home. */
        public final Ref camera;

        public Floor(Ref level, Ref camera) {
            if (level == null) {
                throw new IllegalArgumentException("A floor needs a level");
            }
            this.level = level;
            this.camera = camera;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Floor && this.level.equals(((Floor)obj).level)
                    && Objects.equals(this.camera, ((Floor)obj).camera);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.level, this.camera);
        }
    }

    public static final class Builder {
        private String home;
        private String output;
        private boolean allFloors;
        private List<Floor> floors = Collections.emptyList();
        private DateSchedule dates;
        private TimeSchedule times;
        private boolean allLights = true;
        private List<Ref> lights = Collections.emptyList();
        private int width = 1920;
        private int height = 1080;
        private String renderer;
        private Quality quality = Quality.LOW;
        private boolean hideCeilings = true;
        private boolean isolateLevel;
        private LightCap capLight = LightCap.OFF;
        private int noiseThreshold = 6;

        private Builder() {
        }

        public Builder home(String home) {
            this.home = home;
            return this;
        }

        public Builder output(String output) {
            this.output = output;
            return this;
        }

        public Builder allFloors() {
            this.allFloors = true;
            this.floors = Collections.emptyList();
            return this;
        }

        public Builder floors(List<Floor> floors) {
            this.allFloors = false;
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

        public Builder allLights() {
            this.allLights = true;
            this.lights = Collections.emptyList();
            return this;
        }

        public Builder lights(List<Ref> lights) {
            this.allLights = false;
            this.lights = lights;
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

        public Builder renderer(String renderer) {
            this.renderer = renderer;
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

        public Builder capLight(LightCap capLight) {
            this.capLight = capLight;
            return this;
        }

        public Builder noiseThreshold(int noiseThreshold) {
            this.noiseThreshold = noiseThreshold;
            return this;
        }

        public Instructions build() {
            if (this.dates == null || this.times == null) {
                throw new IllegalStateException("Dates and times are required");
            }
            return new Instructions(this);
        }
    }
}
