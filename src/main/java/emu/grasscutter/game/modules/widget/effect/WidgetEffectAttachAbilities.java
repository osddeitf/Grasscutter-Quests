package emu.grasscutter.game.modules.widget.effect;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.AbilityGroup;
import emu.grasscutter.game.modules.widget.WidgetModule;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.utils.Utils;
import lombok.NonNull;

import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Optional;

public class WidgetEffectAttachAbilities extends AbilityWidgetEffect {
    public WidgetEffectAttachAbilities(@NonNull Player player, int materialId) {
        super(player, materialId);

        var config = WidgetModule.getWidgetConfigMap().get(materialId);
        var abilityGroupNames = new HashSet<String>();
        Optional.ofNullable(config.getAbilityGroupName()).ifPresent(abilityGroupNames::add);

        for (var name: abilityGroupNames) {
            if (Utils.isNullOrEmpty(name)) continue;
            var group = GameData.getAbilityGroupMap().get(name);
            if (group == null) throw new NoSuchElementException("missing ability group: " + name);
            this.abilityGroups.add(group);
        }

        compat_addAbilityGroup(AbilityGroup.AbilityGroupTarget.ABILITY_GROUP_TARGET_AVATAR, config.getAvatarAbilities(), config.getAvatarHashAbilities());
        compat_addAbilityGroup(AbilityGroup.AbilityGroupTarget.ABILITY_GROUP_TARGET_TEAM, config.getTeamAbilities(), config.getTeamHashAbilities());
    }
}
