package io.github.valsr.hafloorplan.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class JsonTest {
    @Test
    @SuppressWarnings("unchecked")
    void roundTrip() {
        Map<String, Object> nested = new LinkedHashMap<String, Object>();
        nested.put("b", "x");
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("map", nested);
        value.put("list", Arrays.asList(1, 1.5, -2e3, true, false, null));
        value.put("text", "a\"b\\c\né\u0001");
        value.put("empty", new LinkedHashMap<String, Object>());
        value.put("none", Arrays.asList());

        Map<String, Object> read = (Map<String, Object>)Json.parse(Json.write(value));

        assertEquals(Arrays.asList("map", "list", "text", "empty", "none"), Arrays.asList(read.keySet().toArray()));
        assertEquals(nested, read.get("map"));
        assertEquals(Arrays.asList(1.0, 1.5, -2000.0, true, false, null), read.get("list"));
        assertEquals("a\"b\\c\né\u0001", read.get("text"));
        assertTrue(((Map<String, Object>)read.get("empty")).isEmpty());
        assertTrue(((List<Object>)read.get("none")).isEmpty());
    }

    @Test
    void integralNumbersHaveNoFraction() {
        assertEquals("6\n", Json.write(6.0));
        assertEquals("6\n", Json.write(6));
        assertEquals("-58.2\n", Json.write(-58.2));
    }

    @Test
    void parsesEscapes() {
        assertEquals("é\t/", Json.parse("\"\\u00e9\\t\\/\""));
    }

    @Test
    void parsesLiterals() {
        assertNull(Json.parse("null"));
        assertEquals(Boolean.TRUE, Json.parse("true"));
        assertEquals(-12.5, Json.parse("-12.5"));
    }

    @Test
    void toleratesBomAndWhitespace() {
        assertEquals(new LinkedHashMap<String, Object>(), Json.parse("﻿\n\n  {}\n\n"));
    }

    @Test
    void reportsPosition() {
        JsonException trailingComma = assertThrows(JsonException.class, () -> Json.parse("{\n  \"a\": 1,\n}"));
        assertEquals(3, trailingComma.getLine());
        assertEquals(1, trailingComma.getColumn());
        assertTrue(trailingComma.getMessage().endsWith(" at line 3, column 1"), trailingComma.getMessage());

        JsonException missingColon = assertThrows(JsonException.class, () -> Json.parse("{\"a\" 1}"));
        assertEquals(1, missingColon.getLine());
        assertEquals(6, missingColon.getColumn());
    }

    @Test
    void rejectsMalformedInput() {
        for (String text : new String[] {"{} x", "{\"a\": 1, \"a\": 2}", "\"abc", "NaN", "// c\n{}", "", "[1 2]", "01"}) {
            assertThrows(JsonException.class, () -> Json.parse(text), text);
        }
    }

    @Test
    void writeRejectsUnknownTypes() {
        assertThrows(IllegalArgumentException.class, () -> Json.write(new Object()));
    }
}
