package emu.grasscutter.game.ability.actions;

import emu.grasscutter.data.common.AbilityMath;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import teyvat.ability.config.actions.ReviveStamina;

/** The reverse of DeductStamina, see ActionDeductStamina */
public class ActionReviveStamina extends AbilityActionHandler<ReviveStamina> {
    @Override
    public Class<?> getType() {
        return ReviveStamina.class;
    }

    @Override
    public boolean execute(Ability ability, ReviveStamina action, byte[] abilityData, GameEntity<?> target) {
        if (!(target instanceof EntityAvatar avatar)) {
            logger.warn("ReviveStamina not implemented for other entities than EntityAvatar right now");
            return false;
        }

        var value = AbilityMath.evaluate(action.value, ability);
        avatar.getPlayer().getStaminaManager().handleReviveStamina(value);
        return true;
    }
}
