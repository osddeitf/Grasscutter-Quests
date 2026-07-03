package emu.grasscutter.database;

import java.util.List;
import java.util.stream.Stream;

import emu.grasscutter.GameConstants;
import emu.grasscutter.Grasscutter;
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

/**
 * Static facade over the active {@link GameRepository}. Preserves the historical API used across
 * the codebase; all data access is delegated to the configured backend, while higher-level
 * orchestration (account/player creation, uid generation, battlepass load-or-create, account
 * deletion) lives here so it is not duplicated per backend.
 */
public final class DatabaseHelper {

    private static GameRepository repo() {
        return DatabaseManager.getRepository();
    }

    public static Account createAccount(String username) {
        return createAccountWithUid(username, 0);
    }

    public static Account createAccountWithUid(String username, int reservedUid) {
        // Unique names only
        if (DatabaseHelper.checkIfAccountExists(username)) {
            return null;
        }

        // Make sure there are no id collisions
        if (reservedUid > 0) {
            // Cannot make account with the same uid as the server console
            if (reservedUid == GameConstants.SERVER_CONSOLE_UID) {
                return null;
            }

            if (DatabaseHelper.checkIfAccountExists(reservedUid)) {
                return null;
            }

            // Make sure no existing player already has this id.
            if (DatabaseHelper.checkIfPlayerExists(reservedUid)) {
                return null;
            }
        }

        // Account
        Account account = new Account();
        account.setUsername(username);
        account.setId(Integer.toString(DatabaseManager.getNextId(account)));

        if (reservedUid > 0) {
            account.setReservedPlayerUid(reservedUid);
        }

        DatabaseHelper.saveAccount(account);
        return account;
    }

    @Deprecated
    public static Account createAccountWithPassword(String username, String password) {
        // Unique names only
        Account exists = DatabaseHelper.getAccountByName(username);
        if (exists != null) {
            return null;
        }

        // Account
        Account account = new Account();
        account.setId(Integer.toString(DatabaseManager.getNextId(account)));
        account.setUsername(username);
        account.setPassword(password);
        DatabaseHelper.saveAccount(account);
        return account;
    }

    public static void saveAccount(Account account) {
        repo().saveAccount(account);
    }

    public static Account getAccountByName(String username) {
        return repo().getAccountByName(username);
    }

    public static Account getAccountByToken(String token) {
        return repo().getAccountByToken(token);
    }

    public static Account getAccountBySessionKey(String sessionKey) {
        return repo().getAccountBySessionKey(sessionKey);
    }

    public static Account getAccountById(String uid) {
        return repo().getAccountById(uid);
    }

    public static Account getAccountByPlayerId(int playerId) {
        return repo().getAccountByPlayerId(playerId);
    }

    public static boolean checkIfAccountExists(String name) {
        return repo().checkIfAccountExists(name);
    }

    public static boolean checkIfAccountExists(int reservedUid) {
        return repo().checkIfAccountExists(reservedUid);
    }

    public static synchronized void deleteAccount(Account target) {
        // To delete an account, we need to also delete all the other documents in the database that reference the account.
        // This should optimally be wrapped inside a transaction, to make sure an error thrown mid-way does not leave the
        // database in an inconsistent state, but unfortunately Mongo only supports that when we have a replica set ...

        Player player = Grasscutter.getGameServer().getPlayerByAccountId(target.getId());

        // Close session first
        if (player != null) {
            player.getSession().close();
        } else {
            player = getPlayerByAccount(target);
            if (player == null) return;
        }
        int uid = player.getUid();

        repo().deleteAccountAndData(target, uid);
    }

    public static <T> Stream<T> getByGameClass(Class<T> classType) {
        return repo().getByClass(classType);
    }

    @Deprecated(forRemoval = true)
    public static List<Player> getAllPlayers() {
        return repo().getAllPlayers();
    }

    public static Player getPlayerByUid(int id) {
        return repo().getPlayerByUid(id);
    }

    @Deprecated
    public static Player getPlayerByAccount(Account account) {
        return repo().getPlayerByAccountId(account.getId(), Player.class);
    }

    public static Player getPlayerByAccount(Account account, Class<? extends Player> playerClass) {
        return repo().getPlayerByAccountId(account.getId(), playerClass);
    }

    public static Player getPlayerByAccount(String accountId, Class<? extends Player> playerClass) {
        return repo().getPlayerByAccountId(accountId, playerClass);
    }

    public static boolean checkIfPlayerExists(int uid) {
        return repo().checkIfPlayerExists(uid);
    }

    public static synchronized Player generatePlayerUid(Player character, int reservedId) {
        // Check if reserved id
        int id;
        if (reservedId > 0 && !checkIfPlayerExists(reservedId)) {
            id = reservedId;
            character.setUid(id);
        } else {
            do {
                id = DatabaseManager.getNextId(character);
            }
            while (checkIfPlayerExists(id));
            character.setUid(id);
        }
        // Save to database
        repo().savePlayer(character);
        return character;
    }

