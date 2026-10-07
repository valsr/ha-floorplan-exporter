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

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.eteks.sweethome3d.j3d.AbstractPhotoRenderer;
import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.Home;

import io.github.valsr.hafloorplan.plan.HomeSummary;
import io.github.valsr.hafloorplan.plan.Quality;

/**
 * Renders with the photo renderers of Sweet Home 3D: SunFlow, YafaRay and any other one declared
 * with the <code>com.eteks.sweethome3d.j3d.rendererClassNames</code> system property,
 * like the Blender GPU renderer once its Java agent is loaded.
 */
public final class Sh3dRenderBackend implements RenderBackend {
    private List<HomeSummary.Renderer> renderers;

    @Override
    public synchronized List<HomeSummary.Renderer> availableRenderers() {
        if (this.renderers == null) {
            List<HomeSummary.Renderer> renderers = new ArrayList<HomeSummary.Renderer>();
            for (String className : AbstractPhotoRenderer.getAvailableRenderers()) {
                // createInstance returns a SunFlow renderer for a class it can't use
                AbstractPhotoRenderer renderer = AbstractPhotoRenderer.createInstance(
                        className, new Home(), null, AbstractPhotoRenderer.Quality.LOW);
                if (renderer.getClass().getName().equals(className) && renderer.isAvailable()) {
                    renderers.add(new HomeSummary.Renderer(className, renderer.getName()));
                }
                renderer.dispose();
            }
            this.renderers = Collections.unmodifiableList(renderers);
        }
        return this.renderers;
    }

    @Override
    public RenderSession open(Home home, String rendererClassName, Quality quality) throws IOException {
        final AbstractPhotoRenderer renderer = AbstractPhotoRenderer.createInstance(
                rendererClassName, home, null, AbstractPhotoRenderer.Quality.valueOf(quality.name()));
        if (!renderer.getClass().getName().equals(rendererClassName)) {
            renderer.dispose();
            throw new IOException("Renderer " + rendererClassName + " is not available");
        }
        return new RenderSession() {
            @Override
            public BufferedImage render(Camera camera, int width, int height) throws IOException {
                BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                renderer.render(image, camera, null);
                return image;
            }

            @Override
            public void stop() {
                renderer.stop();
            }

            @Override
            public void close() {
                renderer.dispose();
            }
        };
    }
}
