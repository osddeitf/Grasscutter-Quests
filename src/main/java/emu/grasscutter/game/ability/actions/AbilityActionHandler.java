package emu.grasscutter.game.ability.actions;

import emu.grasscutter.Loggers;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.GameEntity;
import org.slf4j.Logger;
import teyvat.ability.config.ConfigAbilityAction;

public abstract class AbilityActionHandler<T extends ConfigAbilityAction> {
    public static final Logger logger = Loggers.getAbilitySystem();

    public abstract Class<?> getType();

    /**
     * Kept for compatibility only, the handlers not migrated yet to the source/target variant below.
     * TODO: migrate all handlers, as actions may depend on both the source and target entities, then remove this.
     */
    @Deprecated
    public abstract boolean execute(Ability ability, T action, byte[] abilityData, GameEntity<?> target);

    /**
     * @param source The entity the action is invoked on, e.g. the holder of a modifier from another entity's ability
     * @param target The action's target, the source itself when the invoke has no target
     */
    public boolean execute(Ability ability, T action, byte[] abilityData, GameEntity<?> source, GameEntity<?> target) {
        return execute(ability, action, abilityData, target);
    }

}
