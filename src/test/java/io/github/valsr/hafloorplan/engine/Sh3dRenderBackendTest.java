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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.eteks.sweethome3d.j3d.PhotoRenderer;

import com.eteks.sweethome3d.model.Home;

import io.github.valsr.hafloorplan.plan.LightCap;
import io.github.valsr.hafloorplan.plan.Quality;

class Sh3dRenderBackendTest {
    private static final String LOW = "x.Y.lowQuality.hiddenItemsBlockLight";
    private static final String HIGH = "x.Y.highQuality.hiddenItemsBlockLight";

    @AfterEach
    void clearProperties() {
        System.clearProperty(LOW);
        System.clearProperty(HIGH);
    }

    @Test
    void sunFlowDoesNotSupportTheCap() {
        assertFalse(Sh3dRenderBackend.supportsLightCap(PhotoRenderer.class));
    }

    @Test
    void rendererWithoutParametersDoesNotSupportTheCap() {
        // No resource bundle is named after this class
        assertFalse(Sh3dRenderBackend.supportsLightCap(String.class));
    }

    @Test
    void setLightCapSetsAndRestoresProperties() {
        System.setProperty(LOW, "keep");

        Runnable restore = Sh3dRenderBackend.setLightCap("x.Y", LightCap.SUN);
        assertEquals("sun", System.getProperty(LOW));
        assertEquals("sun", System.getProperty(HIGH));

        restore.run();
        assertEquals("keep", System.getProperty(LOW));
        assertNull(System.getProperty(HIGH));
    }

    @Test
    void noCapOverridesAParameterSetElsewhere() {
        // Sweet Home 3D may have been started with the parameter of the renderer set
        System.setProperty(LOW, "sun");
        Runnable restore = Sh3dRenderBackend.setLightCap("x.Y", LightCap.OFF);
        assertEquals("false", System.getProperty(LOW));
        assertEquals("false", System.getProperty(HIGH));
        restore.run();
        assertEquals("sun", System.getProperty(LOW));
    }

    @Test
    void failingToOpenASessionRestoresProperties() {
        System.setProperty(LOW, "keep");
        // An unknown renderer is refused once its parameter was set
        assertThrows(IOException.class,
                () -> new Sh3dRenderBackend().open(new Home(), "x.Y", Quality.LOW, LightCap.ALL));
        assertEquals("keep", System.getProperty(LOW));
        assertNull(System.getProperty(HIGH));
    }
}
