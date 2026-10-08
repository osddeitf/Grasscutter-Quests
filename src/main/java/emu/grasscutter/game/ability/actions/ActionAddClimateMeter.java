package emu.grasscutter.game.ability.actions;

import emu.grasscutter.data.common.AbilityMath;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.managers.climate.ClimateManager;
import teyvat.ability.config.actions.AddClimateMeter;

/** Adds to the target player's climate meter, in units, e.g. Dulin's Blood lowering Sheer Cold */
public class ActionAddClimateMeter extends AbilityActionHandler<AddClimateMeter> {
    @Override
    public Class<?> getType() {
        return AddClimateMeter.class;
    }

    @Override
    public boolean execute(Ability ability, AddClimateMeter action, byte[] abilityData, GameEntity<?> target) {
        var player = target != null ? ClimateManager.getAffectedPlayer(target) : null;
        if (player == null) {
            logger.warn("AddClimateMeter target {} isn't an avatar nor a team, at {}", target, ability.getName());
            return false;
        }

        player.getClimateManager().addMeter(action.climateType, AbilityMath.evaluate(action.value, ability));
        return true;
    }
}
