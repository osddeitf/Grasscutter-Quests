package emu.grasscutter.game.modules.shop;

import emu.grasscutter.game.player.BasePlayerManager;
import emu.grasscutter.game.player.Player;
import lombok.Getter;
import lombok.NonNull;

public class ShopModule extends BasePlayerManager {

    // persistent
    @Getter private MoonCardManager moonCardManager;

    public ShopModule(@NonNull Player player) {
        super(player);
    }

    public static ShopModule getInstance(@NonNull Player player) {
        return player.getShopModule();
    }

    public static void onLogin(@NonNull Player player) {
        var self = getInstance(player);
        self.moonCardManager = MoonCardManager.loadFromDatabase(player);
        self.moonCardManager.getTodayMoonCard();
    }
}
