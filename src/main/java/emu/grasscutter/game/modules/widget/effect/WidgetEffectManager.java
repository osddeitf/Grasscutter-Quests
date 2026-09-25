package emu.grasscutter.game.modules.widget.effect;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.modules.widget.WidgetModule;
import emu.grasscutter.game.player.Player;
import lombok.NonNull;

import java.util.*;

public class WidgetEffectManager {

    private final Map<Integer, List<BaseWidgetEffect>> widgetEffects = new HashMap<>();

    public static WidgetEffectManager getInstance(@NonNull Player player) {
        return player.getWidgetModule().getEffectManager();
    }

    public static void registerEffect(@NonNull Player player, int materialId) {
        try {
            var self = getInstance(player);
            if (self.widgetEffects.containsKey(materialId)) return; // skip registered

            var effects = getEquipEffects(player, materialId);
            for (var e: effects) e.attach();
            self.widgetEffects.put(materialId, List.copyOf(effects));
        }
        catch (Exception e) {
            // ignore if error occurs
            Grasscutter.getLogger().error("Failed to attach effects for material {}", materialId, e);
        }
    }

    public static void deregisterEffect(@NonNull Player player, int materialId) {
        var self = getInstance(player);
        var effects = self.widgetEffects.getOrDefault(materialId, List.of());
        for (var e: effects) e.detach();
        self.widgetEffects.remove(materialId);
    }

    private static List<BaseWidgetEffect> getEquipEffects(@NonNull Player player, int materialId) {
        var effects = new ArrayList<BaseWidgetEffect>();
        var config = WidgetModule.getWidgetConfigMap().get(materialId);
        if (config != null && config.hasInitialAbilities()) {
            effects.add(new WidgetEffectAttachAbilities(player, materialId));
        }
        return effects;
    }
}
