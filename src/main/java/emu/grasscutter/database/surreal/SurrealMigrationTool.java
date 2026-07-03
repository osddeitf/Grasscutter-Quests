package emu.grasscutter.database.surreal;

import static emu.grasscutter.config.Configuration.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.database.DatabaseCounter;
import emu.grasscutter.database.DatabaseManager;
import emu.grasscutter.database.GameRepository;
import emu.grasscutter.database.morphia.MorphiaGameRepository;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.activity.PlayerActivityData;
import emu.grasscutter.game.activity.musicgame.MusicGameBeatmap;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.battlepass.BattlePassManager;
import emu.grasscutter.game.friends.Friendship;
import emu.grasscutter.game.gacha.GachaRecord;
import emu.grasscutter.game.home.GameHome;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.mail.Mail;
import emu.grasscutter.game.modules.GameModuleData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.GameMainQuest;
import emu.grasscutter.game.world.SceneGroupInstance;
import emu.grasscutter.game.world.SceneInstanceData;

import java.lang.reflect.Modifier;
import java.util.Iterator;
import java.util.List;

/**
 * One-shot copy of every game collection from the running MongoDB/Morphia backend into a target
 * SurrealDB database. Each document is read as an entity via the Morphia repository and written
 * through {@link SurrealGameRepository}, so migrating exercises the exact mapping path used at
 * runtime. Per-record failures are logged and skipped rather than aborting the run.
 *
 * <p>Triggered by the {@code -migrate-surreal=<connection-uri>} start-up argument. The server must
 * be started with {@code backend=mongodb} (the source); the target database name matches
 * {@code DATABASE.game.collection}. Assumes HYBRID run mode (accounts share the game datastore).
 */
public final class SurrealMigrationTool {
    private SurrealMigrationTool() {}

    // Order: counters and accounts first, then owned game data.
    private static final List<Class<?>> COLLECTIONS = List.of(
        DatabaseCounter.class,
        Account.class,
        Player.class,
        Avatar.class,
        GameItem.class,
        GachaRecord.class,
        Friendship.class,
        Mail.class,
        GameMainQuest.class,
        GameHome.class,
        BattlePassManager.class,
        PlayerActivityData.class,
        MusicGameBeatmap.class,
        SceneGroupInstance.class,
        SceneInstanceData.class);

    /** Runs the migration into {@code targetUri}, then returns. Intended to be followed by exit. */
    public static void run(String targetUri) {
        GameRepository source = DatabaseManager.getRepository();
        if (!(source instanceof MorphiaGameRepository)) {
            Grasscutter.getLogger().error(
                "SurrealDB migration requires the source backend to be mongodb (found {}). "
                    + "Set database.game.backend=mongodb and retry.",
                source == null ? "none" : source.getClass().getSimpleName());
            return;
        }

        String targetDb = DATABASE.game.collection;
        Grasscutter.getLogger().info("Starting MongoDB -> SurrealDB migration into {} (db={})", targetUri, targetDb);

        SurrealGameRepository target = new SurrealGameRepository();
        try {
            target.initialize(targetUri, targetDb);
        } catch (RuntimeException e) {
            Grasscutter.getLogger().error("Could not open SurrealDB target at {}", targetUri, e);
            return;
        }

        long grandTotal = 0;
        long grandFailed = 0;
        for (Class<?> cls : COLLECTIONS) {
            long[] counts = migrate(source, target, cls);
            grandTotal += counts[0];
            grandFailed += counts[1];
        }

        // The `modules` collection is polymorphic (single table, discriminated by _t); migrate it via
        // the module API so each record keeps its composite [ownerId, discriminator] identity.
        long[] moduleCounts = migrateModules(source, target);
        grandTotal += moduleCounts[0];
        grandFailed += moduleCounts[1];

        Grasscutter.getLogger().info("Migration complete: {} records migrated, {} failed.", grandTotal, grandFailed);
    }

    /** @return {@code [migratedCount, failedCount]} */
    private static <T> long[] migrate(GameRepository source, SurrealGameRepository target, Class<T> cls) {
        long ok = 0;
        long failed = 0;
        // Morphia decodes documents lazily during iteration (including hasNext), so a single
        // unreadable/unmappable collection can throw here. Contain it to this collection rather
        // than aborting the whole migration.
        try {
            Iterator<T> it = source.getByClass(cls).iterator();
            while (it.hasNext()) {
                T entity = it.next();
                try {
                    target.save(entity);
                    ok++;
                } catch (RuntimeException e) {
                    Grasscutter.getLogger().warn("Failed to write a {} record to SurrealDB", cls.getSimpleName(), e);
                    failed++;
                }
            }
        } catch (RuntimeException e) {
            Grasscutter.getLogger().error("Aborted migrating {} after {} record(s): {}",
                cls.getSimpleName(), ok, e.getMessage(), e);
        }
        Grasscutter.getLogger().info("  {}: {} migrated, {} failed", cls.getSimpleName(), ok, failed);
        return new long[] {ok, failed};
    }

    /**
     * Migrates the polymorphic {@code modules} collection. Morphia decodes each row into its concrete
     * {@link GameModuleData} subtype (via the {@code _t} discriminator); each is written through the
     * module API so it keeps its composite {@code [ownerId, discriminator]} identity on SurrealDB.
     *
     * @return {@code [migratedCount, failedCount]}
     */
    private static long[] migrateModules(GameRepository source, SurrealGameRepository target) {
        long ok = 0;
        long failed = 0;
        // GameModuleData is abstract and single-table: querying the base type returns nothing because
        // Morphia filters by the base discriminator, which no stored document has. Iterate each
        // concrete subtype so Morphia applies the correct discriminator filter per type.
        for (Class<? extends GameModuleData> subtype : Grasscutter.reflector.getSubTypesOf(GameModuleData.class)) {
            if (Modifier.isAbstract(subtype.getModifiers())) continue;
            try {
                Iterator<? extends GameModuleData> it = source.getByClass(subtype).iterator();
                while (it.hasNext()) {
                    GameModuleData module = it.next();
                    try {
                        target.saveModule(module);
                        ok++;
                    } catch (RuntimeException e) {
                        Grasscutter.getLogger().warn("Failed to write a module ({}) to SurrealDB",
                            module == null ? "?" : module.getClass().getSimpleName(), e);
                        failed++;
                    }
                }
            } catch (RuntimeException e) {
                Grasscutter.getLogger().error("Aborted migrating module type {}: {}",
                    subtype.getSimpleName(), e.getMessage(), e);
            }
        }
        Grasscutter.getLogger().info("  modules: {} migrated, {} failed", ok, failed);
        return new long[] {ok, failed};
    }
}
