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

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes the instructions file, a JSON object described in the README.
 */
public final class InstructionsJson {
    private static final int VERSION = 1;
    private static final List<String> KEYS = Arrays.asList("version", "home", "output", "floors", "dates", "times",
            "lights", "width", "height", "renderer", "quality", "hideCeilings", "isolateLevel", "noiseThreshold");
    private static final String ALL = "*";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final List<String> problems = new ArrayList<String>();

    private InstructionsJson() {
    }

    /**
     * Returns the instructions described by <code>json</code>.
     * @throws InstructionsException with all the problems found in <code>json</code>
     */
    public static Instructions parse(String json) {
        InstructionsJson reader = new InstructionsJson();
        Instructions instructions = reader.read(json);
        if (!reader.problems.isEmpty()) {
            throw new InstructionsException(reader.problems);
        }
        return instructions;
    }

    @SuppressWarnings("unchecked")
    private Instructions read(String json) {
        Object root;
        try {
            root = Json.parse(json);
        } catch (JsonException ex) {
            this.problems.add("Invalid JSON: " + ex.getMessage());
            return null;
        }
        if (!(root instanceof Map)) {
            this.problems.add("Instructions must be a JSON object");
            return null;
        }
        Map<String, Object> object = (Map<String, Object>)root;
        for (String key : object.keySet()) {
            if (!KEYS.contains(key)) {
                this.problems.add("Unknown key \"" + key + "\"");
            }
        }

        Integer version = readInteger(object, "version", null, VERSION, VERSION);
        if (version == null && !object.containsKey("version")) {
            this.problems.add("\"version\" is required");
        }
        Instructions.Builder builder = Instructions.builder();
        builder.home(readString(object, "home"));
        builder.output(readString(object, "output"));
        readFloors(object.get("floors"), object.containsKey("floors"), builder);
        DateSchedule dates = readDates(object.get("dates"), object.containsKey("dates"));
        TimeSchedule times = readTimes(object.get("times"), object.containsKey("times"));
        readLights(object.get("lights"), object.containsKey("lights"), builder);
        Integer width = readInteger(object, "width", 1920, 1, Integer.MAX_VALUE);
        Integer height = readInteger(object, "height", 1080, 1, Integer.MAX_VALUE);
        builder.renderer(readString(object, "renderer"));
        String quality = readString(object, "quality");
        if (quality != null) {
            try {
                builder.quality(Quality.valueOf(quality));
            } catch (IllegalArgumentException ex) {
                this.problems.add("\"quality\" must be \"LOW\" or \"HIGH\"");
            }
        }
        Boolean hideCeilings = readBoolean(object, "hideCeilings", true);
        Boolean isolateLevel = readBoolean(object, "isolateLevel", false);
        Integer noiseThreshold = readInteger(object, "noiseThreshold", 6, 0, 255);

        if (!this.problems.isEmpty()) {
            return null;
        }
        return builder.dates(dates).times(times).width(width).height(height)
                .hideCeilings(hideCeilings).isolateLevel(isolateLevel).noiseThreshold(noiseThreshold).build();
    }

    private String readString(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (value == null && !object.containsKey(key)) {
            return null;
        } else if (!(value instanceof String) || ((String)value).isEmpty()) {
            this.problems.add("\"" + key + "\" must be a non-empty string");
            return null;
        }
        return (String)value;
    }

    private Boolean readBoolean(Map<String, Object> object, String key, boolean defaultValue) {
        if (!object.containsKey(key)) {
            return defaultValue;
        } else if (!(object.get(key) instanceof Boolean)) {
            this.problems.add("\"" + key + "\" must be true or false");
            return null;
        }
        return (Boolean)object.get(key);
    }

    /**
     * Returns the integer at <code>key</code>, or <code>defaultValue</code> if the key is missing.
     */
    private Integer readInteger(Map<String, Object> object, String key, Integer defaultValue, int min, int max) {
        if (!object.containsKey(key)) {
            return defaultValue;
        }
        return toInteger(object.get(key), "\"" + key + "\"", min, max);
    }

