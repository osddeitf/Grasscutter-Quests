package emu.grasscutter.game.ability.mixins;

import emu.grasscutter.data.common.AbilityMath;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.EntityAvatar;
import teyvat.ability.config.mixins.CostStaminaMixin;

public class MixinCostStamina extends AbilityMixinHandler<CostStaminaMixin> {
    @Override
    public Class<?> getType() {
        return CostStaminaMixin.class;
    }

    @Override
    public boolean execute(Ability ability, CostStaminaMixin mixinData, byte[] abilityData) {
        if (!(ability.getOwner() instanceof EntityAvatar avatar)) {
            logger.warn("CostStaminaMixin not implemented for other entities than EntityAvatar right now");
            return false;
        }

        // e.g. ExtraAttack_Claymore_Stamina_Cost, resolved through the ability.
        // Note: costStaminaRatio (a ratio of max stamina) is ignored, it's never set in resources (7.0)
        var cost = AbilityMath.evaluate(mixinData.costStaminaDelta, ability);
        avatar.getPlayer().getStaminaManager().handleCostStaminaMixin(cost);
        return true;
    }
}
