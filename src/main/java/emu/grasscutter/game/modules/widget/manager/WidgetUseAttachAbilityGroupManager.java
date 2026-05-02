package emu.grasscutter.game.modules.widget.manager;

import emu.grasscutter.game.modules.widget.WidgetModule;
import emu.grasscutter.game.modules.widget.effect.BaseWidgetEffect;
import emu.grasscutter.game.modules.widget.effect.WidgetEffectToggleAbility;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BaseTypedPacket;
import emu.grasscutter.utils.Utils;
import lombok.NonNull;
import org.anime_game_servers.multi_proto.gi.messages.item.widget.WidgetUseAttachAbilityGroupChangeNotify;

import java.util.ArrayList;
import java.util.List;

public class WidgetUseAttachAbilityGroupManager {

    private List<BaseWidgetEffect> effects;

    public static WidgetUseAttachAbilityGroupManager getInstance(@NonNull Player player) {
        return player.getWidgetModule().getAttachAbilityGroupManager();
    }

    /**
     * @param player player owner
     * @param materialId material of the widget
     * @return true if handled, else the corresponding widget is unhandled
     */
    public static boolean toggleEffect(@NonNull Player player, int materialId) {
        var self = getInstance(player);
        var isAttach = self.effects == null;
        // detach previous widget (if any)
        self.removeEffect();
        if (isAttach) {
            self.effects = getTriggerEffects(player, materialId);

            // material unhandled
            if (self.effects == null) return false;

            self.effects.forEach(BaseWidgetEffect::attach);
        }

        var proto = new WidgetUseAttachAbilityGroupChangeNotify();
        proto.setAttach(isAttach);
        proto.setMaterialId(materialId);
        player.sendPacket(new BaseTypedPacket<>(proto) {});
        return true;
    }

    public static void deactivateEffect(@NonNull Player player, int materialId) {
        var self = getInstance(player);
        if (!self.removeEffect()) return;

        var proto = new WidgetUseAttachAbilityGroupChangeNotify();
        proto.setAttach(false);
        proto.setMaterialId(materialId);
        player.sendPacket(new BaseTypedPacket<>(proto) {});
    }

    private static List<BaseWidgetEffect> getTriggerEffects(@NonNull Player player, int materialId) {
        var effects = new ArrayList<BaseWidgetEffect>();
        var config = WidgetModule.getWidgetConfigMap().get(materialId);
        if (config == null) return null;

        if (!Utils.isNullOrEmpty(config.getAbilityGroupNameSecond())) {
            effects.add(new WidgetEffectToggleAbility(player, materialId));
        }
        return effects.isEmpty() ? null : effects;
    }

    private boolean removeEffect() {
        if (effects == null) return false;
        effects.forEach(BaseWidgetEffect::detach);
        effects = null;
        return true;
    }

}
