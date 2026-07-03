package emu.grasscutter.database.morphia;

import static emu.grasscutter.config.Configuration.*;

import com.mongodb.MongoCommandException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;
import com.mongodb.client.result.DeleteResult;

import dev.morphia.Datastore;
import dev.morphia.Morphia;
import dev.morphia.annotations.Entity;
import dev.morphia.mapping.Mapper;
import dev.morphia.mapping.MapperOptions;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Sort;
import dev.morphia.query.filters.Filters;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.database.DatabaseCounter;
import emu.grasscutter.database.GameRepository;
import emu.grasscutter.database.codec.CodecProvider;
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
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.GameMainQuest;
import emu.grasscutter.game.world.SceneGroupInstance;
import emu.grasscutter.game.world.SceneInstanceData;

import java.util.List;
import java.util.stream.Stream;

import static com.mongodb.client.model.Filters.eq;

/**
 * MongoDB-backed {@link GameRepository} using the Morphia ODM. This is the only class allowed to
 * reference {@code dev.morphia.*} / {@code com.mongodb.*} types.
 *
 * <p>Method bodies are ported verbatim from the pre-refactor {@code DatabaseHelper} and
 * {@code DatabaseManager}, so behavior on MongoDB is unchanged.
 */
public final class MorphiaGameRepository implements GameRepository {
    private Datastore gameDatastore;
    private Datastore dispatchDatastore;

    /** The Morphia game datastore. Used by legacy call sites (experimental module managers). */
    public Datastore getGameDatastore() {
        return gameDatastore;
    }

    public MongoDatabase getGameDatabase() {
        return gameDatastore.getDatabase();
    }

    // Yes. I very dislike this method. However, this will be good for now.
    // TODO: Add dispatch routes for player account management
    public Datastore getAccountDatastore() {
        if (SERVER.runMode == ServerRunMode.GAME_ONLY) {
            return dispatchDatastore;
        } else {
            return gameDatastore;
        }
    }

    @Override
    public void initialize() {
        // Initialize
        MongoClient gameMongoClient = MongoClients.create(DATABASE.game.connectionUri);

        // Set mapper options.
        MapperOptions mapperOptions = MapperOptions.builder()
            .storeEmpties(true)
            .storeNulls(false)
            .codecProvider(new CodecProvider())
            .mapSubPackages(true)
            .build();

        // Create data store.
        gameDatastore = Morphia.createDatastore(gameMongoClient, DATABASE.game.collection, mapperOptions);

        // Map classes.
        Class<?>[] entities = Grasscutter.reflector
                .getTypesAnnotatedWith(Entity.class)
                .stream()
                .filter(cls -> {
                    Entity e = cls.getAnnotation(Entity.class);
                    return e != null && !e.value().equals(Mapper.IGNORED_FIELDNAME);
                })
                .toArray(Class<?>[]::new);

        gameDatastore.getMapper().map(entities);
        gameDatastore.getMapper().mapPackage("emu.grasscutter.game.modules");

        // Ensure indexes for the game datastore
        ensureIndexes(gameDatastore);

        if (SERVER.runMode == ServerRunMode.GAME_ONLY) {
            MongoClient dispatchMongoClient = MongoClients.create(DATABASE.server.connectionUri);

            dispatchDatastore = Morphia.createDatastore(dispatchMongoClient, DATABASE.server.collection, mapperOptions);
            dispatchDatastore.getMapper().map(new Class<?>[] {DatabaseCounter.class, Account.class});

            // Ensure indexes for dispatch datastore
            ensureIndexes(dispatchDatastore);
        }
    }

    /**
     * Ensures the database indexes exist and rebuilds them if there is an error with them
     * @param datastore The datastore to ensure indexes on
     */
    private static void ensureIndexes(Datastore datastore) {
        try {
            datastore.ensureIndexes();
        } catch (MongoCommandException e) {
            Grasscutter.getLogger().info("Mongo index error: ", e);
            // Duplicate index error
            if (e.getCode() == 85) {
                // Drop all indexes and re add them
                MongoIterable<String> collections = datastore.getDatabase().listCollectionNames();
                for (String name : collections) {
                    datastore.getDatabase().getCollection(name).dropIndexes();
                }
                // Add back indexes
                datastore.ensureIndexes();
            }
        }
    }

    @Override
    public synchronized int getNextId(Class<?> c) {
        DatabaseCounter counter = gameDatastore.find(DatabaseCounter.class).filter(Filters.eq("_id", c.getSimpleName())).first();
        if (counter == null) {
            counter = new DatabaseCounter(c.getSimpleName());
        }
        try {
            return counter.getNextId();
        } finally {
            gameDatastore.save(counter);
        }
    }

    // region accounts
    @Override
    public Account getAccountById(String uid) {
        return getAccountDatastore().find(Account.class).filter(Filters.eq("_id", uid)).first();
    }

