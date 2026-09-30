package emu.grasscutter.game.ability.actions;

import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.GameEntity;
import teyvat.ability.config.actions.KillSelf;

public class ActionKillSelf extends AbilityActionHandler<KillSelf> {
    @Override
    public Class<?> getType() {
        return KillSelf.class;
    }

    @Override
    public boolean execute(Ability ability, KillSelf action, byte[] abilityData, GameEntity<?> target) {
        //KillSelf should not have a target field, so target it's the actual entity to be applied, TODO: Check if this is always true
        if(target == null) {
            logger.warn("Tried killing null target");
            return false;
        }

        target.getScene().killEntity(target);

        return true;
    }
}
