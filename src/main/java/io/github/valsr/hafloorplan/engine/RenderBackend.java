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

import java.io.IOException;
import java.util.List;

import com.eteks.sweethome3d.model.Home;

import io.github.valsr.hafloorplan.plan.HomeSummary;
import io.github.valsr.hafloorplan.plan.LightCap;
import io.github.valsr.hafloorplan.plan.Quality;

/**
 * Gives access to the renderers able to compute images of a home.
 */
public interface RenderBackend {
    /**
     * Returns the renderers that can run in the current environment.
     */
    List<HomeSummary.Renderer> availableRenderers();

    /**
     * Returns a session rendering <code>home</code> in its current state.
     * @param capLight what the ceilings and levels hidden in <code>home</code> still block without being seen;
     *     only renderers with {@link HomeSummary.Renderer#supportsLightCap} accept another value than
     *     {@link LightCap#OFF}
     * @param exposure the exposure of images in stops, each one doubling their brightness; only renderers with
     *     {@link HomeSummary.Renderer#supportsExposure} accept another value than 0
     */
    RenderSession open(Home home, String rendererClassName, Quality quality, LightCap capLight,
                       double exposure) throws IOException;
}