    @Override
    public Account getAccountByName(String username) {
        return getAccountDatastore().find(Account.class).filter(Filters.eq("username", username)).first();
    }

    @Override
    public Account getAccountByToken(String token) {
        if (token == null) return null;
        return getAccountDatastore().find(Account.class).filter(Filters.eq("token", token)).first();
    }

    @Override
    public Account getAccountBySessionKey(String sessionKey) {
        if (sessionKey == null) return null;
        return getAccountDatastore().find(Account.class).filter(Filters.eq("sessionKey", sessionKey)).first();
    }

    @Override
    public Account getAccountByPlayerId(int playerId) {
        return getAccountDatastore().find(Account.class).filter(Filters.eq("reservedPlayerId", playerId)).first();
    }

    @Override
    public boolean checkIfAccountExists(String name) {
        return getAccountDatastore().find(Account.class).filter(Filters.eq("username", name)).count() > 0;
    }

    @Override
    public boolean checkIfAccountExists(int reservedUid) {
        return getAccountDatastore().find(Account.class).filter(Filters.eq("reservedPlayerId", reservedUid)).count() > 0;
    }

    @Override
    public void saveAccount(Account account) {
        getAccountDatastore().save(account);
    }

    @Override
    public synchronized void deleteAccountAndData(Account target, int uid) {
        // Delete data from collections
        gameDatastore.getDatabase().getCollection("activities").deleteMany(eq("uid", uid));
        gameDatastore.getDatabase().getCollection("homes").deleteMany(eq("ownerUid", uid));
        gameDatastore.getDatabase().getCollection("mail").deleteMany(eq("ownerUid", uid));
        gameDatastore.getDatabase().getCollection("avatars").deleteMany(eq("ownerId", uid));
        gameDatastore.getDatabase().getCollection("gachas").deleteMany(eq("ownerId", uid));
        gameDatastore.getDatabase().getCollection("items").deleteMany(eq("ownerId", uid));
        gameDatastore.getDatabase().getCollection("quests").deleteMany(eq("ownerUid", uid));
        gameDatastore.getDatabase().getCollection("battlepass").deleteMany(eq("ownerUid", uid));

        // Delete friendships.
        // Here, we need to make sure to not only delete the deleted account's friendships,
        // but also all friendship entries for that account's friends.
        gameDatastore.getDatabase().getCollection("friendships").deleteMany(eq("ownerId", uid));
        gameDatastore.getDatabase().getCollection("friendships").deleteMany(eq("friendId", uid));

        // Delete the player last.
        gameDatastore.find(Player.class).filter(Filters.eq("id", uid)).delete();

        // Finally, delete the account itself.
        getAccountDatastore().find(Account.class).filter(Filters.eq("id", target.getId())).delete();
    }
    // endregion

    // region players
    @Override
    public Player getPlayerByUid(int id) {
        return gameDatastore.find(Player.class).filter(Filters.eq("_id", id)).first();
    }

    @Override
    public Player getPlayerByAccountId(String accountId, Class<? extends Player> playerClass) {
        return gameDatastore.find(playerClass).filter(Filters.eq("accountId", accountId)).first();
    }

    @Override
    public boolean checkIfPlayerExists(int uid) {
        return gameDatastore.find(Player.class).filter(Filters.eq("_id", uid)).count() > 0;
    }

    @Override
    public void savePlayer(Player character) {
        gameDatastore.save(character);
    }

    @Override
    public List<Player> getAllPlayers() {
        return gameDatastore.find(Player.class).stream().toList();
    }
    // endregion

    // region avatars
    @Override
    public void saveAvatar(Avatar avatar) {
        gameDatastore.save(avatar);
    }

    @Override
    public List<Avatar> getAvatars(int ownerUid) {
        return gameDatastore.find(Avatar.class).filter(Filters.eq("ownerId", ownerUid)).stream().toList();
    }
    // endregion

    // region items
    @Override
    public void saveItem(GameItem item) {
        gameDatastore.save(item);
    }

    @Override
    public boolean deleteItem(GameItem item) {
        DeleteResult result = gameDatastore.delete(item);
        return result.wasAcknowledged();
    }

    @Override
    public List<GameItem> getInventoryItems(int ownerUid) {
        return gameDatastore.find(GameItem.class).filter(Filters.eq("ownerId", ownerUid)).stream().toList();
    }
    // endregion

    // region friendships
    @Override
    public List<Friendship> getFriends(int ownerUid) {
        return gameDatastore.find(Friendship.class).filter(Filters.eq("ownerId", ownerUid)).stream().toList();
    }

    @Override
    public List<Friendship> getReverseFriends(int friendUid) {
        return gameDatastore.find(Friendship.class).filter(Filters.eq("friendId", friendUid)).stream().toList();
    }

    @Override
    public void saveFriendship(Friendship friendship) {
        gameDatastore.save(friendship);
    }

