package io.github.valsr.hafloorplan.plan;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Dates from a start to an end, both included, at a fixed interval in days.
 */
public final class DateSchedule {
    private final LocalDate start;
    private final LocalDate end;
    private final int intervalDays;

    public DateSchedule(LocalDate start, LocalDate end, int intervalDays) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Start and end dates are required");
        }
        if (intervalDays < 1) {
            throw new IllegalArgumentException("Date interval must be at least 1 day");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("End date " + end + " is before start date " + start);
        }
        this.start = start;
        this.end = end;
        this.intervalDays = intervalDays;
    }

    public LocalDate getStart() {
        return this.start;
    }

    public LocalDate getEnd() {
        return this.end;
    }

    public int getIntervalDays() {
        return this.intervalDays;
    }

    public List<LocalDate> dates() {
        List<LocalDate> dates = new ArrayList<LocalDate>();
        for (LocalDate date = this.start; !date.isAfter(this.end); date = date.plusDays(this.intervalDays)) {
            dates.add(date);
        }
        return Collections.unmodifiableList(dates);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof DateSchedule)) {
            return false;
        }
        DateSchedule other = (DateSchedule)obj;
        return this.start.equals(other.start) && this.end.equals(other.end)
                && this.intervalDays == other.intervalDays;
    }

    @Override
    public int hashCode() {
        return 31 * (31 * this.start.hashCode() + this.end.hashCode()) + this.intervalDays;
    }

    @Override
    public String toString() {
        return this.start + ".." + this.end + "/" + this.intervalDays;
    }
}
