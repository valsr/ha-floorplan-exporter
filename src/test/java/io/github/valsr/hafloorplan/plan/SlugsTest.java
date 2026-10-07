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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SlugsTest {
    @Test
    void lowerCasesAndJoinsWords() {
        assertEquals("kitchen-lamp", Slugs.slug("Kitchen Lamp"));
    }

    @Test
    void stripsAccentsAndPunctuation() {
        assertEquals("ete-2", Slugs.slug("  Été / 2 "));
    }

    @Test
    void cannotEscapeTheFolder() {
        assertEquals("x", Slugs.slug("../x"));
    }

    @Test
    void namesWithoutUsableCharactersBecomeItem() {
        assertEquals("item", Slugs.slug(""));
        assertEquals("item", Slugs.slug(null));
        assertEquals("item", Slugs.slug("Лампа"));
    }

    @Test
    void collisionsGetNumericSuffixes() {
        Slugs slugs = new Slugs();
        assertEquals("lamp", slugs.unique("Lamp"));
        assertEquals("lamp-2", slugs.unique("Lamp"));
        assertEquals("lamp-3", slugs.unique("lamp"));
        assertEquals("lamp-2-2", slugs.unique("lamp-2"));
    }
}
