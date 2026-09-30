package emu.grasscutter.game.ability.actions;

import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.GameEntity;
import teyvat.ability.config.actions.Predicated;

public class ActionPredicated extends AbilityActionHandler<Predicated> {
    @Override
    public Class<?> getType() {
        return Predicated.class;
    }

    @Override
    public boolean execute(Ability ability, Predicated action, byte[] abilityData, GameEntity<?> target) {
        //This doesn't do nothing on server
        return true;
    }
}
