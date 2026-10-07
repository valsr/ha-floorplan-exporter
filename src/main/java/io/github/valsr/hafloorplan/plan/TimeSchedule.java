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

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Times of day from a start to an end, both included, at a fixed interval in minutes.
 */
public final class TimeSchedule {
    private final LocalTime start;
    private final LocalTime end;
    private final int intervalMinutes;

    public TimeSchedule(LocalTime start, LocalTime end, int intervalMinutes) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Start and end times are required");
        }
        if (intervalMinutes < 1) {
            throw new IllegalArgumentException("Time interval must be at least 1 minute");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("End time " + end + " is before start time " + start);
        }
        this.start = start;
        this.end = end;
        this.intervalMinutes = intervalMinutes;
    }

    public LocalTime getStart() {
        return this.start;
    }

    public LocalTime getEnd() {
        return this.end;
    }

    public int getIntervalMinutes() {
        return this.intervalMinutes;
    }

    public List<LocalTime> times() {
        List<LocalTime> times = new ArrayList<LocalTime>();
        // Counted in seconds of the day because LocalTime wraps around at midnight
        int endSecond = this.end.toSecondOfDay();
        for (long second = this.start.toSecondOfDay(); second <= endSecond; second += this.intervalMinutes * 60L) {
            times.add(LocalTime.ofSecondOfDay(second));
        }
        return Collections.unmodifiableList(times);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TimeSchedule)) {
            return false;
        }
        TimeSchedule other = (TimeSchedule)obj;
        return this.start.equals(other.start) && this.end.equals(other.end)
                && this.intervalMinutes == other.intervalMinutes;
    }

    @Override
    public int hashCode() {
        return 31 * (31 * this.start.hashCode() + this.end.hashCode()) + this.intervalMinutes;
    }

    @Override
    public String toString() {
        return this.start + ".." + this.end + "/" + this.intervalMinutes;
    }
}
