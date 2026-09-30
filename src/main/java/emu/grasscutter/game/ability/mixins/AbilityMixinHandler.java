package emu.grasscutter.game.ability.mixins;

import emu.grasscutter.Loggers;
import emu.grasscutter.game.ability.Ability;
import org.slf4j.Logger;
import teyvat.ability.config.ConfigAbilityMixin;

public abstract class AbilityMixinHandler<T extends ConfigAbilityMixin> {
    public static final Logger logger = Loggers.getAbilitySystem();

    public abstract Class<?> getType();

    public abstract boolean execute(Ability ability, T mixinData, byte[] abilityData);

}
