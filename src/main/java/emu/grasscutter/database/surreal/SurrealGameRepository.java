package emu.grasscutter.database.surreal;

import static emu.grasscutter.config.Configuration.*;

import com.surrealdb.Array;
import com.surrealdb.Entry;
import com.surrealdb.RecordId;
import com.surrealdb.Response;
import com.surrealdb.Surreal;
import com.surrealdb.UpType;
import com.surrealdb.Value;

import dev.morphia.annotations.Entity;
import dev.morphia.mapping.Mapper;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.database.GameRepository;
import emu.grasscutter.database.mapping.DocumentMapper;
import emu.grasscutter.database.mapping.EntityMapping;
import emu.grasscutter.database.mapping.MorphiaMappingAdapter;
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

import org.bson.types.ObjectId;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * SurrealDB-backed {@link GameRepository} (embedded or remote). This is the only class allowed to
 * reference {@code com.surrealdb.*}.
 *
 * <p>Entities are persisted by mapping them to a plain document with {@link DocumentMapper} (Gson)
 * and writing via {@code upsert(recordId, CONTENT, map)}; the SDK's typed POJO binder is not used
 * (it cannot instantiate Java enums). Identity is carried by the SurrealDB record id, derived from
 * each entity's {@code @Id}; for {@code ObjectId}-keyed entities a fresh {@code ObjectId} is
 * generated for new records (its hex becomes the record id), so existing MongoDB data is untouched.
 */
public final class SurrealGameRepository implements GameRepository {
    private static final String NS = "grasscutter";

    private final DocumentMapper mapper = new DocumentMapper();
    private Surreal gameDb;
    private Surreal dispatchDb;

    private Surreal accountDb() {
        return SERVER.runMode == ServerRunMode.GAME_ONLY ? dispatchDb : gameDb;
    }

    @Override
    public void initialize() {
        gameDb = open(DATABASE.game.connectionUri, DATABASE.game.collection);
        if (SERVER.runMode == ServerRunMode.GAME_ONLY) {
            dispatchDb = open(DATABASE.server.connectionUri, DATABASE.server.collection);
        } else {
            dispatchDb = gameDb;
        }
        defineIndexes();
    }

    private Surreal open(String uri, String database) {
        Surreal s = new Surreal();
        s.connect(uri);
        s.useNs(NS).useDb(database);
        Grasscutter.getLogger().info("Connected to SurrealDB backend ({}), db={}", uri, database);
        return s;
    }

    /** Emits {@code DEFINE INDEX} for every collection entity's Morphia index metadata. */
    private void defineIndexes() {
        Grasscutter.reflector.getTypesAnnotatedWith(Entity.class).stream()
            .filter(cls -> {
                Entity e = cls.getAnnotation(Entity.class);
                return e != null && !e.value().equals(Mapper.IGNORED_FIELDNAME);
            })
            // The experimental module system (single-table discriminator) is out of scope (Phase 6).
            .filter(cls -> !cls.getName().startsWith("emu.grasscutter.game.modules"))
            .forEach(cls -> {
                EntityMapping m = MorphiaMappingAdapter.of(cls);
                if (m.table() == null) return;
                int i = 0;
                for (EntityMapping.Index idx : m.indexes()) {
                    String name = m.table() + "_idx_" + (i++);
                    String fields = String.join(", ", idx.fields());
                    String sql = "DEFINE INDEX IF NOT EXISTS " + name + " ON TABLE " + m.table()
                        + " FIELDS " + fields + (idx.unique() ? " UNIQUE" : "") + ";";
                    try {
                        gameDb.query(sql);
                    } catch (RuntimeException ex) {
                        Grasscutter.getLogger().warn("Failed to define index: {}", sql, ex);
                    }
                }
            });
    }

    // region core mapping helpers
    private static String table(Class<?> type) {
        return MorphiaMappingAdapter.of(type).table();
    }

