package emu.grasscutter.game.ability;

import emu.grasscutter.data.common.AbilityMath;
import emu.grasscutter.game.entity.GameEntity;
import lombok.Getter;
import teyvat.ability.config.ConfigAbilityModifier;

import java.util.HashMap;
import java.util.Map;

public class AbilityModifierController {

    @Getter private final Ability ability;
    @Getter private final ConfigAbilityModifier modifierData;
    private final Map<String, Integer> abilityPropertyIdxMap = new HashMap<>();

    public AbilityModifierController(Ability ability, ConfigAbilityModifier modifierData) {
        this.ability = ability;
        this.modifierData = modifierData;
    }

    public void attach(GameEntity<?> entity) {
        for (var prop: modifierData.getProperties().entrySet()) {
            int idx = entity.addAbilityProperty(prop.getKey(), AbilityMath.evaluate(prop.getValue(), ability));
            abilityPropertyIdxMap.put(prop.getKey(), idx);
        }
    }

    public void detach(GameEntity<?> entity) {
        for (var prop: modifierData.getProperties().entrySet()) {
            entity.removeAbilityProperty(prop.getKey(), abilityPropertyIdxMap.get(prop.getKey()));
        }
    }
}
