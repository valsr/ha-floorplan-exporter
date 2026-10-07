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
