package io.github.valsr.hafloorplan.engine;

import java.awt.image.BufferedImage;

/**
 * Computes the transparent image that holds only what a light adds to a scene.
 */
public final class OverlayDiff {
    private OverlayDiff() {
    }

    /**
     * Returns the overlay which, drawn over <code>nightBase</code>, gives <code>lit</code>. Each pixel gets
     * the smallest opacity able to reach the lit color. Pixels brightened by <code>threshold</code> or less
     * on every channel are left fully transparent to keep render noise out of the overlay.
     * @throws IllegalArgumentException if the images don't have the same size
     */
    public static BufferedImage diff(BufferedImage nightBase, BufferedImage lit, int threshold) {
        int width = nightBase.getWidth();
        int height = nightBase.getHeight();
        if (lit.getWidth() != width || lit.getHeight() != height) {
            throw new IllegalArgumentException("Images of different sizes: " + width + "x" + height
                    + " and " + lit.getWidth() + "x" + lit.getHeight());
        }
        BufferedImage overlay = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int [] basePixels = nightBase.getRGB(0, 0, width, height, null, 0, width);
        int [] litPixels = lit.getRGB(0, 0, width, height, null, 0, width);
        int [] overlayPixels = new int [basePixels.length];
        for (int i = 0; i < basePixels.length; i++) {
            overlayPixels [i] = diff(basePixels [i], litPixels [i], threshold);
        }
        overlay.setRGB(0, 0, width, height, overlayPixels, 0, width);
        return overlay;
    }

    private static int diff(int base, int lit, int threshold) {
        int maxDifference = 0;
        // Smallest opacity a such that base * (1 - a) + color * a = lit has a color within 0..255
        double opacity = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int b = base >> shift & 0xFF;
            int difference = (lit >> shift & 0xFF) - b;
            maxDifference = Math.max(maxDifference, difference);
            if (b < 255) {
                opacity = Math.max(opacity, (double)difference / (255 - b));
            }
        }
        if (maxDifference <= threshold) {
            return 0;
        }
        int alpha = Math.max(1, (int)Math.round(Math.min(1, opacity) * 255));
        int argb = alpha << 24;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int b = base >> shift & 0xFF;
            int l = lit >> shift & 0xFF;
            long color = Math.round(b + (l - b) * 255. / alpha);
            argb |= (int)Math.max(0, Math.min(255, color)) << shift;
        }
        return argb;
    }
}
