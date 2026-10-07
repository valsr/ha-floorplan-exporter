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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal JSON reader and writer, enough for the instructions file and the manifest.
 */
public final class Json {
    private final String text;
    private int index;

    private Json(String text) {
        this.text = text;
    }

    /**
     * Returns the value described by <code>text</code>: a <code>LinkedHashMap</code>, an <code>ArrayList</code>,
     * a <code>String</code>, a <code>Double</code>, a <code>Boolean</code> or <code>null</code>.
     * @throws JsonException if <code>text</code> isn't valid JSON
     */
    public static Object parse(String text) {
        Json parser = new Json(text);
        if (text.startsWith("﻿")) {
            parser.index = 1;
        }
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (parser.index < text.length()) {
            throw parser.error("Unexpected character '" + text.charAt(parser.index) + "'");
        }
        return value;
    }

    private Object readValue() {
        skipWhitespace();
        if (this.index >= this.text.length()) {
            throw error("Unexpected end of text");
        }
        char c = this.text.charAt(this.index);
        if (c == '{') {
            return readObject();
        } else if (c == '[') {
            return readArray();
        } else if (c == '"') {
            return readString();
        } else if (c == '-' || c >= '0' && c <= '9') {
            return readNumber();
        } else if (this.text.startsWith("true", this.index)) {
            this.index += 4;
            return Boolean.TRUE;
        } else if (this.text.startsWith("false", this.index)) {
            this.index += 5;
            return Boolean.FALSE;
        } else if (this.text.startsWith("null", this.index)) {
            this.index += 4;
            return null;
        }
        throw error("Unexpected character '" + c + "'");
    }

