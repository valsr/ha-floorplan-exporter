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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.util.Random;

import org.junit.jupiter.api.Test;

class OverlayDiffTest {
    private static BufferedImage pixel(int r, int g, int b) {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFF000000 | r << 16 | g << 8 | b);
        return image;
    }

    private static int alpha(int argb) {
        return argb >>> 24;
    }

    private static int channel(int argb, int shift) {
        return argb >> shift & 0xFF;
    }

    /** Returns the color of <code>overlay</code> drawn over <code>base</code>. */
    private static int composite(int base, int overlay) {
        double a = alpha(overlay) / 255.;
        int rgb = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            rgb |= (int)Math.round(channel(base, shift) * (1 - a) + channel(overlay, shift) * a) << shift;
        }
        return rgb;
    }

    private static void assertNear(int expected, int actual, String message) {
        assertTrue(Math.abs(expected - actual) <= 1, message + ": expected " + expected + " but was " + actual);
    }

    @Test
    void identicalImagesAreTransparent() {
        BufferedImage image = new BufferedImage(4, 3, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(1);
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 4; x++) {
                image.setRGB(x, y, 0xFF000000 | random.nextInt(0x1000000));
            }
        }
        BufferedImage overlay = OverlayDiff.diff(image, image, 6);
        assertEquals(BufferedImage.TYPE_INT_ARGB, overlay.getType());
        assertEquals(4, overlay.getWidth());
        assertEquals(3, overlay.getHeight());
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 4; x++) {
                assertEquals(0, alpha(overlay.getRGB(x, y)));
            }
        }
    }

    @Test
    void belowThresholdIsTransparent() {
        assertEquals(0, alpha(OverlayDiff.diff(pixel(100, 100, 100), pixel(106, 106, 106), 6).getRGB(0, 0)));
        assertTrue(alpha(OverlayDiff.diff(pixel(100, 100, 100), pixel(107, 107, 107), 6).getRGB(0, 0)) > 0);
    }

    @Test
    void compositingReproducesLitImage() {
        Random random = new Random(42);
        for (int i = 0; i < 64; i++) {
            int [] base = new int [3];
            int [] lit = new int [3];
            for (int c = 0; c < 3; c++) {
                base [c] = random.nextInt(240);
                lit [c] = base [c] + random.nextInt(256 - base [c]);
            }
            lit [0] = Math.max(lit [0], base [0] + 10);
            BufferedImage basePixel = pixel(base [0], base [1], base [2]);
            int overlay = OverlayDiff.diff(basePixel, pixel(lit [0], lit [1], lit [2]), 6).getRGB(0, 0);
            int result = composite(basePixel.getRGB(0, 0), overlay);
            for (int c = 0; c < 3; c++) {
                assertNear(lit [c], channel(result, 16 - 8 * c), "pair " + i + " channel " + c);
            }
        }
    }

    @Test
    void usesMinimalAlpha() {
        int overlay = OverlayDiff.diff(pixel(0, 0, 0), pixel(128, 64, 0), 6).getRGB(0, 0);
        assertEquals(128, alpha(overlay));
        assertNear(255, channel(overlay, 16), "red");
        assertNear(128, channel(overlay, 8), "green");
        assertNear(0, channel(overlay, 0), "blue");
    }

    @Test
    void saturatedBaseChannel() {
        int overlay = OverlayDiff.diff(pixel(255, 10, 10), pixel(255, 60, 10), 6).getRGB(0, 0);
        assertNear(52, alpha(overlay), "alpha");
        assertEquals(255, channel(overlay, 8));
    }

    @Test
    void darkerLitPixelIsTransparent() {
        assertEquals(0, alpha(OverlayDiff.diff(pixel(100, 100, 100), pixel(50, 60, 70), 6).getRGB(0, 0)));
    }

    @Test
    void sizeMismatchThrows() {
        assertThrows(IllegalArgumentException.class, () -> OverlayDiff.diff(
                new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB), new BufferedImage(2, 3, BufferedImage.TYPE_INT_ARGB), 6));
    }
}
