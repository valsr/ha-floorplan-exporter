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

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomeLight;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;

import io.github.valsr.hafloorplan.plan.HomeSummary;
import io.github.valsr.hafloorplan.plan.LightCap;
import io.github.valsr.hafloorplan.plan.Quality;

/**
 * A backend that renders flat grey images, brighter with each light turned on,
 * and records the state of the home each session was opened with.
 */
public class FakeRenderBackend implements RenderBackend {
    public static final String RENDERER = "fake.Renderer";

    public final List<Session> sessions = new ArrayList<Session>();
    /** Index, among all renders, of the one that throws an exception, or -1. */
    public int failOnRender = -1;
    /** <code>true</code> if the fake renderer claims it can cap light. */
    public boolean supportsLightCap;
    /** Run at the beginning of each render. */
    public Runnable onRender;
    private int renderCount;

    @Override
    public List<HomeSummary.Renderer> availableRenderers() {
        return Arrays.asList(new HomeSummary.Renderer(RENDERER, "Fake", this.supportsLightCap));
    }

    @Override
    public RenderSession open(Home home, String rendererClassName, Quality quality, LightCap capLight) {
        Session session = new Session(home, rendererClassName, quality, capLight);
        this.sessions.add(session);
        return session;
    }

    static void addLights(List<HomePieceOfFurniture> furniture, List<HomeLight> lights) {
        for (HomePieceOfFurniture piece : furniture) {
            if (piece instanceof HomeFurnitureGroup) {
                addLights(((HomeFurnitureGroup)piece).getFurniture(), lights);
            } else if (piece instanceof HomeLight) {
                lights.add((HomeLight)piece);
            }
        }
    }

    public class Session implements RenderSession {
        public final String rendererClassName;
        public final Quality quality;
        public final LightCap capLight;
        /** Power of each light by name when the session was opened. */
        public final Map<String, Float> lightPowers = new LinkedHashMap<String, Float>();
        /** Visibility of each level by name when the session was opened. */
        public final Map<String, Boolean> levelsVisible = new LinkedHashMap<String, Boolean>();
        /** Visibility of the ceiling of each room by name when the session was opened. */
        public final Map<String, Boolean> ceilingsVisible = new LinkedHashMap<String, Boolean>();
        public final List<Long> cameraTimes = new ArrayList<Long>();
        public final List<Float> cameraHeights = new ArrayList<Float>();
        public boolean stopped;
        public int closeCount;
        private int litCount;

        Session(Home home, String rendererClassName, Quality quality, LightCap capLight) {
            this.rendererClassName = rendererClassName;
            this.quality = quality;
            this.capLight = capLight;
            List<HomeLight> lights = new ArrayList<HomeLight>();
            addLights(home.getFurniture(), lights);
            for (HomeLight light : lights) {
                this.lightPowers.put(light.getName(), light.getPower());
                if (light.getPower() > 0) {
                    this.litCount++;
                }
            }
            for (Level level : home.getLevels()) {
                this.levelsVisible.put(level.getName(), level.isVisible());
            }
            for (Room room : home.getRooms()) {
                this.ceilingsVisible.put(room.getName(), room.isCeilingVisible());
            }
        }

        @Override
        public BufferedImage render(Camera camera, int width, int height) throws IOException {
            if (FakeRenderBackend.this.onRender != null) {
                FakeRenderBackend.this.onRender.run();
            }
            if (FakeRenderBackend.this.renderCount++ == FakeRenderBackend.this.failOnRender) {
                throw new IOException("Fake render failure");
            }
            this.cameraTimes.add(camera.getTime());
            this.cameraHeights.add(camera.getZ());
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            int grey = 40 + 60 * this.litCount;
            Graphics2D g = image.createGraphics();
            g.setColor(new java.awt.Color(grey, grey, grey));
            g.fillRect(0, 0, width, height);
            g.dispose();
            return image;
        }

        @Override
        public void stop() {
            this.stopped = true;
        }

        @Override
        public void close() {
            this.closeCount++;
        }
    }
}
