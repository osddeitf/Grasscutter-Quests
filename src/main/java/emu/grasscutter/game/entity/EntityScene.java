package emu.grasscutter.game.entity;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.config.fields.ConfigAbilityData;
import emu.grasscutter.game.ability.AbilityManager;
import emu.grasscutter.game.ability.AbilityModifierController;
import emu.grasscutter.game.entity.interfaces.StringAbilityEntity;
import emu.grasscutter.game.world.Scene;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public class EntityScene extends MetaGameEntity implements StringAbilityEntity {

    public EntityScene(Scene scene) {
        super(scene);
        initAbilities();
    }

    @Override
    public AbilityManager getAbilityTargetManager() {
        return getScene().getWorld().getHost().getAbilityManager();
    }

    /**
     * The scene entity's abilities, in the order the client instances them: an ability's position is its
     * instancedAbilityId. An ability listed twice takes one slot, the first.
     */
    @Override
    public Collection<String> getAbilityData() {
        var defaults = GameData.getConfigGlobalCombat().getDefaultAbilities();
        var names = new LinkedHashSet<String>();
        addAll(names, defaults.getLevelElementAbilities());
        addAll(names, defaults.getLevelDefaultAbilities());
        addAll(names, defaults.getLevelItemAbilities());
        addAll(names, defaults.getLevelSBuffAbilities());
        if (getScene().getDungeonManager() != null) {
            addAll(names, defaults.getDungeonAbilities());
        }

        for (var levelEntityName : getLevelEntityConfigNames()) {
            var config = GameData.getConfigLevelEntityDataMap().get(levelEntityName);
            if (config == null || config.getAbilities() == null) continue;
            addAll(names, config.getAbilities().stream().map(ConfigAbilityData::getAbilityName).toList());
        }
        return names;
    }

    /**
     * The level entity configs that apply: the scene's, then the dungeon's layered on top.
     * The order is unverified, no observed scene and dungeon both contribute abilities yet.
     */
    private List<String> getLevelEntityConfigNames() {
        var names = new ArrayList<String>(2);
        var sceneConfig = getScene().getSceneData().getLevelEntityConfig();
        if (sceneConfig != null && !sceneConfig.isEmpty()) names.add(sceneConfig);

        var dungeonManager = getScene().getDungeonManager();
        var dungeonConfig = dungeonManager == null ? null : dungeonManager.getLevelEntityConfigName();
        if (dungeonConfig != null && !dungeonConfig.isEmpty() && !names.contains(dungeonConfig)) names.add(dungeonConfig);
        return names;
    }

    private static void addAll(Collection<String> names, List<String> abilities) {
        if (abilities == null) return;
        abilities.stream().filter(Objects::nonNull).forEach(names::add);
    }

    /** Re-instances the abilities, when what they depend on changed (e.g. a dungeon attached to the scene) */
    public void reinitAbilities() {
        getInstancedModifiers().values().forEach(modifier -> ((AbilityModifierController) modifier).detach(this));
        getInstancedModifiers().clear();
        getInstancedAbilities().clear();
        initAbilities();
    }

    @Override
    public int getEntityTypeId() {
        return 0x13;
    }
}
