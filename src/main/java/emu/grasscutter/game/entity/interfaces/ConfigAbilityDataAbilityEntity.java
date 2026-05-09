package emu.grasscutter.game.entity.interfaces;

import emu.grasscutter.data.binout.config.fields.ConfigAbilityData;
import emu.grasscutter.game.entity.GameEntity;
import lombok.val;

public interface ConfigAbilityDataAbilityEntity extends BaseAbilityEntity<ConfigAbilityData> {
    @Override
    default void initAbilities() {
        val abilityData = getAbilityData();
        if (abilityData == null || abilityData.isEmpty()) return;

        var abilityNames = abilityData.stream().map(ConfigAbilityData::getAbilityName).toList();
        getAbilityTargetManager().initEntityAbilities((GameEntity<?>)this, abilityNames);
    }
}
