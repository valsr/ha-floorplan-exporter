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

import io.github.valsr.hafloorplan.plan.RenderJob;

/**
 * Follows the progress of an export. Called in the thread running the export.
 */
public interface ExportListener {
    /**
     * @param index index of the job, starting at 0
     */
    void jobStarted(int index, int total, RenderJob job);

    void jobFinished(int index, int total, RenderJob job);
}
