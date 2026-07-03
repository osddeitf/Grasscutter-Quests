package emu.grasscutter.database.mapping;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Backend-neutral mapping metadata for a persistable class, derived from its Morphia annotations
 * by {@link MorphiaMappingAdapter}. Consumed by non-Morphia backends (e.g. SurrealDB) so entities
 * keep a single source of mapping truth (their existing Morphia annotations) and do not need
 * backend-specific annotations.
 */
public final class EntityMapping {
    /** A single index definition (one or more fields, optionally unique). */
    public record Index(List<String> fields, boolean unique) {}

    private final Class<?> type;
    private final String table;              // @Entity value; null when this class is embedded-only
    private final Field idField;             // @Id field (may be null for embedded value objects)
    private final List<Index> indexes;
    private final Method preLoad;            // @PreLoad
    private final Method postLoad;           // @PostLoad
    private final Method prePersist;         // @PrePersist
    private final Method postPersist;        // @PostPersist

    EntityMapping(Class<?> type, String table, Field idField, List<Index> indexes,
                  Method preLoad, Method postLoad, Method prePersist, Method postPersist) {
        this.type = type;
        this.table = table;
        this.idField = idField;
        this.indexes = indexes;
        this.preLoad = preLoad;
        this.postLoad = postLoad;
        this.prePersist = prePersist;
        this.postPersist = postPersist;
    }

    public Class<?> type() { return type; }
    public String table() { return table; }
    public boolean isCollection() { return table != null; }
    public Field idField() { return idField; }
    public List<Index> indexes() { return indexes; }
    public Method preLoad() { return preLoad; }
    public Method postLoad() { return postLoad; }
    public Method prePersist() { return prePersist; }
    public Method postPersist() { return postPersist; }
}
