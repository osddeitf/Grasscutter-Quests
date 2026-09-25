package emu.grasscutter.database.mapping;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import dev.morphia.annotations.Index;
import dev.morphia.annotations.Indexed;
import dev.morphia.annotations.Indexes;
import dev.morphia.annotations.PostLoad;
import dev.morphia.annotations.PostPersist;
import dev.morphia.annotations.PreLoad;
import dev.morphia.annotations.PrePersist;
import dev.morphia.mapping.Mapper;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Derives backend-neutral {@link EntityMapping} metadata from the Morphia annotations already
 * present on entity classes ({@code @Entity}, {@code @Id}, {@code @Indexed}/{@code @Indexes},
 * lifecycle hooks). This lets a non-Morphia backend reuse the same annotations rather than
 * requiring its own, keeping entities as the single source of mapping truth.
 *
 * <p>Results are cached per class.
 */
public final class MorphiaMappingAdapter {
    private static final ConcurrentHashMap<Class<?>, EntityMapping> CACHE = new ConcurrentHashMap<>();

    private MorphiaMappingAdapter() {}

    public static EntityMapping of(Class<?> type) {
        return CACHE.computeIfAbsent(type, MorphiaMappingAdapter::build);
    }

    private static EntityMapping build(Class<?> type) {
        // Table name: @Entity(value). A missing/IGNORED value means the class is used only as an
        // embedded value object and has no collection of its own.
        String table = null;
        String discriminator = null;
        Entity entity = type.getAnnotation(Entity.class);
        if (entity != null && !Mapper.IGNORED_FIELDNAME.equals(entity.value())) {
            table = entity.value();
        }
        if (entity != null) {
            // Explicit discriminator, else Morphia's default (the simple class name).
            discriminator = entity.discriminator().isEmpty() ? type.getSimpleName() : entity.discriminator();
        }

        List<Field> fields = allFields(type);
        Field idField = fields.stream()
            .filter(f -> f.isAnnotationPresent(Id.class))
            .findFirst().orElse(null);
        if (idField != null) idField.setAccessible(true);

        List<EntityMapping.Index> indexes = collectIndexes(type, fields);

        return new EntityMapping(
            type, table, discriminator, idField, indexes,
            findHook(type, PreLoad.class),
            findHook(type, PostLoad.class),
            findHook(type, PrePersist.class),
            findHook(type, PostPersist.class));
    }

    private static List<EntityMapping.Index> collectIndexes(Class<?> type, List<Field> fields) {
        List<EntityMapping.Index> result = new ArrayList<>();

        // Field-level @Indexed
        for (Field f : fields) {
            Indexed indexed = f.getAnnotation(Indexed.class);
            if (indexed != null) {
                boolean unique = indexed.options() != null && indexed.options().unique();
                result.add(new EntityMapping.Index(List.of(f.getName()), unique));
            }
        }

        // Class-level @Indexes({ @Index(fields = { @Field("a"), @Field("b") }) })
        Indexes indexesAnn = type.getAnnotation(Indexes.class);
        if (indexesAnn != null) {
            for (Index idx : indexesAnn.value()) {
                List<String> idxFields = new ArrayList<>();
                for (dev.morphia.annotations.Field field : idx.fields()) {
                    idxFields.add(field.value());
                }
                boolean unique = idx.options() != null && idx.options().unique();
                result.add(new EntityMapping.Index(List.copyOf(idxFields), unique));
            }
        }
        return List.copyOf(result);
    }

    private static Method findHook(Class<?> type, Class<? extends Annotation> hook) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.isAnnotationPresent(hook)) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        return null;
    }

    private static List<Field> allFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                fields.add(f);
            }
        }
        return fields;
    }
}
