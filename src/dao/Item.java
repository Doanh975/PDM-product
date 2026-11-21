package dao;

import java.util.Objects;

/** An (id, label) pair for combo boxes. id == null means "All" / "None". */
public final class Item {

    public final Integer id;
    public final String label;

    public Item(Integer id, String label) {
        this.id = id;
        this.label = label;
    }

    @Override public String toString() { return label; }

    @Override public boolean equals(Object o) {
        return o instanceof Item other && Objects.equals(id, other.id);
    }

    @Override public int hashCode() { return Objects.hashCode(id); }
}
