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
import java.io.Closeable;
import java.io.IOException;

import com.eteks.sweethome3d.model.Camera;

/**
 * A renderer loaded with one state of a home. The home must not change while the session is open;
 * only the camera, including its time, may differ from one image to the next.
 */
public interface RenderSession extends Closeable {
    /**
     * Returns the image of the home seen from <code>camera</code>, of type <code>TYPE_INT_ARGB</code>.
     */
    BufferedImage render(Camera camera, int width, int height) throws IOException;

    /**
     * Interrupts the image being rendered. May be called from another thread.
     */
    void stop();

    @Override
    void close();
}
