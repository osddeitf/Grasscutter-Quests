package emu.grasscutter.game.ability.actions;

import emu.grasscutter.Loggers;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.GameEntity;
import org.slf4j.Logger;
import teyvat.ability.config.ConfigAbilityAction;

public abstract class AbilityActionHandler<T extends ConfigAbilityAction> {
    public static final Logger logger = Loggers.getAbilitySystem();

    public abstract Class<?> getType();

    public abstract boolean execute(Ability ability, T action, byte[] abilityData, GameEntity<?> target);

}
