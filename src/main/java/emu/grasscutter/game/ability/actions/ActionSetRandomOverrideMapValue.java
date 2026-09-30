package emu.grasscutter.game.ability.actions;

import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.common.AbilityMath;
import emu.grasscutter.data.common.DynamicFloat;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.utils.Utils;
import org.anime_game_servers.multi_proto.gi.messages.ability.action.AbilityActionSetRandomOverrideMapValue;
import teyvat.ability.config.ConfigAbilityAction;
import teyvat.ability.config.actions.SetRandomOverrideMapValue;

public class ActionSetRandomOverrideMapValue extends AbilityActionHandler<SetRandomOverrideMapValue> {
    @Override
    public Class<?> getType() {
        return SetRandomOverrideMapValue.class;
    }

    @Override
    public boolean execute(Ability ability, SetRandomOverrideMapValue action, byte[] abilityData, GameEntity<?> target) {
        AbilityActionSetRandomOverrideMapValue valueProto;
        try {
            valueProto = AbilityActionSetRandomOverrideMapValue.parseBy(abilityData, ability.getPlayerOwner().getSession().getVersion());
        } catch (Exception e) {
            return false;
        }

        if(action.valueRangeMax == null || action.valueRangeMin == null) {
            logger.warn("Tried setting value with null range: {} {}", action.valueRangeMin, action.valueRangeMax);
            return true;
        }

        float value = valueProto.getRandomValue();
        float valueRangeMin = AbilityMath.evaluate(action.valueRangeMin, ability);
        float valueRangeMax = AbilityMath.evaluate(action.valueRangeMax, ability);

        if(value < valueRangeMin || value > valueRangeMax) {
            logger.warn("Tried setting value out of range: {} inside [{}, {}]", value, valueRangeMin, valueRangeMax);
            return true;
        }

        ability.getServerOverrideMap().put(Utils.abilityHash(action.overrideMapKey), value);

        return true;
    }
}
