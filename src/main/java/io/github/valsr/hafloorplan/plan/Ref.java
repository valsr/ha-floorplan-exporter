package io.github.valsr.hafloorplan.plan;

import java.util.Objects;

/**
 * A reference to a level, a camera or a light of a home: by id first, then by name.
 */
public final class Ref {
    public final String id;
    public final String name;

    public Ref(String id, String name) {
        if (id == null && name == null) {
            throw new IllegalArgumentException("A reference needs an id or a name");
        }
        this.id = id;
        this.name = name;
    }

    /**
     * Returns a reference to the object with <code>text</code> as id or, failing that, as name.
     */
    public static Ref of(String text) {
        return new Ref(text, text);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Ref && Objects.equals(this.id, ((Ref)obj).id) && Objects.equals(this.name, ((Ref)obj).name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id, this.name);
    }

    /**
     * Returns the text describing this reference in messages: its name if it has one.
     */
    @Override
    public String toString() {
        return this.name != null ? this.name : this.id;
    }
}
