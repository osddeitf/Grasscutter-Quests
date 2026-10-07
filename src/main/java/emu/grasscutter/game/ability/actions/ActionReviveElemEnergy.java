package emu.grasscutter.game.ability.actions;

import emu.grasscutter.data.common.AbilityMath;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import org.anime_game_servers.multi_proto.gi.messages.general.PropChangeReason;
import teyvat.ability.config.actions.ReviveElemEnergy;

/**
 * Changes the target avatar's energy, e.g. Avatar_Attack_ReviveEnergy on normal and charged attack hits: the client
 * rolls the weapon type's chance, the server only adds the energy.
 */
public class ActionReviveElemEnergy extends AbilityActionHandler<ReviveElemEnergy> {
    @Override
    public Class<?> getType() {
        return ReviveElemEnergy.class;
    }

    @Override
    public boolean execute(Ability ability, ReviveElemEnergy action, byte[] abilityData, GameEntity<?> target) {
        if (!(target instanceof EntityAvatar avatar)) {
            logger.warn("ReviveElemEnergy target {} isn't an avatar, at {}", target, ability.getName());
            return false;
        }

        // A flat value, or a ratio of the max energy (never both in the configs), negative to drain (e.g. bosses)
        float value = AbilityMath.evaluate(action.value, ability);
        float ratio = AbilityMath.evaluate(action.ratio, ability);
        float amount = value + ratio * avatar.getAvatar().getMaxEnergy();
        if (amount == 0) return true;

        // Flat, not scaled by energy recharge, as the attack energy was before
        avatar.addEnergy(amount, PropChangeReason.PROP_CHANGE_ABILITY, true);
        return true;
    }
}