    public static synchronized int getNextPlayerId(int reservedId) {
        // Check if reserved id
        int id;
        if (reservedId > 0 && !checkIfPlayerExists(reservedId)) {
            id = reservedId;
        } else {
            do {
                id = DatabaseManager.getNextId(Player.class);
            }
            while (checkIfPlayerExists(id));
        }
        return id;
    }

    public static void savePlayer(Player character) {
        repo().savePlayer(character);
    }

    public static void saveAvatar(Avatar avatar) {
        repo().saveAvatar(avatar);
    }

    public static List<Avatar> getAvatars(Player player) {
        return repo().getAvatars(player.getUid());
    }

    public static void saveItem(GameItem item) {
        repo().saveItem(item);
    }

    public static boolean deleteItem(GameItem item) {
        return repo().deleteItem(item);
    }

    public static List<GameItem> getInventoryItems(Player player) {
        return repo().getInventoryItems(player.getUid());
    }

    public static List<Friendship> getFriends(Player player) {
        return repo().getFriends(player.getUid());
    }

    public static List<Friendship> getReverseFriends(Player player) {
        return repo().getReverseFriends(player.getUid());
    }

    public static void saveFriendship(Friendship friendship) {
        repo().saveFriendship(friendship);
    }

    public static void deleteFriendship(Friendship friendship) {
        repo().deleteFriendship(friendship);
    }

    public static Friendship getReverseFriendship(Friendship friendship) {
        return repo().getReverseFriendship(friendship.getOwnerId(), friendship.getFriendId());
    }

    public static List<GachaRecord> getGachaRecords(int ownerId, int page, int gachaType) {
        return getGachaRecords(ownerId, page, gachaType, 10);
    }

    public static List<GachaRecord> getGachaRecords(int ownerId, int page, int gachaType, int pageSize) {
        return repo().getGachaRecords(ownerId, gachaType, page, pageSize);
    }

    public static long getGachaRecordsMaxPage(int ownerId, int page, int gachaType) {
        return getGachaRecordsMaxPage(ownerId, page, gachaType, 10);
    }

    public static long getGachaRecordsMaxPage(int ownerId, int page, int gachaType, int pageSize) {
        long count = repo().getGachaRecordsCount(ownerId, gachaType);
        return count / 10 + (count % 10 > 0 ? 1 : 0 );
    }

    public static void saveGachaRecord(GachaRecord gachaRecord) {
        repo().saveGachaRecord(gachaRecord);
    }

    public static List<Mail> getAllMail(Player player) {
        return repo().getAllMail(player.getUid());
    }

    public static void saveMail(Mail mail) {
        repo().saveMail(mail);
    }

    public static boolean deleteMail(Mail mail) {
        return repo().deleteMail(mail);
    }

    public static List<GameMainQuest> getAllQuests(Player player) {
        return repo().getAllQuests(player.getUid());
    }

    public static void saveQuest(GameMainQuest quest) {
        try {
            repo().saveQuest(quest);
        } catch (Exception exception) {
            Grasscutter.getLogger().error("Failed to save quest m{}", quest.getParentQuestId(), exception);
        }
    }

    public static boolean deleteQuest(GameMainQuest quest) {
        return repo().deleteQuest(quest);
    }

    public static GameHome getHomeByUid(int id) {
        return repo().getHomeByUid(id);
    }

    public static void saveHome(GameHome gameHome) {
        repo().saveHome(gameHome);
    }

    public static BattlePassManager loadBattlePass(Player player) {
        BattlePassManager manager = repo().getBattlePassByUid(player.getUid());
        if (manager == null) {
            manager = new BattlePassManager(player);
            manager.save();
        } else {
            manager.setPlayer(player);
        }
        return manager;
    }

    public static void saveBattlePass(BattlePassManager manager) {
        repo().saveBattlePass(manager);
    }

    public static PlayerActivityData getPlayerActivityData(int uid, int activityId) {
        return repo().getPlayerActivityData(uid, activityId);
    }

    public static void savePlayerActivityData(PlayerActivityData playerActivityData) {
        repo().savePlayerActivityData(playerActivityData);
    }

    public static MusicGameBeatmap getMusicGameBeatmap(long musicShareId) {
        return repo().getMusicGameBeatmap(musicShareId);
    }

    public static void saveMusicGameBeatmap(MusicGameBeatmap musicGameBeatmap) {
        repo().saveMusicGameBeatmap(musicGameBeatmap);
    }

    public static void saveGroupInstance(SceneGroupInstance instance) {
        repo().saveGroupInstance(instance);
    }

    public static SceneGroupInstance loadGroupInstance(int groupId, Player owner) {
        return repo().loadGroupInstance(owner.getUid(), groupId);
    }

    public static void saveSceneInstanceData(SceneInstanceData instance) {
        repo().saveSceneInstanceData(instance);
    }

    public static SceneInstanceData loadSceneInstanceData(int sceneId, Player owner) {
        return repo().loadSceneInstanceData(owner.getUid(), sceneId);
    }
}
