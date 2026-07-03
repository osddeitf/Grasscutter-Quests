package emu.grasscutter.database.mapping;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import dev.morphia.annotations.Property;
import dev.morphia.mapping.Mapper;

import emu.grasscutter.Grasscutter;

import org.anime_game_servers.multi_proto.gi.messages.scene.map.MapMarkPointType;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Backend-neutral serialization between persistable POJOs and a plain {@code Map<String,Object>}
 * document tree, driven by Gson. Used by non-Morphia backends (e.g. SurrealDB): the resulting map
 * is handed to the driver as record content, and driver results (walked into a map) are turned
 * back into entities here.
 *
 * <p>Why Gson rather than the SurrealDB SDK's typed binder: the SDK's converter cannot instantiate
 * Java enums (and does not understand our {@code ObjectId}/custom types). Gson skips {@code
 * transient} fields (matching Morphia) and serializes enums by name, which is exactly what we want.
 *
 * <p>Lifecycle hooks ({@code @PrePersist} before write, {@code @PreLoad}/{@code @PostLoad} around
 * read) are invoked reflectively via {@link MorphiaMappingAdapter}, since non-Morphia backends do
 * not get Morphia's callbacks. (On the Morphia backend, Morphia still fires them itself; this mapper
 * is not used there, so there is no double invocation.)
 */
public final class DocumentMapper {
    private final Gson gson;

    public DocumentMapper() {
        this.gson = new GsonBuilder()
            // Honor Morphia @Property field renames (e.g. WidgetSwitchManager's slots field).
            .setFieldNamingStrategy(field -> {
                Property p = field.getAnnotation(Property.class);
                return (p != null && !p.value().equals(Mapper.IGNORED_FIELDNAME)) ? p.value() : field.getName();
            })
            // ObjectId has no natural JSON form; persist as its hex string.
            .registerTypeAdapter(ObjectId.class,
                (com.google.gson.JsonSerializer<ObjectId>) (src, t, c) ->
                    src == null ? JsonNull.INSTANCE : new JsonPrimitive(src.toHexString()))
            .registerTypeAdapter(ObjectId.class,
                (com.google.gson.JsonDeserializer<ObjectId>) (json, t, c) ->
                    json == null || json.isJsonNull() ? null : new ObjectId(json.getAsString()))
            // Port of MapMarkPointCodec: store enum name, tolerate the historic prefix on read.
            .registerTypeAdapter(MapMarkPointType.class,
                (com.google.gson.JsonSerializer<MapMarkPointType>) (src, t, c) ->
                    src == null ? JsonNull.INSTANCE : new JsonPrimitive(src.name()))
            .registerTypeAdapter(MapMarkPointType.class,
                (com.google.gson.JsonDeserializer<MapMarkPointType>) (json, t, c) -> {
                    if (json == null || json.isJsonNull()) return null;
                    try {
                        return MapMarkPointType.valueOf(json.getAsString().replace("MAP_MARK_POINT_TYPE_", ""));
                    } catch (IllegalArgumentException e) {
                        return MapMarkPointType.UNRECOGNISED;
                    }
                })
            // Dates as epoch millis (a sortable number) so SurrealQL ORDER BY works correctly
            // (Gson's default renders Date as a locale string, which would sort lexically).
            .registerTypeAdapter(Date.class,
                (com.google.gson.JsonSerializer<Date>) (src, t, c) ->
                    src == null ? JsonNull.INSTANCE : new JsonPrimitive(src.getTime()))
            .registerTypeAdapter(Date.class,
                (com.google.gson.JsonDeserializer<Date>) (json, t, c) ->
                    json == null || json.isJsonNull() ? null : new Date(json.getAsLong()))
            .registerTypeAdapter(Instant.class,
                (com.google.gson.JsonSerializer<Instant>) (src, t, c) ->
                    src == null ? JsonNull.INSTANCE : new JsonPrimitive(src.toEpochMilli()))
            .registerTypeAdapter(Instant.class,
                (com.google.gson.JsonDeserializer<Instant>) (json, t, c) ->
                    json == null || json.isJsonNull() ? null : Instant.ofEpochMilli(json.getAsLong()))
            .create();
    }

    /** Serializes an entity to a plain document tree, invoking {@code @PrePersist} first. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> toDocument(Object entity) {
        if (entity == null) return null;
        EntityMapping mapping = MorphiaMappingAdapter.of(entity.getClass());
        invoke(mapping.prePersist(), entity, null);
        JsonElement tree = gson.toJsonTree(entity);
        return (Map<String, Object>) jsonToJava(tree);
    }

    /**
     * Reconstructs an entity from a document tree, invoking {@code @PreLoad} (with the raw document)
     * before and {@code @PostLoad} after.
     */
    public <T> T fromDocument(Class<T> type, Map<String, Object> document) {
        if (document == null) return null;
        EntityMapping mapping = MorphiaMappingAdapter.of(type);
        T entity = gson.fromJson(gson.toJsonTree(document), type);
        invoke(mapping.preLoad(), entity, document);
        invoke(mapping.postLoad(), entity, document);
        return entity;
    }

    // region reflection helpers
    private static void invoke(Method hook, Object target, Map<String, Object> document) {
        if (hook == null) return;
        try {
            if (hook.getParameterCount() == 0) {
                hook.invoke(target);
            } else if (hook.getParameterCount() == 1
                    && hook.getParameterTypes()[0].isAssignableFrom(Document.class)) {
                // @PreLoad(Document) style hooks (e.g. Account) expect a Mongo-like document view.
                hook.invoke(target, new Document(document == null ? Map.of() : document));
            }
        } catch (ReflectiveOperationException e) {
            Grasscutter.getLogger().error("Failed to invoke lifecycle hook {} on {}",
                hook.getName(), target.getClass().getSimpleName(), e);
        }
    }
    // endregion

    // region JSON tree <-> plain Java
    private static Object jsonToJava(JsonElement el) {
        if (el == null || el.isJsonNull()) return null;
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                map.put(e.getKey(), jsonToJava(e.getValue()));
            }
            return map;
        }
        if (el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            List<Object> list = new ArrayList<>(arr.size());
            for (JsonElement e : arr) list.add(jsonToJava(e));
            return list;
        }
        JsonPrimitive p = el.getAsJsonPrimitive();
        if (p.isBoolean()) return p.getAsBoolean();
        if (p.isString()) return p.getAsString();
        // Number: prefer a long when integral, else double.
        double d = p.getAsDouble();
        long l = (long) d;
        return (l == d) ? (Object) l : (Object) d;
    }
    // endregion
}
