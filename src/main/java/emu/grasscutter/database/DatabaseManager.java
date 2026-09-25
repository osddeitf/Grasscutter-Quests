package emu.grasscutter.database;

import static emu.grasscutter.config.Configuration.*;

import com.mongodb.client.MongoDatabase;

import dev.morphia.Datastore;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.database.morphia.MorphiaGameRepository;
import emu.grasscutter.database.surreal.SurrealGameRepository;

public final class DatabaseManager {
    private static GameRepository repository;

    /** The active, backend-agnostic persistence layer. */
    public static GameRepository getRepository() {
        return repository;
    }

    /**
     * The Morphia game datastore, or {@code null} when the active backend is not Morphia/MongoDB.
     *
     * <p>Retained only for legacy call sites that still use Morphia directly (the experimental
     * module managers). New code must go through {@link GameRepository}.
     */
    public static Datastore getGameDatastore() {
        if (repository instanceof MorphiaGameRepository morphia) {
            return morphia.getGameDatastore();
        }
        return null;
    }

    public static MongoDatabase getGameDatabase() {
        if (repository instanceof MorphiaGameRepository morphia) {
            return morphia.getGameDatabase();
        }
        return null;
    }

    /** @deprecated Use {@link GameRepository} account methods. Kept for legacy Morphia call sites. */
    @Deprecated
    public static Datastore getAccountDatastore() {
        if (repository instanceof MorphiaGameRepository morphia) {
            return morphia.getAccountDatastore();
        }
        return null;
    }

    public static void initialize() {
        String backend = DATABASE.game.backend;
        repository = switch (backend == null ? "mongodb" : backend.toLowerCase()) {
            case "mongodb", "mongo", "morphia" -> new MorphiaGameRepository();
            case "surreal", "surrealdb" -> new SurrealGameRepository();
            default -> {
                Grasscutter.getLogger().warn("Unknown database backend '{}', falling back to mongodb.", backend);
                yield new MorphiaGameRepository();
            }
        };
        repository.initialize();
    }

    public static synchronized int getNextId(Class<?> c) {
        return repository.getNextId(c);
    }

    public static synchronized int getNextId(Object o) {
        return getNextId(o.getClass());
    }
}
