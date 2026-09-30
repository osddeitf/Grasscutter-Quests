package emu.grasscutter.game.ability.actions;

import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.common.AbilityMath;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.EntityClientGadget;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.props.FightProperty;
import teyvat.ability.config.ConfigAbilityAction;
import teyvat.ability.config.actions.LoseHP;

public class ActionLoseHP extends AbilityActionHandler<LoseHP> {
    @Override
    public Class<?> getType() {
        return LoseHP.class;
    }

    @Override
    public boolean execute(Ability ability, LoseHP action, byte[] abilityData, GameEntity<?> target) {
        GameEntity owner = ability.getOwner();
        //handle client gadgets, that the effective caster is the current local avatar
        if(owner instanceof EntityClientGadget ownerGadget) {
            owner = ownerGadget.getScene().getEntityById(ownerGadget.getOwnerEntityId()); //Caster for EntityClientGadget

            //TODO: Do this per entity, not just the player
            if(ownerGadget.getOwner().getAbilityManager().isAbilityInvulnerable()) return true;
        }
        if(owner == null || target == null) return false;

        if(action.enableLockHP && target.isLockHP()) {
            return true;
        }

        if(action.disableWhenLoading && target.getScene().getWorld().getHost().getSceneLoadState().getValue() < 2) {
            return true;
        }

        float amountByCasterMaxHPRatio = AbilityMath.evaluate(action.amountByCasterMaxHPRatio, ability);
        float amountByCasterAttackRatio = AbilityMath.evaluate(action.amountByCasterAttackRatio, ability);
        float amountByCasterCurrentHPRatio = AbilityMath.evaluate(action.amountByCasterCurrentHPRatio, ability); //Seems unused on server
        float amountByTargetCurrentHPRatio = AbilityMath.evaluate(action.amountByTargetCurrentHPRatio, ability);
        float amountByTargetMaxHPRatio = AbilityMath.evaluate(action.amountByTargetMaxHPRatio, ability);
        float limboByTargetMaxHPRatio = AbilityMath.evaluate(action.limboByTargetMaxHPRatio, ability);
        float amount = AbilityMath.evaluate(action.amount, ability);

        float amountToLose = amount;
        amountToLose += amountByCasterMaxHPRatio * owner.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        amountToLose += amountByCasterAttackRatio * owner.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK);
        //Ignore amountByCasterCurrentHPRatio if owner has Infinite HP
        if(owner.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP) != Float.POSITIVE_INFINITY) {
            amountToLose += amountByCasterCurrentHPRatio * owner.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
        }

        float currentHp = target.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
        float maxHp = target.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        amountToLose += amountByTargetCurrentHPRatio * currentHp;
        amountToLose += amountByTargetMaxHPRatio * maxHp;

        if(limboByTargetMaxHPRatio > 1.192093e-07)
            amountToLose = (float)Math.min(Math.max(currentHp -  Math.max(limboByTargetMaxHPRatio * maxHp, 1.0), 0.0), amountToLose);

        if(currentHp < (amountToLose + 0.01) && !action.lethal)
            amountToLose = 0;

        target.damage(amountToLose);

        return true;
    }
}
