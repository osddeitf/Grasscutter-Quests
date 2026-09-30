package emu.grasscutter.game.ability.actions;

import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.GameEntity;
import teyvat.ability.config.actions.ChangeTag;

public class ActionChangeTag extends AbilityActionHandler<ChangeTag> {

    @Override
    public Class<?> getType() {
        return ChangeTag.class;
    }

    @Override
    public boolean execute(Ability ability, ChangeTag action, byte[] abilityData, GameEntity target) {
        target.changeTag(action.tag, action.isAdd);
        return true;
    }
}
