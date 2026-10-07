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

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Turns names into file-system safe slugs. One instance hands out slugs that are
 * unique among the names it was given.
 */
public final class Slugs {
    private static final String EMPTY_SLUG = "item";

    private final Set<String> used = new HashSet<String>();

    /**
     * Returns the slug of <code>name</code>, made of <code>a-z</code>, <code>0-9</code> and <code>-</code>.
     */
    public static String slug(String name) {
        if (name == null) {
            return EMPTY_SLUG;
        }
        String slug = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug.isEmpty() ? EMPTY_SLUG : slug;
    }

    /**
     * Returns the slug of <code>name</code>, with a numeric suffix if an earlier name got the same one.
     */
    public String unique(String name) {
        String base = slug(name);
        String candidate = base;
        for (int i = 2; !this.used.add(candidate); i++) {
            candidate = base + "-" + i;
        }
        return candidate;
    }
}
