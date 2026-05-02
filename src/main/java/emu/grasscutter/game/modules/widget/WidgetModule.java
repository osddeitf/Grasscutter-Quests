package emu.grasscutter.game.modules.widget;

import emu.grasscutter.game.modules.widget.effect.WidgetEffectManager;
import emu.grasscutter.game.modules.widget.manager.*;
import emu.grasscutter.game.player.BasePlayerManager;
import emu.grasscutter.game.player.Player;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.Getter;
import lombok.NonNull;

import java.util.HashMap;
import java.util.Map;

public class WidgetModule extends BasePlayerManager {

    @Getter static final Int2ObjectOpenHashMap<WidgetConfig> widgetConfigMap = new Int2ObjectOpenHashMap<>();
    @Getter static final Int2ObjectOpenHashMap<WidgetCoolDownGroup> widgetCoolDownGroupMap = new Int2ObjectOpenHashMap<>();

    // persist
    @Getter private WidgetSwitchManager switchManager;

    // non-persist
    @Getter private final WidgetEffectManager effectManager = new WidgetEffectManager();
    @Getter private final WidgetCoolDownManager coolDownManager = new WidgetCoolDownManager();

    public WidgetModule(@NonNull Player player) {
        super(player);
    }

    public static WidgetModule getInstance(@NonNull Player player) {
        return player.getWidgetModule();
    }

    public static void onLogin(@NonNull Player player) {
        var self = getInstance(player);
        self.switchManager = WidgetSwitchManager.loadFromDatabase(player);
    }

    public static class WidgetConfigContainer {
        public Map<Integer, WidgetConfig> widgetConfigMap = new HashMap<>();
        public Map<Integer, WidgetCoolDownGroup> cdGroupConfigMap = new HashMap<>();
    }
}
