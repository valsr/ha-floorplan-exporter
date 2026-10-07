package io.github.valsr.hafloorplan.plan;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One image to render.
 */
public final class RenderJob {
    public enum Kind {
        /** A floor at a date and time with all lights off. */
        BASE,
        /** A floor at night with all lights off, the reference light overlays are computed against. */
        NIGHT_BASE,
        /** A floor at night with one light on. */
        LIGHT
    }

    public final String floorId;
    public final Kind kind;
    /** The date of a {@link Kind#BASE} job, otherwise <code>null</code>. */
    public final LocalDate date;
    /** The time of a {@link Kind#BASE} job, otherwise <code>null</code>. */
    public final LocalTime time;
    /** The light of a {@link Kind#LIGHT} job, otherwise <code>null</code>. */
    public final String lightId;
    /** The path of the image in the output folder, with <code>/</code> separators. */
    public final String path;

    RenderJob(String floorId, Kind kind, LocalDate date, LocalTime time, String lightId, String path) {
        this.floorId = floorId;
        this.kind = kind;
        this.date = date;
        this.time = time;
        this.lightId = lightId;
        this.path = path;
    }

    @Override
    public String toString() {
        return this.path;
    }
}
