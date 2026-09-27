package emu.grasscutter.game.entity;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.ability.AbilityManager;
import emu.grasscutter.game.entity.interfaces.StringAbilityEntity;
import emu.grasscutter.game.props.EntityIdType;
import emu.grasscutter.game.world.Scene;

import java.util.Collection;

public class EntityScene extends MetaGameEntity implements StringAbilityEntity {

    public EntityScene(Scene scene) {
        super(scene);
        initAbilities();
    }

    @Override
    public AbilityManager getAbilityTargetManager() {
        return getScene().getWorld().getHost().getAbilityManager();
    }

    @Override
    public Collection<String> getAbilityData() {
        //Load abilities from levelElementAbilities
        return GameData.getConfigGlobalCombat().getDefaultAbilities().getLevelElementAbilities();
    }

    @Override
    public int getEntityTypeId() {
        return 0x13;
    }

    @Override
    public int getId() {
        // observed formula
        int bitOffset = EntityIdType.getBitOffset();
        return (0x13 << bitOffset) | (1 << (bitOffset - 1)) | 1;
    }
}
