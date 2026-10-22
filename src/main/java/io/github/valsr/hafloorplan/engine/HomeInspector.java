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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomeLight;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Wall;

import io.github.valsr.hafloorplan.plan.HomeSummary;

/**
 * Extracts from a home what planning an export needs.
 */
public final class HomeInspector {
    private HomeInspector() {
    }

    /**
     * Returns the floors, lights and named stored cameras of <code>home</code> with the renderers
     * of <code>backend</code>. A home without levels has one floor of id {@link HomeSummary#DEFAULT_FLOOR_ID}.
     */
    public static HomeSummary summarize(Home home, RenderBackend backend) {
        List<HomeSummary.Floor> floors = new ArrayList<HomeSummary.Floor>();
        for (Level level : home.getLevels()) {
            floors.add(new HomeSummary.Floor(level.getId(), level.getName()));
        }
        boolean levels = !floors.isEmpty();
        if (!levels) {
            String name = "Home";
            if (home.getName() != null) {
                name = new File(home.getName()).getName().replaceFirst("\\.[^.]*$", "");
            }
            floors.add(new HomeSummary.Floor(HomeSummary.DEFAULT_FLOOR_ID, name));
        }

        List<HomeSummary.Light> lights = new ArrayList<HomeSummary.Light>();
        for (HomeLight light : getLights(home)) {
            String floorId = levels
                    ? (light.getLevel() != null ? light.getLevel().getId() : null)
                    : HomeSummary.DEFAULT_FLOOR_ID;
            lights.add(new HomeSummary.Light(light.getId(), light.getName(), floorId, light.getPower()));
        }

        List<HomeSummary.Camera> cameras = new ArrayList<HomeSummary.Camera>();
        for (Camera camera : home.getStoredCameras()) {
            if (camera.getName() != null) {
                cameras.add(new HomeSummary.Camera(camera.getId(), camera.getName()));
            }
        }
        return new HomeSummary(floors, lights, cameras, backend.availableRenderers());
    }

    /**
     * Returns the corners of the walls of the floor <code>floorId</code> as <code>{x, y, elevation}</code>:
     * each point of their outline, with their thickness, at the bottom and at the top of the wall. The walls
     * are the ones Sweet Home 3D shows at that floor, including those rising from a lower level, which start at
     * the elevation of the floor. A sloping wall gets its higher end everywhere. In a home without levels, every
     * wall counts, from elevation 0.
     */
    static List<double []> getWallCorners(Home home, String floorId) {
        Level floor = null;
        for (Level level : home.getLevels()) {
            if (level.getId().equals(floorId)) {
                floor = level;
            }
        }
        boolean allWalls = HomeSummary.DEFAULT_FLOOR_ID.equals(floorId) && home.getLevels().isEmpty();
        List<double []> corners = new ArrayList<double []>();
        for (Wall wall : home.getWalls()) {
            if (!allWalls && (floor == null || !wall.isAtLevel(floor))) {
                continue;
            }
            Level level = wall.getLevel();
            double base = level != null ? level.getElevation() : 0;
            float height = wall.getHeight() != null ? wall.getHeight() : home.getWallHeight();
            if (wall.getHeightAtEnd() != null) {
                height = Math.max(height, wall.getHeightAtEnd());
            }
            double bottom = floor != null ? Math.max(base, floor.getElevation()) : base;
            for (float [] point : wall.getPoints()) {
                corners.add(new double [] {point [0], point [1], bottom});
                corners.add(new double [] {point [0], point [1], base + height});
            }
        }
        return corners;
    }

    /**
     * Returns all the lights of <code>home</code>, including the ones in groups of furniture.
     */
    static List<HomeLight> getLights(Home home) {
        List<HomeLight> lights = new ArrayList<HomeLight>();
        addLights(home.getFurniture(), lights);
        return lights;
    }

    private static void addLights(List<HomePieceOfFurniture> furniture, List<HomeLight> lights) {
        for (HomePieceOfFurniture piece : furniture) {
            if (piece instanceof HomeFurnitureGroup) {
                addLights(((HomeFurnitureGroup)piece).getFurniture(), lights);
            } else if (piece instanceof HomeLight) {
                lights.add((HomeLight)piece);
            }
        }
    }
}