    /** Builds the record id from an entity's {@code @Id}, generating one for new ObjectId keys. */
    private RecordId recordId(Object entity) {
        EntityMapping m = MorphiaMappingAdapter.of(entity.getClass());
        Field idField = m.idField();
        try {
            Object idv = idField.get(entity);
            if (idv == null) {
                if (idField.getType() == ObjectId.class) {
                    idv = new ObjectId();
                } else if (idField.getType() == String.class) {
                    idv = new ObjectId().toHexString();
                } else {
                    throw new IllegalStateException("Null id for " + entity.getClass());
                }
                idField.set(entity, idv);
            }
            return toRecordId(m.table(), idv);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static RecordId toRecordId(String table, Object idv) {
        if (idv instanceof ObjectId oid) return new RecordId(table, oid.toHexString());
        if (idv instanceof String s) return new RecordId(table, s);
        if (idv instanceof Integer i) return new RecordId(table, (long) i);
        if (idv instanceof Long l) return new RecordId(table, (long) l);
        return new RecordId(table, String.valueOf(idv));
    }

    /** Serializes an entity to record content (its {@code @Id} field is carried by the record id). */
    private Map<String, Object> content(Object entity) {
        Map<String, Object> doc = mapper.toDocument(entity);
        doc.remove(MorphiaMappingAdapter.of(entity.getClass()).idField().getName());
        return doc;
    }

    private <T> void upsert(T entity) {
        gameDb.upsert(recordId(entity), UpType.CONTENT, content(entity));
    }

    /** Reconstructs an entity from a result Value, restoring its {@code @Id} from the record id. */
    private <T> T fromValue(Class<T> type, Value value) {
        var obj = value.getObject();
        Map<String, Object> map = new LinkedHashMap<>();
        com.surrealdb.RecordId rid = null;
        for (Entry e : obj) {
            if (e.getKey().equals("id") && e.getValue().isRecordId()) {
                rid = e.getValue().getRecordId();
            } else {
                map.put(e.getKey(), valueToJava(e.getValue()));
            }
        }
        T entity = mapper.fromDocument(type, map);
        if (entity != null && rid != null) restoreId(type, entity, rid);
        return entity;
    }

    private <T> void restoreId(Class<T> type, T entity, RecordId rid) {
        Field idField = MorphiaMappingAdapter.of(type).idField();
        if (idField == null) return;
        com.surrealdb.Id id = rid.getId();
        Class<?> ft = idField.getType();
        try {
            Object v;
            if (ft == int.class || ft == Integer.class) v = (int) id.getLong();
            else if (ft == long.class || ft == Long.class) v = id.getLong();
            else if (ft == ObjectId.class) v = new ObjectId(id.getString());
            else v = id.isString() ? id.getString() : String.valueOf(id.getLong());
            idField.set(entity, v);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /** Recursively converts a SurrealDB Value to plain Java (Map/List/scalar). */
    private static Object valueToJava(Value v) {
        if (v == null || v.isNull() || v.isNone()) return null;
        if (v.isObject()) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (Entry e : v.getObject()) m.put(e.getKey(), valueToJava(e.getValue()));
            return m;
        }
        if (v.isArray()) {
            List<Object> list = new ArrayList<>();
            for (Value e : v.getArray()) list.add(valueToJava(e));
            return list;
        }
        if (v.isString()) return v.getString();
        if (v.isLong()) return v.getLong();
        if (v.isDouble()) return v.getDouble();
        if (v.isBoolean()) return v.getBoolean();
        if (v.isRecordId()) return v.getRecordId().toString();
        if (v.isUuid()) return v.getUuid().toString();
        if (v.isDateTime()) return v.getDateTime().toString();
        return v.toString();
    }

    private <T> T selectOne(Surreal db, Class<T> type, RecordId rid) {
        return db.select(rid).map(v -> fromValue(type, v)).orElse(null);
    }

    private <T> List<T> queryList(Surreal db, Class<T> type, String sql, Map<String, ?> params) {
        Response r = params == null ? db.query(sql) : db.queryBind(sql, params);
        List<T> out = new ArrayList<>();
        Value result = r.take(0);
        if (result.isArray()) {
            for (Value v : result.getArray()) out.add(fromValue(type, v));
        }
        return out;
    }

    private <T> T queryFirst(Surreal db, Class<T> type, String sql, Map<String, ?> params) {
        List<T> list = queryList(db, type, sql, params);
        return list.isEmpty() ? null : list.get(0);
    }
    // endregion

    // region counters
    @Override
    public synchronized int getNextId(Class<?> c) {
        String name = c.getSimpleName();
        Response r = gameDb.query("UPSERT counters:" + name
            + " SET count = (count ?? 10000) + 1 RETURN AFTER;");
        Object j = valueToJava(r.take(0));
        if (j instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> row
                && row.get("count") instanceof Number n) {
            return n.intValue();
        }
        throw new IllegalStateException("Failed to read counter for " + name);
    }
    // endregion

    // region accounts
    @Override
    public Account getAccountById(String uid) {
        return selectOne(accountDb(), Account.class, new RecordId(table(Account.class), uid));
    }

    @Override
    public Account getAccountByName(String username) {
        return queryFirst(accountDb(), Account.class,
            "SELECT * FROM " + table(Account.class) + " WHERE username = $v", Map.of("v", username));
    }

    @Override
    public Account getAccountByToken(String token) {
        if (token == null) return null;
        return queryFirst(accountDb(), Account.class,
            "SELECT * FROM " + table(Account.class) + " WHERE token = $v", Map.of("v", token));
    }

    @Override
    public Account getAccountBySessionKey(String sessionKey) {
        if (sessionKey == null) return null;
        return queryFirst(accountDb(), Account.class,
            "SELECT * FROM " + table(Account.class) + " WHERE sessionKey = $v", Map.of("v", sessionKey));
    }

    @Override
    public Account getAccountByPlayerId(int playerId) {
        return queryFirst(accountDb(), Account.class,
            "SELECT * FROM " + table(Account.class) + " WHERE reservedPlayerId = $v", Map.of("v", playerId));
    }

    @Override
    public boolean checkIfAccountExists(String name) {
        return getAccountByName(name) != null;
    }

    @Override
    public boolean checkIfAccountExists(int reservedUid) {
        return getAccountByPlayerId(reservedUid) != null;
    }

    @Override
    public void saveAccount(Account account) {
        accountDb().upsert(recordId(account), UpType.CONTENT, content(account));
    }

    @Override
    public synchronized void deleteAccountAndData(Account target, int uid) {
        Map<String, Object> p = Map.of("u", uid);
        gameDb.queryBind("DELETE activities WHERE uid = $u", p);
        gameDb.queryBind("DELETE homes WHERE ownerUid = $u", p);
        gameDb.queryBind("DELETE mail WHERE ownerUid = $u", p);
        gameDb.queryBind("DELETE avatars WHERE ownerId = $u", p);
        gameDb.queryBind("DELETE gachas WHERE ownerId = $u", p);
        gameDb.queryBind("DELETE items WHERE ownerId = $u", p);
        gameDb.queryBind("DELETE quests WHERE ownerUid = $u", p);
        gameDb.queryBind("DELETE battlepass WHERE ownerUid = $u", p);
        gameDb.queryBind("DELETE friendships WHERE ownerId = $u OR friendId = $u", p);
        gameDb.delete(new RecordId(table(Player.class), uid));
        accountDb().delete(new RecordId(table(Account.class), target.getId()));
    }
    // endregion

    // region players
    @Override
    public Player getPlayerByUid(int id) {
        return selectOne(gameDb, Player.class, new RecordId(table(Player.class), id));
    }

    @Override
    public Player getPlayerByAccountId(String accountId, Class<? extends Player> playerClass) {
        return queryFirst(gameDb, playerClass,
            "SELECT * FROM " + table(Player.class) + " WHERE accountId = $v", Map.of("v", accountId));
    }

    @Override
    public boolean checkIfPlayerExists(int uid) {
        return getPlayerByUid(uid) != null;
    }

    @Override
    public void savePlayer(Player character) {
        upsert(character);
    }

    @Override
    public List<Player> getAllPlayers() {
        return queryList(gameDb, Player.class, "SELECT * FROM " + table(Player.class), null);
    }
    // endregion

    // region avatars
    @Override
    public void saveAvatar(Avatar avatar) {
        upsert(avatar);
    }

    @Override
    public List<Avatar> getAvatars(int ownerUid) {
        return queryList(gameDb, Avatar.class,
            "SELECT * FROM " + table(Avatar.class) + " WHERE ownerId = $v", Map.of("v", ownerUid));
    }
    // endregion

    // region items
    @Override
    public void saveItem(GameItem item) {
        upsert(item);
    }

    @Override
    public boolean deleteItem(GameItem item) {
        gameDb.delete(recordId(item));
        return true;
    }

    @Override
    public List<GameItem> getInventoryItems(int ownerUid) {
        return queryList(gameDb, GameItem.class,
            "SELECT * FROM " + table(GameItem.class) + " WHERE ownerId = $v", Map.of("v", ownerUid));
    }
    // endregion

    // region friendships
    @Override
    public List<Friendship> getFriends(int ownerUid) {
        return queryList(gameDb, Friendship.class,
            "SELECT * FROM " + table(Friendship.class) + " WHERE ownerId = $v", Map.of("v", ownerUid));
    }

    @Override
    public List<Friendship> getReverseFriends(int friendUid) {
        return queryList(gameDb, Friendship.class,
            "SELECT * FROM " + table(Friendship.class) + " WHERE friendId = $v", Map.of("v", friendUid));
    }

    @Override
    public void saveFriendship(Friendship friendship) {
        upsert(friendship);
    }

    @Override
    public void deleteFriendship(Friendship friendship) {
        gameDb.delete(recordId(friendship));
    }

    @Override
    public Friendship getReverseFriendship(int ownerId, int friendId) {
        return queryFirst(gameDb, Friendship.class,
            "SELECT * FROM " + table(Friendship.class) + " WHERE ownerId = $o AND friendId = $f",
            Map.of("o", friendId, "f", ownerId));
    }
    // endregion

    // region gacha
    @Override
    public List<GachaRecord> getGachaRecords(int ownerId, int gachaType, int page, int pageSize) {
        return queryList(gameDb, GachaRecord.class,
            "SELECT * FROM " + table(GachaRecord.class)
                + " WHERE ownerId = $o AND gachaType = $t"
                + " ORDER BY transactionDate DESC LIMIT $lim START $start",
            Map.of("o", ownerId, "t", gachaType, "lim", pageSize, "start", pageSize * page));
    }

    @Override
    public long getGachaRecordsCount(int ownerId, int gachaType) {
        Response r = gameDb.queryBind("SELECT count() FROM " + table(GachaRecord.class)
            + " WHERE ownerId = $o AND gachaType = $t GROUP ALL", Map.of("o", ownerId, "t", gachaType));
        Object j = valueToJava(r.take(0));
        if (j instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> row
                && row.get("count") instanceof Number n) {
            return n.longValue();
        }
        return 0;
    }

    @Override
    public void saveGachaRecord(GachaRecord gachaRecord) {
        upsert(gachaRecord);
    }
    // endregion

    // region mail
    @Override
    public List<Mail> getAllMail(int ownerUid) {
        return queryList(gameDb, Mail.class,
            "SELECT * FROM " + table(Mail.class) + " WHERE ownerUid = $v", Map.of("v", ownerUid));
    }

    @Override
    public void saveMail(Mail mail) {
        upsert(mail);
    }

    @Override
    public boolean deleteMail(Mail mail) {
        gameDb.delete(recordId(mail));
        return true;
    }
    // endregion

    // region quests
    @Override
    public List<GameMainQuest> getAllQuests(int ownerUid) {
        return queryList(gameDb, GameMainQuest.class,
            "SELECT * FROM " + table(GameMainQuest.class) + " WHERE ownerUid = $v", Map.of("v", ownerUid));
    }

    @Override
    public void saveQuest(GameMainQuest quest) {
        upsert(quest);
    }

    @Override
    public boolean deleteQuest(GameMainQuest quest) {
        gameDb.delete(recordId(quest));
        return true;
    }
    // endregion

    // region home
    @Override
    public GameHome getHomeByUid(int id) {
        return queryFirst(gameDb, GameHome.class,
            "SELECT * FROM " + table(GameHome.class) + " WHERE ownerUid = $v", Map.of("v", id));
    }

    @Override
    public void saveHome(GameHome gameHome) {
        upsert(gameHome);
    }
    // endregion

    // region battlepass
    @Override
    public BattlePassManager getBattlePassByUid(int ownerUid) {
        return queryFirst(gameDb, BattlePassManager.class,
            "SELECT * FROM " + table(BattlePassManager.class) + " WHERE ownerUid = $v", Map.of("v", ownerUid));
    }

    @Override
    public void saveBattlePass(BattlePassManager manager) {
        upsert(manager);
    }
    // endregion

    // region activity
    @Override
    public PlayerActivityData getPlayerActivityData(int uid, int activityId) {
        return queryFirst(gameDb, PlayerActivityData.class,
            "SELECT * FROM " + table(PlayerActivityData.class) + " WHERE uid = $u AND activityId = $a",
            Map.of("u", uid, "a", activityId));
    }

    @Override
    public void savePlayerActivityData(PlayerActivityData playerActivityData) {
        upsert(playerActivityData);
    }
    // endregion

    // region music game
    @Override
    public MusicGameBeatmap getMusicGameBeatmap(long musicShareId) {
        return selectOne(gameDb, MusicGameBeatmap.class,
            new RecordId(table(MusicGameBeatmap.class), musicShareId));
    }

    @Override
    public void saveMusicGameBeatmap(MusicGameBeatmap musicGameBeatmap) {
        upsert(musicGameBeatmap);
    }
    // endregion

    // region scene instances
    @Override
    public void saveGroupInstance(SceneGroupInstance instance) {
        upsert(instance);
    }

    @Override
    public SceneGroupInstance loadGroupInstance(int ownerUid, int groupId) {
        return queryFirst(gameDb, SceneGroupInstance.class,
            "SELECT * FROM " + table(SceneGroupInstance.class) + " WHERE ownerUid = $o AND groupId = $g",
            Map.of("o", ownerUid, "g", groupId));
    }

    @Override
    public void saveSceneInstanceData(SceneInstanceData instance) {
        upsert(instance);
    }

    @Override
    public SceneInstanceData loadSceneInstanceData(int ownerUid, int sceneId) {
        return queryFirst(gameDb, SceneInstanceData.class,
            "SELECT * FROM " + table(SceneInstanceData.class) + " WHERE ownerUid = $o AND sceneId = $s",
            Map.of("o", ownerUid, "s", sceneId));
    }
    // endregion

    // region generic
    @Override
    public <T> Stream<T> getByClass(Class<T> classType) {
        return queryList(gameDb, classType, "SELECT * FROM " + table(classType), null).stream();
    }
    // endregion
}
