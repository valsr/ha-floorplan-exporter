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

import java.util.Locale;

/**
 * What the ceilings and levels hidden to see a floor from above still block, without being seen.
 */
public enum LightCap {
    /** Nothing: light falls in the floor from where the camera looks. */
    OFF,
    /** The direct light of the sun, which then comes in only through windows and openings. */
    SUN,
    /** All light, as if the home was complete. */
    ALL;

    /**
     * Returns the name of this value in instructions files, manifests and renderer parameters.
     */
    public String toText() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Returns the value named <code>text</code> in instructions files, or <code>null</code> if there's none.
     */
    public static LightCap fromText(String text) {
        for (LightCap value : values()) {
            if (value.toText().equals(text)) {
                return value;
            }
        }
        return null;
    }
}
