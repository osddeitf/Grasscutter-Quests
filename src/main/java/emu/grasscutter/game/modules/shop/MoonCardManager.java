package emu.grasscutter.game.modules.shop;

import dev.morphia.annotations.Entity;
import dev.morphia.query.filters.Filters;
import emu.grasscutter.config.GameSchedule;
import emu.grasscutter.database.DatabaseManager;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.modules.GameModuleData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.server.packet.send.PacketCardProductRewardNotify;
import lombok.NonNull;
import org.anime_game_servers.multi_proto.gi.messages.shop.ShopCardProduct;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedList;

@Entity(value = GameModuleData.COLLECTION, discriminator = "grasscutter.shop.moon_card")
public class MoonCardManager extends GameModuleData {

    // Hard code Product id keep cool 😎
//    public static final String MOON_CARD_PRODUCT_ID = "ys_chn_blessofmoon_tier5";
    public static final String MOON_CARD_PRODUCT_ID = "ys_glb_blessofmoon_tier5";
    public static final String MOON_CARD_PRODUCT_TIER = "Tier5";
    private static final int ITEM_GENESIS_CRYSTAL = 203;
    private static final int ITEM_GENESIS_CRYSTAL_AMOUNT = 300;
    private static final int ITEM_PRIMO_GEM = 201;
    public static final int ITEM_PRIMO_GEM_AMOUNT = 90;
    private static final int MOON_CARD_ADD_DURATION = 30;
    private static final int MOON_CARD_MAX_DURATION = 180;

    // Just for keepsake. TODO: investigate why sometimes it's not usable (the client prevents it)
    public static final int RECHARGE_MOON_CARD_ITEM_ID = 1202;

    // persistent
    private Instant moonCardExpiration = null;
    private final LinkedList<Instant> moonCardGetTimes = new LinkedList<>();

    // transient
    private transient Player player;

    // morphia-only
    private MoonCardManager() {
        super(0);
    }

    public MoonCardManager(@NonNull Player player) {
        super(player.getUid());
        this.player = player;
    }

    public static MoonCardManager getInstance(@NonNull Player player) {
        return player.getShopModule().getMoonCardManager();
    }

    public boolean isMoonCardActive() {
        return moonCardExpiration != null && moonCardExpiration.isAfter(Instant.now());
    }

    public boolean isClaimedToday() {
        return moonCardGetTimes.contains(GameSchedule.currentDayStart());
    }

    public int getMoonCardRemainDays() {
        if (moonCardExpiration == null) return 0;
        // It is capped, so the casting here should be safe
        return (int) GameSchedule.difference(GameSchedule.nextDailyReset(), moonCardExpiration, ChronoUnit.DAYS);
    }

    public boolean rechargeMoonCard() {
        var duration = getMoonCardRemainDays() + MOON_CARD_ADD_DURATION;
        if (duration > MOON_CARD_MAX_DURATION) return false;
        player.getInventory().addItem(new GameItem(ITEM_GENESIS_CRYSTAL, ITEM_GENESIS_CRYSTAL_AMOUNT));
        moonCardExpiration = GameSchedule.nextDailyResetFromNow(isClaimedToday()? duration + 1: duration);
        saveToDatabase();
        getTodayMoonCard();
        return true;
    }

    public void getTodayMoonCard() {
        if (!isMoonCardActive()) return;
        if (isClaimedToday()) return;

        // reward
        GameItem item = new GameItem(ITEM_PRIMO_GEM, ITEM_PRIMO_GEM_AMOUNT);
        player.getInventory().addItem(item, ActionReason.BlessingRedeemReward);
        player.sendPacket(new PacketCardProductRewardNotify(this));

        // mark today as claimed
        var today = GameSchedule.currentDayStart();
        moonCardGetTimes.addLast(today);
        if (moonCardGetTimes.size() > 30) {
            moonCardGetTimes.removeLast();
        }

        // persist changes
        saveToDatabase();
    }

    public ShopCardProduct getMoonCardProduct() {
        var proto = new ShopCardProduct();
        proto.setProductId(MOON_CARD_PRODUCT_ID);
        proto.setPriceTier(MOON_CARD_PRODUCT_TIER);
        proto.setMcoinBase(ITEM_GENESIS_CRYSTAL_AMOUNT);
        proto.setHcoinPerDay(ITEM_PRIMO_GEM_AMOUNT);
        proto.setDays(MOON_CARD_ADD_DURATION);
        proto.setRemainRewardDays(getMoonCardRemainDays());
        proto.setCardProductType(CardProductType.CARD_PRODUCT_TYPE_HCOIN.getValue());
        return proto;
    }

    public static MoonCardManager loadFromDatabase(@NonNull Player player) {
        // Experimental module system persists only on the Morphia backend for now (Phase 6).
        if (DatabaseManager.getGameDatastore() == null) return new MoonCardManager(player);
        var saved = DatabaseManager.getGameDatastore()
            .find(MoonCardManager.class)
            .filter(Filters.eq("ownerId", player.getUid()))
            .first();

        if (saved == null) {
            return new MoonCardManager(player);
        }

        // init if previously persisted
        saved.player = player;
        return saved;
    }

    private void saveToDatabase() {
        if (DatabaseManager.getGameDatastore() == null) return;
        DatabaseManager.getGameDatastore().save(this);
    }
}
