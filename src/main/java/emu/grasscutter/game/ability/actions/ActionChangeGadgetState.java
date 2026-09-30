package emu.grasscutter.game.ability.actions;

import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.GameEntity;
import teyvat.ability.config.ConfigAbilityAction;
import teyvat.ability.config.actions.ChangeGadgetState;

public class ActionChangeGadgetState extends AbilityActionHandler<ChangeGadgetState> {

    @Override
    public Class<?> getType() {
        return ChangeGadgetState.class;
    }

    @Override
    public boolean execute(Ability ability, ChangeGadgetState action, byte[] abilityData, GameEntity target) {
        if (target instanceof EntityGadget gadget) {
            gadget.updateState(action.state);
            return true;
        }
        return false;
    }
}
