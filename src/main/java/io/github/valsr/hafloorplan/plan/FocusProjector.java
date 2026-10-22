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
package io.github.valsr.hafloorplan.plan;

import java.util.List;

/**
 * Computes the focus box of a floor: where its wall corners land in an image, through the pinhole camera
 * the Blender GPU renderer builds from a Sweet Home 3D camera.
 */
public final class FocusProjector {
    /** Depth under which a corner counts as level with the camera, where the projection doesn't hold. */
    private static final double MIN_DEPTH = 1E-6;
    /** Rounding error ignored before rounding outwards, so that an edge computed as 99.99999 stays at 100. */
    private static final double ROUNDING_TOLERANCE = 1E-6;

    private FocusProjector() {
    }

    /**
     * Returns the box of <code>corners</code> seen by a camera, widened by <code>marginPercent</code> % of its
     * width and height on each side, rounded outwards and clipped to the image, or <code>null</code> if there are
     * no corners, one is level with or behind the camera, or the box lies outside the image.
     * @param x the camera abscissa in the plan
     * @param y the camera ordinate in the plan
     * @param z the camera elevation
     * @param fieldOfView the horizontal field of view in radians
     * @param corners points as <code>{x, y, elevation}</code>, in the units of the camera
     */
    public static FocusBox project(double x, double y, double z, double yaw, double pitch, double fieldOfView,
                                   int width, int height, List<double []> corners, double marginPercent) {
        if (corners.isEmpty()) {
            return null;
        }
        // Sweet Home 3D's 3D frame is (x, elevation, y); these are the vectors BlenderRenderer gives Blender,
        // whose worker takes right = forward x up
        double [] forward = {-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)};
        double [] up = {-Math.sin(yaw) * Math.sin(pitch), Math.cos(pitch), Math.cos(yaw) * Math.sin(pitch)};
        double [] side = cross(forward, up);
        double scale = width / 2. / Math.tan(fieldOfView / 2);

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (double [] corner : corners) {
            double [] d = {corner [0] - x, corner [2] - z, corner [1] - y};
            double depth = dot(d, forward);
            if (depth <= MIN_DEPTH) {
                return null;
            }
            double px = width / 2. + scale * dot(d, side) / depth;
            double py = height / 2. - scale * dot(d, up) / depth;
            minX = Math.min(minX, px);
            maxX = Math.max(maxX, px);
            minY = Math.min(minY, py);
            maxY = Math.max(maxY, py);
        }

        double marginX = (maxX - minX) * marginPercent / 100;
        double marginY = (maxY - minY) * marginPercent / 100;
        int left = (int)Math.max(0, Math.floor(minX - marginX + ROUNDING_TOLERANCE));
        int top = (int)Math.max(0, Math.floor(minY - marginY + ROUNDING_TOLERANCE));
        int right = (int)Math.min(width, Math.ceil(maxX + marginX - ROUNDING_TOLERANCE));
        int bottom = (int)Math.min(height, Math.ceil(maxY + marginY - ROUNDING_TOLERANCE));
        return left < right && top < bottom ? new FocusBox(left, top, right, bottom) : null;
    }

    private static double [] cross(double [] a, double [] b) {
        return new double [] {a [1] * b [2] - a [2] * b [1], a [2] * b [0] - a [0] * b [2], a [0] * b [1] - a [1] * b [0]};
    }

    private static double dot(double [] a, double [] b) {
        return a [0] * b [0] + a [1] * b [1] + a [2] * b [2];
    }
}
