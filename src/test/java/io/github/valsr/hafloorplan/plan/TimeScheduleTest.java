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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class TimeScheduleTest {
    private static LocalTime t(String text) {
        return LocalTime.parse(text);
    }

    @Test
    void everyFourHours() {
        assertEquals(Arrays.asList(t("00:00"), t("04:00"), t("08:00"), t("12:00"), t("16:00"), t("20:00")),
                new TimeSchedule(t("00:00"), t("23:00"), 240).times());
    }

    @Test
    void startEqualToEndGivesOneTime() {
        assertEquals(Collections.singletonList(t("08:00")), new TimeSchedule(t("08:00"), t("08:00"), 30).times());
    }

    @Test
    void rangeCannotCrossMidnight() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new TimeSchedule(t("22:00"), t("02:00"), 60));
        assertTrue(ex.getMessage().contains("before"), ex.getMessage());
    }

    @Test
    void intervalMustBePositive() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new TimeSchedule(t("00:00"), t("02:00"), 0));
        assertTrue(ex.getMessage().contains("interval"), ex.getMessage());
    }

    @Test
    void doesNotWrapPastMidnight() {
        List<LocalTime> times = new TimeSchedule(t("00:00"), t("23:59"), 1).times();
        assertEquals(1440, times.size());
        assertEquals(t("23:59"), times.get(1439));
    }
}
