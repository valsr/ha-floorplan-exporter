package io.github.valsr.hafloorplan.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

class DateScheduleTest {
    private static LocalDate d(String text) {
        return LocalDate.parse(text);
    }

    @Test
    void bothEndsAreIncluded() {
        assertEquals(Arrays.asList(d("2026-01-01"), d("2026-01-02"), d("2026-01-03")),
                new DateSchedule(d("2026-01-01"), d("2026-01-03"), 1).dates());
    }

    @Test
    void lastValueIsTheLastStepNotAfterTheEnd() {
        assertEquals(Arrays.asList(d("2026-01-01"), d("2026-01-05"), d("2026-01-09")),
                new DateSchedule(d("2026-01-01"), d("2026-01-10"), 4).dates());
    }

    @Test
    void startEqualToEndGivesOneDate() {
        assertEquals(Collections.singletonList(d("2026-03-04")),
                new DateSchedule(d("2026-03-04"), d("2026-03-04"), 7).dates());
    }

    @Test
    void intervalMustBePositive() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new DateSchedule(d("2026-01-01"), d("2026-01-03"), 0));
        assertTrue(ex.getMessage().contains("interval"), ex.getMessage());
    }

    @Test
    void endMustNotBeBeforeStart() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new DateSchedule(d("2026-01-03"), d("2026-01-01"), 1));
        assertTrue(ex.getMessage().contains("before"), ex.getMessage());
    }
}
