package emu.grasscutter.game.entity.interfaces;

import emu.grasscutter.game.entity.GameEntity;
import lombok.val;

public interface StringAbilityEntity extends BaseAbilityEntity<String>{
    @Override
    default void initAbilities() {
        val abilityData = getAbilityData();
        if (abilityData == null || abilityData.isEmpty()) return;

        getAbilityTargetManager().initEntityAbilities((GameEntity<?>) this, abilityData);
    }
}
