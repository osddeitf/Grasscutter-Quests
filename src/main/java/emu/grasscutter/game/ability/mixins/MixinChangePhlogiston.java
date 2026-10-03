package emu.grasscutter.game.ability.mixins;

import emu.grasscutter.data.common.AbilityMath;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityTeam;
import teyvat.ability.config.mixins.ChangePhlogistonMixin;

public class MixinChangePhlogiston extends AbilityMixinHandler<ChangePhlogistonMixin> {
    @Override
    public Class<?> getType() {
        return ChangePhlogistonMixin.class;
    }

    @Override
    public boolean execute(Ability ability, ChangePhlogistonMixin mixinData, byte[] abilityData) {
        // Phlogiston is the player's: the owning avatar's or team's, else the ability's (e.g. on a saurian vehicle)
        var owner = ability.getOwner();
        var player = owner instanceof EntityAvatar avatar ? avatar.getPlayer()
            : owner instanceof EntityTeam team ? team.getPlayer()
            : ability.getPlayerOwner();
        if (player == null) return false;

        // e.g. RansuRatio * 2.5, resolved through the ability
        var speed = AbilityMath.evaluate(mixinData.speed, ability);
        player.getStaminaManager().handleChangePhlogistonMixin(owner, mixinData.changeType, speed, mixinData.tickInterval);
        return true;
    }
}
