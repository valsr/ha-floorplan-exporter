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
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

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
    /** Rendering parameter telling a renderer that the ceilings and levels hidden in a home still block light. */
    private static final String LIGHT_CAP_PARAMETER = "hiddenItemsBlockLight";

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
                    renderers.add(new HomeSummary.Renderer(className, renderer.getName(),
                            supportsLightCap(renderer.getClass())));
                }
                renderer.dispose();
            }
            this.renderers = Collections.unmodifiableList(renderers);
        }
        return this.renderers;
    }

    /**
     * Returns <code>true</code> if the renderer declares the parameter {@link #LIGHT_CAP_PARAMETER}
     * among its rendering parameters, in the resource bundle named after its class.
     */
    static boolean supportsLightCap(Class<?> rendererClass) {
        try {
            return ResourceBundle.getBundle(rendererClass.getName(), Locale.ROOT, getClassLoader(rendererClass))
                    .containsKey("lowQuality." + LIGHT_CAP_PARAMETER);
        } catch (MissingResourceException ex) {
            return false;
        }
    }

    private static ClassLoader getClassLoader(Class<?> rendererClass) {
        ClassLoader classLoader = rendererClass.getClassLoader();
        return classLoader != null ? classLoader : ClassLoader.getSystemClassLoader();
    }

    /**
     * Sets the light cap parameter of a renderer for its two quality levels, with the system properties
     * renderers read their parameters from, and returns the action which puts back their previous values.
     */
    static Runnable setLightCap(String rendererClassName, boolean capLight) {
        final String [] properties = {
            rendererClassName + ".lowQuality." + LIGHT_CAP_PARAMETER,
            rendererClassName + ".highQuality." + LIGHT_CAP_PARAMETER};
        final String [] previousValues = new String [properties.length];
        for (int i = 0; i < properties.length; i++) {
            previousValues [i] = System.setProperty(properties [i], String.valueOf(capLight));
        }
        return new Runnable() {
            public void run() {
                for (int i = 0; i < properties.length; i++) {
                    if (previousValues [i] != null) {
                        System.setProperty(properties [i], previousValues [i]);
                    } else {
                        System.clearProperty(properties [i]);
                    }
                }
            }
        };
    }

    @Override
    public RenderSession open(Home home, String rendererClassName, Quality quality, boolean capLight) throws IOException {
        // Kept for the whole session because a renderer may read its parameters when it renders its first image
        final Runnable restoreLightCap = capLight
                ? setLightCap(rendererClassName, true)
                : null;
        final AbstractPhotoRenderer renderer;
        try {
            renderer = AbstractPhotoRenderer.createInstance(
                    rendererClassName, home, null, AbstractPhotoRenderer.Quality.valueOf(quality.name()));
            if (!renderer.getClass().getName().equals(rendererClassName)) {
                renderer.dispose();
                throw new IOException("Renderer " + rendererClassName + " is not available");
            }
        } catch (IOException ex) {
            restore(restoreLightCap);
            throw ex;
        } catch (RuntimeException ex) {
            restore(restoreLightCap);
            throw ex;
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
                try {
                    renderer.dispose();
                } finally {
                    // Sweet Home 3D's own photo dialog mustn't inherit the parameter
                    restore(restoreLightCap);
                }
            }
        };
    }

    private static void restore(Runnable restoreLightCap) {
        if (restoreLightCap != null) {
            restoreLightCap.run();
        }
    }
}
