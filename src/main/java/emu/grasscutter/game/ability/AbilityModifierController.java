package emu.grasscutter.game.ability;

import emu.grasscutter.data.common.AbilityMath;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.managers.climate.ClimateManager;
import lombok.Getter;
import lombok.val;
import teyvat.ability.config.ConfigAbilityModifier;
import teyvat.ability.config.enums.AbilityState;
import teyvat.ability.config.mixins.ResistClimateMixin;

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

        var resistClimates = modifierData.getMixins().stream()
            .filter(ResistClimateMixin.class::isInstance).map(ResistClimateMixin.class::cast).toList();
        if (!resistClimates.isEmpty()) {
            var player = ClimateManager.getAffectedPlayer(entity);
            if (player != null) {
                player.getClimateManager().addResists(this, entity, resistClimates, ability);
            } else {
                AbilityManager.getLogger().warn("ResistClimateMixin on {} isn't on an avatar nor a team, at {}", entity, ability.getName());
            }
        }

        Ability.addOnKillMixins(entity, this, modifierData.getMixins(), ability);

        val state = modifierData.getState();
        if (state != null && state != AbilityState.None) entity.addAbilityState(this, state);
    }

    public void detach(GameEntity<?> entity) {
        for (var prop: modifierData.getProperties().entrySet()) {
            entity.removeAbilityProperty(prop.getKey(), abilityPropertyIdxMap.get(prop.getKey()));
        }

        var player = ClimateManager.getAffectedPlayer(entity);
        if (player != null) player.getClimateManager().removeResists(this);

        entity.removeOnKillEvent(this);
        entity.removeAbilityState(this);
    }
}
