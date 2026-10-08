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

/**
 * What planning an export needs to know about a home, free of Sweet Home 3D classes.
 */
public final class HomeSummary {
    /** Id of the only floor of a home without levels. */
    public static final String DEFAULT_FLOOR_ID = "default";

    private final List<Floor> floors;
    private final List<Light> lights;
    private final List<Camera> cameras;
    private final List<Renderer> renderers;

    /**
     * @param floors the floors from the lowest to the highest
     */
    public HomeSummary(List<Floor> floors, List<Light> lights, List<Camera> cameras, List<Renderer> renderers) {
        this.floors = Collections.unmodifiableList(new ArrayList<Floor>(floors));
        this.lights = Collections.unmodifiableList(new ArrayList<Light>(lights));
        this.cameras = Collections.unmodifiableList(new ArrayList<Camera>(cameras));
        this.renderers = Collections.unmodifiableList(new ArrayList<Renderer>(renderers));
    }

    public List<Floor> getFloors() {
        return this.floors;
    }

    public List<Light> getLights() {
        return this.lights;
    }

    public List<Camera> getCameras() {
        return this.cameras;
    }

    public List<Renderer> getRenderers() {
        return this.renderers;
    }

    public Floor floor(String id) {
        for (Floor floor : this.floors) {
            if (floor.id.equals(id)) {
                return floor;
            }
        }
        return null;
    }

    public Light light(String id) {
        for (Light light : this.lights) {
            if (light.id.equals(id)) {
                return light;
            }
        }
        return null;
    }

    public Camera camera(String id) {
        for (Camera camera : this.cameras) {
            if (camera.id.equals(id)) {
                return camera;
            }
        }
        return null;
    }

    public Renderer renderer(String className) {
        for (Renderer renderer : this.renderers) {
            if (renderer.className.equals(className)) {
                return renderer;
            }
        }
        return null;
    }

    public static final class Floor {
        public final String id;
        public final String name;

        public Floor(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static final class Light {
        public final String id;
        public final String name;
        /** Id of the floor the light is on, or <code>null</code> if it's on none. */
        public final String floorId;
        /** Power of the light in the home, 0 if it's turned off. */
        public final float power;

        /**
         * Creates a light turned on.
         */
        public Light(String id, String name, String floorId) {
            this(id, name, floorId, 1);
        }

        public Light(String id, String name, String floorId, float power) {
            this.id = id;
            this.name = name;
            this.floorId = floorId;
            this.power = power;
        }
    }

    public static final class Camera {
        public final String id;
        public final String name;

        public Camera(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static final class Renderer {
        public final String className;
        public final String displayName;
        /** <code>true</code> if the renderer can keep hidden ceilings and levels as invisible light blockers. */
        public final boolean supportsLightCap;

        /**
         * Creates a renderer unable to cap light.
         */
        public Renderer(String className, String displayName) {
            this(className, displayName, false);
        }

        public Renderer(String className, String displayName, boolean supportsLightCap) {
            this.className = className;
            this.displayName = displayName;
            this.supportsLightCap = supportsLightCap;
        }
    }
}
