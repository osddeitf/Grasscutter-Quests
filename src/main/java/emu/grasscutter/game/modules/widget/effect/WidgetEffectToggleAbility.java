package emu.grasscutter.game.modules.widget.effect;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.modules.widget.WidgetModule;
import emu.grasscutter.game.player.Player;
import lombok.NonNull;

import java.util.NoSuchElementException;

public class WidgetEffectToggleAbility extends AbilityWidgetEffect {
    public WidgetEffectToggleAbility(@NonNull Player player, int materialId) {
        super(player, materialId);
        var config = WidgetModule.getWidgetConfigMap().get(materialId);
        var groupName = config.getAbilityGroupNameSecond();
        var group = GameData.getAbilityGroupMap().get(groupName);
        if (group == null) throw new NoSuchElementException("missing ability group: " + groupName);
        this.abilityGroups.add(group);
    }
}