    private Integer toInteger(Object value, String label, int min, int max) {
        if (value instanceof Double) {
            double number = (Double)value;
            if (number == Math.rint(number) && number >= min && number <= max) {
                return (int)number;
            }
        }
        if (min == max) {
            this.problems.add(label + " must be " + min);
        } else if (max == Integer.MAX_VALUE) {
            this.problems.add(label + " must be a whole number of at least " + min);
        } else {
            this.problems.add(label + " must be a whole number from " + min + " to " + max);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void readFloors(Object value, boolean present, Instructions.Builder builder) {
        if (ALL.equals(value)) {
            builder.allFloors();
        } else if (!present) {
            this.problems.add("\"floors\" is required");
        } else if (!(value instanceof List) || ((List<Object>)value).isEmpty()) {
            this.problems.add("\"floors\" must be \"*\" or a non-empty array");
        } else {
            List<Instructions.Floor> floors = new ArrayList<Instructions.Floor>();
            int index = 0;
            for (Object item : (List<Object>)value) {
                String label = "floors[" + index++ + "]";
                if (!(item instanceof Map)) {
                    this.problems.add(label + " must be an object with a \"level\"");
                    continue;
                }
                Map<String, Object> floor = (Map<String, Object>)item;
                for (String key : floor.keySet()) {
                    if (!key.equals("level") && !key.equals("camera")) {
                        this.problems.add("Unknown key \"" + key + "\" in " + label);
                    }
                }
                Ref level = null;
                if (floor.containsKey("level")) {
                    level = readRef(floor.get("level"), label + ".level");
                } else {
                    this.problems.add(label + " needs a \"level\"");
                }
                Ref camera = floor.containsKey("camera") ? readRef(floor.get("camera"), label + ".camera") : null;
                if (level != null) {
                    floors.add(new Instructions.Floor(level, camera));
                }
            }
            builder.floors(floors);
        }
    }

    @SuppressWarnings("unchecked")
    private void readLights(Object value, boolean present, Instructions.Builder builder) {
        if (!present || ALL.equals(value)) {
            builder.allLights();
        } else if (!(value instanceof List)) {
            this.problems.add("\"lights\" must be \"*\" or an array");
        } else {
            List<Ref> lights = new ArrayList<Ref>();
            int index = 0;
            for (Object item : (List<Object>)value) {
                Ref light = readRef(item, "lights[" + index++ + "]");
                if (light != null) {
                    lights.add(light);
                }
            }
            builder.lights(lights);
        }
    }

    @SuppressWarnings("unchecked")
    private Ref readRef(Object value, String label) {
        if (value instanceof String && !((String)value).isEmpty()) {
            return Ref.of((String)value);
        } else if (value instanceof Map) {
            Map<String, Object> ref = (Map<String, Object>)value;
            Object id = ref.get("id");
            Object name = ref.get("name");
            boolean onlyIdAndName = true;
            for (String key : ref.keySet()) {
                onlyIdAndName &= key.equals("id") || key.equals("name");
            }
            if (onlyIdAndName && (id != null || name != null)
                    && (id == null || id instanceof String) && (name == null || name instanceof String)) {
                return new Ref((String)id, (String)name);
            }
        }
        this.problems.add(label + " must be a name or an object with a string \"id\" and/or \"name\"");
        return null;
    }

    @SuppressWarnings("unchecked")
    private DateSchedule readDates(Object value, boolean present) {
        if (!present) {
            this.problems.add("\"dates\" is required");
            return null;
        } else if (!(value instanceof Map)) {
            this.problems.add("\"dates\" must be an object with \"start\", \"end\" and \"intervalDays\"");
            return null;
        }
        Map<String, Object> dates = (Map<String, Object>)value;
        try {
            LocalDate start = LocalDate.parse(String.valueOf(dates.get("start")));
            LocalDate end = LocalDate.parse(String.valueOf(dates.get("end")));
            Integer interval = toInteger(dates.get("intervalDays"), "\"dates.intervalDays\"", 1, Integer.MAX_VALUE);
            return interval != null ? new DateSchedule(start, end, interval) : null;
        } catch (DateTimeParseException ex) {
            this.problems.add("\"dates\" needs \"start\" and \"end\" written as YYYY-MM-DD");
        } catch (IllegalArgumentException ex) {
            this.problems.add("\"dates\": " + ex.getMessage());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private TimeSchedule readTimes(Object value, boolean present) {
        if (!present) {
            this.problems.add("\"times\" is required");
            return null;
        } else if (!(value instanceof Map)) {
            this.problems.add("\"times\" must be an object with \"start\", \"end\" and \"intervalMinutes\"");
            return null;
        }
        Map<String, Object> times = (Map<String, Object>)value;
        try {
            LocalTime start = LocalTime.parse(String.valueOf(times.get("start")), TIME_FORMAT);
            LocalTime end = LocalTime.parse(String.valueOf(times.get("end")), TIME_FORMAT);
            Integer interval = toInteger(times.get("intervalMinutes"), "\"times.intervalMinutes\"", 1, Integer.MAX_VALUE);
            return interval != null ? new TimeSchedule(start, end, interval) : null;
        } catch (DateTimeParseException ex) {
            this.problems.add("\"times\" needs \"start\" and \"end\" written as HH:mm");
        } catch (IllegalArgumentException ex) {
            this.problems.add("\"times\": " + ex.getMessage());
        }
        return null;
    }

    /**
     * Returns <code>instructions</code> as the JSON text of an instructions file.
     */
    public static String write(Instructions instructions) {
        Map<String, Object> object = new LinkedHashMap<String, Object>();
        object.put("version", VERSION);
        if (instructions.getHome() != null) {
            object.put("home", instructions.getHome());
        }
        if (instructions.getOutput() != null) {
            object.put("output", instructions.getOutput());
        }
        if (instructions.isAllFloors()) {
            object.put("floors", ALL);
        } else {
            List<Object> floors = new ArrayList<Object>();
            for (Instructions.Floor floor : instructions.getFloors()) {
                Map<String, Object> floorObject = new LinkedHashMap<String, Object>();
                floorObject.put("level", refValue(floor.level));
                if (floor.camera != null) {
                    floorObject.put("camera", refValue(floor.camera));
                }
                floors.add(floorObject);
            }
            object.put("floors", floors);
        }
        Map<String, Object> dates = new LinkedHashMap<String, Object>();
        dates.put("start", instructions.getDates().getStart().toString());
        dates.put("end", instructions.getDates().getEnd().toString());
        dates.put("intervalDays", instructions.getDates().getIntervalDays());
        object.put("dates", dates);
        Map<String, Object> times = new LinkedHashMap<String, Object>();
        times.put("start", instructions.getTimes().getStart().format(TIME_FORMAT));
        times.put("end", instructions.getTimes().getEnd().format(TIME_FORMAT));
        times.put("intervalMinutes", instructions.getTimes().getIntervalMinutes());
        object.put("times", times);
        if (instructions.isAllLights()) {
            object.put("lights", ALL);
        } else {
            List<Object> lights = new ArrayList<Object>();
            for (Ref light : instructions.getLights()) {
                lights.add(refValue(light));
            }
            object.put("lights", lights);
        }
        object.put("width", instructions.getWidth());
        object.put("height", instructions.getHeight());
        if (instructions.getRenderer() != null) {
            object.put("renderer", instructions.getRenderer());
        }
        object.put("quality", instructions.getQuality().name());
        object.put("hideCeilings", instructions.isHideCeilings());
        object.put("isolateLevel", instructions.isIsolateLevel());
        object.put("noiseThreshold", instructions.getNoiseThreshold());
        return Json.write(object);
    }

    private static Object refValue(Ref ref) {
        if (ref.id != null && ref.id.equals(ref.name)) {
            return ref.id;
        }
        Map<String, Object> object = new LinkedHashMap<String, Object>();
        if (ref.id != null) {
            object.put("id", ref.id);
        }
        if (ref.name != null) {
            object.put("name", ref.name);
        }
        return object;
    }
}