    @Override
    public void deleteFriendship(Friendship friendship) {
        gameDatastore.delete(friendship);
    }

    @Override
    public Friendship getReverseFriendship(int ownerId, int friendId) {
        return gameDatastore.find(Friendship.class).filter(Filters.and(
                Filters.eq("ownerId", friendId),
                Filters.eq("friendId", ownerId)
        )).first();
    }
    // endregion

    // region gacha
    @Override
    public List<GachaRecord> getGachaRecords(int ownerId, int gachaType, int page, int pageSize) {
        return gameDatastore.find(GachaRecord.class).filter(
            Filters.eq("ownerId", ownerId),
            Filters.eq("gachaType", gachaType)
        ).iterator(new FindOptions()
                .sort(Sort.descending("transactionDate"))
                .skip(pageSize * page)
                .limit(pageSize)
        ).toList();
    }

    @Override
    public long getGachaRecordsCount(int ownerId, int gachaType) {
        return gameDatastore.find(GachaRecord.class).filter(
            Filters.eq("ownerId", ownerId),
            Filters.eq("gachaType", gachaType)
        ).count();
    }

    @Override
    public void saveGachaRecord(GachaRecord gachaRecord) {
        gameDatastore.save(gachaRecord);
    }
    // endregion

    // region mail
    @Override
    public List<Mail> getAllMail(int ownerUid) {
        return gameDatastore.find(Mail.class).filter(Filters.eq("ownerUid", ownerUid)).stream().toList();
    }

    @Override
    public void saveMail(Mail mail) {
        gameDatastore.save(mail);
    }

    @Override
    public boolean deleteMail(Mail mail) {
        DeleteResult result = gameDatastore.delete(mail);
        return result.wasAcknowledged();
    }
    // endregion

    // region quests
    @Override
    public List<GameMainQuest> getAllQuests(int ownerUid) {
        return gameDatastore.find(GameMainQuest.class).filter(Filters.eq("ownerUid", ownerUid)).stream().toList();
    }

    @Override
    public void saveQuest(GameMainQuest quest) {
        gameDatastore.save(quest);
    }

    @Override
    public boolean deleteQuest(GameMainQuest quest) {
        return gameDatastore.delete(quest).wasAcknowledged();
    }
    // endregion

    // region home
    @Override
    public GameHome getHomeByUid(int id) {
        return gameDatastore.find(GameHome.class).filter(Filters.eq("ownerUid", id)).first();
    }

    @Override
    public void saveHome(GameHome gameHome) {
        gameDatastore.save(gameHome);
    }
    // endregion

    // region battlepass
    @Override
    public BattlePassManager getBattlePassByUid(int ownerUid) {
        return gameDatastore.find(BattlePassManager.class).filter(Filters.eq("ownerUid", ownerUid)).first();
    }

    @Override
    public void saveBattlePass(BattlePassManager manager) {
        gameDatastore.save(manager);
    }
    // endregion

    // region activity
    @Override
    public PlayerActivityData getPlayerActivityData(int uid, int activityId) {
        return gameDatastore.find(PlayerActivityData.class)
            .filter(Filters.and(Filters.eq("uid", uid), Filters.eq("activityId", activityId)))
            .first();
    }

    @Override
    public void savePlayerActivityData(PlayerActivityData playerActivityData) {
        gameDatastore.save(playerActivityData);
    }
    // endregion

    // region music game
    @Override
    public MusicGameBeatmap getMusicGameBeatmap(long musicShareId) {
        return gameDatastore.find(MusicGameBeatmap.class)
            .filter(Filters.eq("musicShareId", musicShareId))
            .first();
    }

    @Override
    public void saveMusicGameBeatmap(MusicGameBeatmap musicGameBeatmap) {
        gameDatastore.save(musicGameBeatmap);
    }
    // endregion

    // region scene instances
    @Override
    public void saveGroupInstance(SceneGroupInstance instance) {
        gameDatastore.save(instance);
    }

    @Override
    public SceneGroupInstance loadGroupInstance(int ownerUid, int groupId) {
        return gameDatastore.find(SceneGroupInstance.class).filter(Filters.and(Filters.eq("ownerUid", ownerUid), Filters.eq("groupId", groupId))).first();
    }

    @Override
    public void saveSceneInstanceData(SceneInstanceData instance) {
        gameDatastore.save(instance);
    }

    @Override
    public SceneInstanceData loadSceneInstanceData(int ownerUid, int sceneId) {
        return gameDatastore.find(SceneInstanceData.class).filter(Filters.and(Filters.eq("ownerUid", ownerUid), Filters.eq("sceneId", sceneId))).first();
    }
    // endregion

    // region generic
    @Override
    public <T> Stream<T> getByClass(Class<T> classType) {
        return gameDatastore.find(classType).stream();
    }
    // endregion
}
