package emu.grasscutter.database;

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

import java.util.List;
import java.util.stream.Stream;

/**
 * Backend-agnostic persistence layer for the game server.
 *
 * <p>This is the seam that decouples game logic from a specific database. Each supported backend
 * (Morphia/MongoDB today, SurrealDB later) provides one implementation. All methods are explicit
 * per-entity data-access operations; higher-level orchestration (account/player creation, uid
 * generation, cross-collection account deletion) lives in {@link DatabaseHelper} on top of these.
 *
 * <p>Implementations must not leak backend-specific types (e.g. Morphia {@code Datastore}, Mongo
 * {@code MongoDatabase}) across this interface.
 */
public interface GameRepository {

    // region lifecycle
    /** Open connections, map entities, and ensure indexes. Called once at startup. */
    void initialize();
    // endregion

    // region counters
    /** Atomically returns the next auto-increment id for the given class. */
    int getNextId(Class<?> type);
    // endregion

    // region accounts (routed to the account/dispatch datastore where applicable)
    Account getAccountById(String uid);

    Account getAccountByName(String username);

    Account getAccountByToken(String token);

    Account getAccountBySessionKey(String sessionKey);

    Account getAccountByPlayerId(int playerId);

    boolean checkIfAccountExists(String username);

    boolean checkIfAccountExists(int reservedUid);

    void saveAccount(Account account);

    /**
     * Deletes an account together with all game data owned by the given player uid
     * (activities, homes, mail, avatars, gachas, items, quests, battlepass, friendships, and the
     * player record itself). The caller is responsible for closing any live session first.
     */
    void deleteAccountAndData(Account account, int playerUid);
    // endregion

    // region players
    Player getPlayerByUid(int uid);

    Player getPlayerByAccountId(String accountId, Class<? extends Player> playerClass);

    boolean checkIfPlayerExists(int uid);

    void savePlayer(Player player);

    List<Player> getAllPlayers();
    // endregion

    // region avatars
    void saveAvatar(Avatar avatar);

    List<Avatar> getAvatars(int ownerUid);
    // endregion

    // region items
    void saveItem(GameItem item);

    boolean deleteItem(GameItem item);

    List<GameItem> getInventoryItems(int ownerUid);
    // endregion

    // region friendships
    List<Friendship> getFriends(int ownerUid);

    List<Friendship> getReverseFriends(int friendUid);

    void saveFriendship(Friendship friendship);

    void deleteFriendship(Friendship friendship);

    Friendship getReverseFriendship(int ownerId, int friendId);
    // endregion

    // region gacha
    List<GachaRecord> getGachaRecords(int ownerId, int gachaType, int page, int pageSize);

    long getGachaRecordsCount(int ownerId, int gachaType);

    void saveGachaRecord(GachaRecord gachaRecord);
    // endregion

    // region mail
    List<Mail> getAllMail(int ownerUid);

    void saveMail(Mail mail);

    boolean deleteMail(Mail mail);
    // endregion

    // region quests
    List<GameMainQuest> getAllQuests(int ownerUid);

    void saveQuest(GameMainQuest quest);

    boolean deleteQuest(GameMainQuest quest);
    // endregion

    // region home
    GameHome getHomeByUid(int ownerUid);

    void saveHome(GameHome gameHome);
    // endregion

    // region battlepass
    BattlePassManager getBattlePassByUid(int ownerUid);

    void saveBattlePass(BattlePassManager manager);
    // endregion

    // region activity
    PlayerActivityData getPlayerActivityData(int uid, int activityId);

    void savePlayerActivityData(PlayerActivityData playerActivityData);
    // endregion

    // region music game
    MusicGameBeatmap getMusicGameBeatmap(long musicShareId);

    void saveMusicGameBeatmap(MusicGameBeatmap musicGameBeatmap);
    // endregion

    // region scene instances
    void saveGroupInstance(SceneGroupInstance instance);

    SceneGroupInstance loadGroupInstance(int ownerUid, int groupId);

    void saveSceneInstanceData(SceneInstanceData instance);

    SceneInstanceData loadSceneInstanceData(int ownerUid, int sceneId);
    // endregion

    // region modules
    /**
     * Loads the owner's instance of a {@link GameModuleData} subtype, or {@code null} if none is
     * stored. Modules share one collection distinguished by the entity's discriminator.
     */
    <T extends GameModuleData> T loadModule(Class<T> type, int ownerId);

    /** Inserts or replaces the owner's instance of a module (keyed by owner + discriminator). */
    void saveModule(GameModuleData module);
    // endregion

    // region generic
    <T> Stream<T> getByClass(Class<T> classType);
    // endregion
}
