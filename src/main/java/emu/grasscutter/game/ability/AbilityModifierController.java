package emu.grasscutter.game.ability;

import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityModifier;
import emu.grasscutter.game.entity.GameEntity;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

public class AbilityModifierController {

    @Getter private final Ability ability;
    @Getter private final AbilityData abilityData;
    @Getter private final AbilityModifier modifierData;
    private final Map<String, Integer> abilityPropertyIdxMap = new HashMap<>();

    public AbilityModifierController(Ability ability, AbilityData abilityData, AbilityModifier modifierData) {
        this.ability = ability;
        this.abilityData = abilityData;
        this.modifierData = modifierData;
    }

    public void attach(GameEntity<?> entity) {
        if (modifierData.properties != null) {
            for (var prop: modifierData.properties.entrySet()) {
                int idx = entity.addAbilityProperty(prop.getKey(), prop.getValue().get(ability));
                abilityPropertyIdxMap.put(prop.getKey(), idx);
            }
        }
    }

    public void detach(GameEntity<?> entity) {
        if (modifierData.properties != null) {
            for (var prop: modifierData.properties.entrySet()) {
                entity.removeAbilityProperty(prop.getKey(), abilityPropertyIdxMap.get(prop.getKey()));
            }
        }
    }
}
