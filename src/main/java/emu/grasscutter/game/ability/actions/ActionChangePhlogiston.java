package emu.grasscutter.game.ability.actions;

import emu.grasscutter.data.common.AbilityMath;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityTeam;
import emu.grasscutter.game.entity.GameEntity;
import teyvat.ability.config.actions.ChangePhlogiston;

public class ActionChangePhlogiston extends AbilityActionHandler<ChangePhlogiston> {
    @Override
    public Class<?> getType() {
        return ChangePhlogiston.class;
    }

    @Override
    public boolean execute(Ability ability, ChangePhlogiston action, byte[] abilityData, GameEntity<?> target) {
        // Phlogiston is the player's: the targeted avatar's or team's, else the ability's (e.g. Self on an animal)
        var player = target instanceof EntityAvatar avatar ? avatar.getPlayer()
            : target instanceof EntityTeam team ? team.getPlayer()
            : ability.getPlayerOwner();
        if (player == null) return false;

        var points = AbilityMath.evaluate(action.value, ability);
        player.getStaminaManager().handleChangePhlogiston(target, action.changeType, points);
        return true;
    }
}
