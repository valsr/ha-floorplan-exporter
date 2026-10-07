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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.Compass;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeLight;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;

/**
 * Puts the copy of a home in the state an image is rendered in. Never give it the home edited by the user.
 */
final class SceneConfigurer {
    private final Home home;
    private final Map<HomeLight, Float> lightPowers = new LinkedHashMap<HomeLight, Float>();
    private final Map<Room, Boolean> ceilingsVisible = new LinkedHashMap<Room, Boolean>();

    /**
     * @param home a clone, modified by this object
     */
    SceneConfigurer(Home home) {
        this.home = home;
        for (HomeLight light : HomeInspector.getLights(home)) {
            this.lightPowers.put(light, light.getPower());
        }
        for (Room room : home.getRooms()) {
            this.ceilingsVisible.put(room, room.isCeilingVisible());
        }
    }

    /**
     * Shows the floor <code>floorId</code> with the levels under it, or alone if <code>isolate</code> is
     * <code>true</code>, the way Sweet Home 3D does when a level is selected: through the visible flag of levels,
     * which renderers test. Ceilings of the floor are hidden if <code>hideCeilings</code> is <code>true</code>.
     */
    void showFloor(String floorId, boolean isolate, boolean hideCeilings) {
        Level floor = null;
        for (Level level : this.home.getLevels()) {
            if (level.getId().equals(floorId)) {
                floor = level;
            }
        }
        if (floor != null) {
            boolean under = true;
            for (Level level : this.home.getLevels()) {
                level.setVisible(isolate ? level == floor : under);
                if (level == floor) {
                    under = false;
                }
            }
            this.home.setSelectedLevel(floor);
        }
        for (Map.Entry<Room, Boolean> ceiling : this.ceilingsVisible.entrySet()) {
            Room room = ceiling.getKey();
            // In a home without levels, the only floor has all the rooms
            boolean onFloor = floor == null || room.getLevel() == floor;
            room.setCeilingVisible(ceiling.getValue() && !(hideCeilings && onFloor));
        }
    }

    /**
     * Turns off all the lights except <code>litLightId</code>, which gets back the power it has in the home.
     * @param litLightId the id of a light, or <code>null</code> to turn off all of them
     */
    void setLights(String litLightId) {
        for (Map.Entry<HomeLight, Float> light : this.lightPowers.entrySet()) {
            light.getKey().setPower(light.getKey().getId().equals(litLightId) ? light.getValue() : 0);
        }
    }

    /**
     * Returns a copy of the stored camera <code>cameraId</code>, or of the current camera of the home
     * if <code>cameraId</code> is <code>null</code>.
     */
    Camera camera(String cameraId) {
        if (cameraId == null) {
            return this.home.getCamera().clone();
        }
        for (Camera camera : this.home.getStoredCameras()) {
            if (camera.getId().equals(cameraId)) {
                return camera.clone();
            }
        }
        throw new IllegalArgumentException("No stored camera " + cameraId);
    }

    /**
     * Returns the time to give a camera to see the home at a date and time of the place it's located at.
     * Sweet Home 3D stores this local time as if it were UTC, and renderers convert it
     * with <code>Camera.convertTimeToTimeZone</code> and the time zone of the compass.
     */
    static long cameraTime(LocalDate date, LocalTime time) {
        return LocalDateTime.of(date, time).toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    /**
     * Returns the elevation of the sun above the horizon in degrees at the given local time.
     */
    float sunElevation(LocalDateTime time) {
        Compass compass = this.home.getCompass();
        long utcTime = Camera.convertTimeToTimeZone(cameraTime(time.toLocalDate(), time.toLocalTime()), compass.getTimeZone());
        return (float)Math.toDegrees(compass.getSunElevation(utcTime));
    }

    /**
     * Returns the time lights are rendered at: midnight of <code>date</code> or, where the sun is still up
     * at midnight, the hour of that day when it's the lowest.
     */
    LocalDateTime nightTime(LocalDate date) {
        LocalDateTime night = date.atStartOfDay();
        if (sunElevation(night) >= 0) {
            for (int hour = 1; hour < 24; hour++) {
                LocalDateTime time = date.atTime(hour, 0);
                if (sunElevation(time) < sunElevation(night)) {
                    night = time;
                }
            }
        }
        return night;
    }
}
