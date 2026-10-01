package emu.grasscutter.game.ability.actions;

import emu.grasscutter.data.common.AbilityMath;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import teyvat.ability.config.actions.DeductStamina;

public class ActionDeductStamina extends AbilityActionHandler<DeductStamina> {
    @Override
    public Class<?> getType() {
        return DeductStamina.class;
    }

    @Override
    public boolean execute(Ability ability, DeductStamina action, byte[] abilityData, GameEntity<?> target) {
        // The action's target: the avatar itself (e.g. a special jump), or the avatar a monster hit
        if (!(target instanceof EntityAvatar avatar)) {
            logger.warn("DeductStamina not implemented for other entities than EntityAvatar right now");
            return false;
        }

        var cost = AbilityMath.evaluate(action.value, ability);
        avatar.getPlayer().getStaminaManager().handleDeductStamina(cost);
        return true;
    }
}
