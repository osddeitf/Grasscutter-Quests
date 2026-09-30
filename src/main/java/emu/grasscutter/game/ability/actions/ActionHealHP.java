package emu.grasscutter.game.ability.actions;

import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.common.AbilityMath;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.EntityClientGadget;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.props.FightProperty;
import teyvat.ability.config.ConfigAbilityAction;
import teyvat.ability.config.actions.HealHP;

public class ActionHealHP extends AbilityActionHandler<HealHP> {
    @Override
    public Class<?> getType() {
        return HealHP.class;
    }

    @Override
    public boolean execute(Ability ability, HealHP action, byte[] abilityData, GameEntity<?> target) {
        logger.info("Heal ability action executing 1\n");

        GameEntity owner = ability.getOwner();
        //handle client gadgets, that the effective caster is the current local avatar
        if(owner instanceof EntityClientGadget ownerGadget) {
            owner = ownerGadget.getScene().getEntityById(ownerGadget.getOwnerEntityId()); //Caster for EntityClientGadget
            logger.info("Owner {} has top owner {}: {}", ability.getOwner(), ownerGadget.getOwnerEntityId(), owner);
        }
        if(owner == null) return false;

        float amountByCasterMaxHPRatio = AbilityMath.evaluate(action.amountByCasterMaxHPRatio, ability);
        float amountByCasterAttackRatio = AbilityMath.evaluate(action.amountByCasterAttackRatio, ability);
        float amountByCasterCurrentHPRatio = AbilityMath.evaluate(action.amountByCasterCurrentHPRatio, ability);
        float amountByTargetCurrentHPRatio = AbilityMath.evaluate(action.amountByTargetCurrentHPRatio, ability);
        float amountByTargetMaxHPRatio = AbilityMath.evaluate(action.amountByTargetMaxHPRatio, ability);
        float amount = AbilityMath.evaluate(action.amount, ability);

        float amountToRegenerate = amount;
        amountToRegenerate += amountByCasterMaxHPRatio * owner.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        amountToRegenerate += amountByCasterAttackRatio * owner.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK);
        amountToRegenerate += amountByCasterCurrentHPRatio * owner.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);

        float abilityRatio = 1.0f;
        if(!action.ignoreAbilityProperty) abilityRatio += target.getFightProperty(FightProperty.FIGHT_PROP_HEAL_ADD) + target.getFightProperty(FightProperty.FIGHT_PROP_HEALED_ADD);

        amountToRegenerate += amountByTargetCurrentHPRatio * target.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
        amountToRegenerate += amountByTargetMaxHPRatio * target.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);

        logger.info("Healing {} without ratios\n", amountToRegenerate);
        target.heal(amountToRegenerate * abilityRatio * AbilityMath.evaluate(action.healRatio, ability, 1f), action.muteHealEffect);

        return true;
    }
}