    private Map<String, Object> readObject() {
        Map<String, Object> object = new LinkedHashMap<String, Object>();
        this.index++;
        skipWhitespace();
        if (peek() == '}') {
            this.index++;
            return object;
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                throw error("Expected a key in double quotes");
            }
            int keyIndex = this.index;
            String key = readString();
            if (object.containsKey(key)) {
                this.index = keyIndex;
                throw error("Duplicate key \"" + key + "\"");
            }
            skipWhitespace();
            if (peek() != ':') {
                throw error("Expected ':'");
            }
            this.index++;
            object.put(key, readValue());
            skipWhitespace();
            char c = peek();
            this.index++;
            if (c == '}') {
                return object;
            } else if (c != ',') {
                this.index--;
                throw error("Expected ',' or '}'");
            }
        }
    }

    private List<Object> readArray() {
        List<Object> array = new ArrayList<Object>();
        this.index++;
        skipWhitespace();
        if (peek() == ']') {
            this.index++;
            return array;
        }
        while (true) {
            array.add(readValue());
            skipWhitespace();
            char c = peek();
            this.index++;
            if (c == ']') {
                return array;
            } else if (c != ',') {
                this.index--;
                throw error("Expected ',' or ']'");
            }
        }
    }

    private String readString() {
        StringBuilder string = new StringBuilder();
        this.index++;
        while (true) {
            if (this.index >= this.text.length()) {
                throw error("Unterminated string");
            }
            char c = this.text.charAt(this.index++);
            if (c == '"') {
                return string.toString();
            } else if (c < 0x20) {
                this.index--;
                throw error("Control character in string");
            } else if (c != '\\') {
                string.append(c);
            } else {
                char escaped = peek();
                this.index++;
                switch (escaped) {
                    case '"': case '\\': case '/': string.append(escaped); break;
                    case 'b': string.append('\b'); break;
                    case 'f': string.append('\f'); break;
                    case 'n': string.append('\n'); break;
                    case 'r': string.append('\r'); break;
                    case 't': string.append('\t'); break;
                    case 'u':
                        if (this.index + 4 > this.text.length()
                                || !this.text.substring(this.index, this.index + 4).matches("[0-9a-fA-F]{4}")) {
                            throw error("Invalid \\u escape");
                        }
                        string.append((char)Integer.parseInt(this.text.substring(this.index, this.index + 4), 16));
                        this.index += 4;
                        break;
                    default:
                        this.index--;
                        throw error("Invalid escape");
                }
            }
        }
    }

    private Double readNumber() {
        int start = this.index;
        if (peek() == '-') {
            this.index++;
        }
        if (peek() == '0') {
            this.index++;
        } else {
            readDigits();
        }
        if (peek() == '.') {
            this.index++;
            readDigits();
        }
        if (peek() == 'e' || peek() == 'E') {
            this.index++;
            if (peek() == '+' || peek() == '-') {
                this.index++;
            }
            readDigits();
        }
        return Double.valueOf(this.text.substring(start, this.index));
    }

    private void readDigits() {
        int start = this.index;
        while (peek() >= '0' && peek() <= '9') {
            this.index++;
        }
        if (this.index == start) {
            throw error("Expected a digit");
        }
    }

    /**
     * Returns the current character, or 0 at the end of the text.
     */
    private char peek() {
        return this.index < this.text.length() ? this.text.charAt(this.index) : 0;
    }

    private void skipWhitespace() {
        while (this.index < this.text.length() && " \t\r\n".indexOf(this.text.charAt(this.index)) >= 0) {
            this.index++;
        }
    }

    private JsonException error(String problem) {
        int line = 1;
        int column = 1;
        for (int i = 0; i < this.index && i < this.text.length(); i++) {
            if (this.text.charAt(i) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return new JsonException(problem, line, column);
    }

    /**
     * Returns <code>value</code> as indented JSON text ending with a new line. Maps keep their iteration order.
     * @throws IllegalArgumentException if <code>value</code> contains something else than maps with string keys,
     *     lists, strings, finite numbers, booleans and <code>null</code>
     */
    public static String write(Object value) {
        StringBuilder json = new StringBuilder();
        write(value, json, "");
        return json.append('\n').toString();
    }

    private static void write(Object value, StringBuilder json, String indent) {
        if (value == null || value instanceof Boolean) {
            json.append(value);
        } else if (value instanceof String) {
            writeString((String)value, json);
        } else if (value instanceof Number) {
            double number = ((Number)value).doubleValue();
            if (Double.isNaN(number) || Double.isInfinite(number)) {
                throw new IllegalArgumentException("JSON can't hold " + number);
            } else if (number == Math.rint(number) && Math.abs(number) < 1e15) {
                json.append((long)number);
            } else {
                json.append(number);
            }
        } else if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>)value;
            if (map.isEmpty()) {
                json.append("{}");
                return;
            }
            String childIndent = indent + "  ";
            String separator = "{\n";
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String)) {
                    throw new IllegalArgumentException("JSON keys must be strings: " + entry.getKey());
                }
                json.append(separator).append(childIndent);
                writeString((String)entry.getKey(), json);
                json.append(": ");
                write(entry.getValue(), json, childIndent);
                separator = ",\n";
            }
            json.append('\n').append(indent).append('}');
        } else if (value instanceof List) {
            List<?> list = (List<?>)value;
            if (list.isEmpty()) {
                json.append("[]");
                return;
            }
            String childIndent = indent + "  ";
            String separator = "[\n";
            for (Object item : list) {
                json.append(separator).append(childIndent);
                write(item, json, childIndent);
                separator = ",\n";
            }
            json.append('\n').append(indent).append(']');
        } else {
            throw new IllegalArgumentException("JSON can't hold a " + value.getClass().getName());
        }
    }

    private static void writeString(String string, StringBuilder json) {
        json.append('"');
        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            switch (c) {
                case '"': json.append("\\\""); break;
                case '\\': json.append("\\\\"); break;
                case '\n': json.append("\\n"); break;
                case '\r': json.append("\\r"); break;
                case '\t': json.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        json.append(String.format("\\u%04x", (int)c));
                    } else {
                        json.append(c);
                    }
            }
        }
        json.append('"');
    }
}
