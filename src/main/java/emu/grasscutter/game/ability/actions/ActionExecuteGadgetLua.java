package emu.grasscutter.game.ability.actions;

import emu.grasscutter.data.common.AbilityMath;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.GameEntity;
import teyvat.ability.config.actions.ExecuteGadgetLua;

public class ActionExecuteGadgetLua extends AbilityActionHandler<ExecuteGadgetLua> {
    @Override
    public Class<?> getType() {
        return ExecuteGadgetLua.class;
    }

    @Override
    public boolean execute(Ability ability, ExecuteGadgetLua action, byte[]  abilityData, GameEntity<?> target) {
        GameEntity owner = ability.getOwner();

        if(owner.getEntityController() != null) {
            owner.getEntityController().onClientExecuteRequest(owner,
                (int) AbilityMath.evaluate(action.param1, ability),
                (int) AbilityMath.evaluate(action.param2, ability),
                (int) AbilityMath.evaluate(action.param3, ability)
            );
            return true;
        }

        if (target.getEntityController() != null) {
            target.getEntityController().onClientExecuteRequest(target,
                (int) AbilityMath.evaluate(action.param1, ability),
                (int) AbilityMath.evaluate(action.param2, ability),
                (int) AbilityMath.evaluate(action.param3, ability)
            );
            return true;
        }

        return false;
    }
}
