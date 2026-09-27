package emu.grasscutter.game.ability.embryos;

import emu.grasscutter.GameConstants;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.OpenConfigData;
import emu.grasscutter.data.binout.OpenConfigSource;
import emu.grasscutter.data.binout.config.ConfigLevelEntity;
import emu.grasscutter.data.binout.config.fields.ConfigAbilityData;
import emu.grasscutter.data.excels.WorldAreaData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.server.packet.send.PacketAbilityChangeNotify;
import emu.grasscutter.utils.Utils;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import lombok.NonNull;

import java.util.*;
import java.util.stream.IntStream;

public class AvatarAbilityManager extends BaseAbilityManager {
    public static final String SOURCE_WEAPON      = "weapon";
    public static final String SOURCE_RELIQUARY   = "reliquary";
    public static final String SOURCE_PROUD_SKILL = "proud_skill";
    public static final String SOURCE_TALENT      = "talent";
    public static final String SOURCE_RESONANCE   = "resonance";

    private final Avatar avatar;
    private final Map<String, Set<OpenConfigSource>> openConfigSources = new LinkedHashMap<>();
    private final IntSet openConfigAbilities = new IntLinkedOpenHashSet();

    public AvatarAbilityManager(@NonNull Avatar avatar) {
        this.avatar = avatar;
        initAbilityEmbryos();
    }

    public void notifyChanges() {
        if (!abilityChanged) return;
        abilityChanged = false;
        var entity = avatar.getAsEntity();
        if (entity != null) {
            avatar.getPlayer().sendPacket(new PacketAbilityChangeNotify(entity));
        }
    }

    public void attachOpenConfigs(String source, Set<? extends OpenConfigSource> sources) {
        Set<OpenConfigSource> stored = Set.copyOf(sources);
        var prev = openConfigSources.put(source, stored);
        var prevSet = prev == null? Set.<OpenConfigSource>of(): prev;

        var removed = prevSet.stream().filter(s -> !stored.contains(s)).toList();
        var added = stored.stream().filter(s -> !prevSet.contains(s)).toList();
        if (removed.isEmpty() && added.isEmpty()) return;

        onOpenConfigsDetached(removed);
        onOpenConfigsAttached(added);

        // Snapshot so BaseAbilityManager's prev-vs-new diff has a stable reference.
        updateAbilityEmbryos(AbilityType.OPEN_CONFIG, new IntLinkedOpenHashSet(openConfigAbilities));
    }

    private void onOpenConfigsDetached(List<OpenConfigSource> removed) {
        for (var src : removed) {
            var abilities = getAddAbilityNames(src.getOpenConfig());
            openConfigAbilities.removeAll(abilities);

            var entry = GameData.getOpenConfigEntries().get(src.getOpenConfig());
            if (entry == null) continue;
            for (var data : entry.getConfigTalents()) {
                switch (data.type) {
                    case ModifyAbility -> {
//                        avatar.getAsEntity().removeAbilityProperty();
//                        data.paramDelta.get(src, 0);
//                        data.paramRatio.get(src, 0);
                    }
                    // TODO: ModifySkillPoint, AddTalentExtraLevel, UnlockTalentParam
                }
            }
        }
    }

    private void onOpenConfigsAttached(List<OpenConfigSource> added) {
        for (var src : added) {
            var abilities = getAddAbilityNames(src.getOpenConfig());
            openConfigAbilities.addAll(abilities);

            var entry = GameData.getOpenConfigEntries().get(src.getOpenConfig());
            if (entry == null) continue;
            for (var data : entry.getConfigTalents()) {
                switch (data.type) {
                    case ModifyAbility -> {}
                    // TODO: ModifySkillPoint, AddTalentExtraLevel, UnlockTalentParam
                }
            }
        }
    }

    private IntSet getAddAbilityNames(String openConfig) {
        if (openConfig.isEmpty()) return IntSet.of();

        var entry = GameData.getOpenConfigEntries().get(openConfig);
        if (entry == null) {
            // Missing resource -> fallback to the name itself, as the ability may share the same name.
            return IntSet.of(Utils.abilityHash(openConfig));
        }

        var stream = entry.getConfigTalents().stream()
            .filter(config -> config.type == OpenConfigData.Type.AddAbility)
            .map(config -> config.abilityName)
            .mapToInt(Utils::abilityHash);
        return IntLinkedOpenHashSet.toSet(stream);
    }

    public void updateSkillDepotAbilities() {
        var skillDepot = GameData.getAvatarSkillDepotDataMap().get(avatar.getSkillDepotId());
        if (skillDepot == null || skillDepot.getSkillDepotAbilityGroup() == null) return;

        var abilityGroup = GameData.getAbilityGroupMap().get(skillDepot.getSkillDepotAbilityGroup());
        if (abilityGroup != null) {
            var abilityList = abilityGroup.targetAbilities.stream().mapToInt(config -> Utils.abilityHash(config.abilityName));
            updateAbilityEmbryos(AbilityType.SKILL_DEPOT, abilityList);
        }
    }

    public void updateMoonsignResonanceAbilities() {
        // TODO: investigate and handle adding moon sign resonance
        updateAbilityEmbryos(AbilityType.RESONANCE_MOON_SIGN, IntSet.of());
    }

    public void updateWorldAreaAbilities(@NonNull WorldAreaData area) {
        var type = area.getAreaID2() == 0
            ? AbilityType.WORLD_AREA_LEVEL_1
            : AbilityType.WORLD_AREA_LEVEL_2;
        List<String> groupList = Objects.requireNonNullElse(area.getAvatarAbilityGroupList(), List.of());
        updateAbilityEmbryos(type, groupList.stream()
            .map(GameData.getAbilityGroupMap()::get)
            .filter(Objects::nonNull)
            .flatMap(group -> group.getAvatarAbilities().stream())
            .mapToInt(Integer::intValue)
        );
    }

    public void updateLevelEntityAbilities(@NonNull ConfigLevelEntity config) {
        List<ConfigAbilityData> abilities = Objects.requireNonNullElse(config.getAvatarAbilities(), List.of());
        updateAbilityEmbryos(AbilityType.LEVEL_ENTITY, abilities.stream()
            .map(ConfigAbilityData::getAbilityName)
            .mapToInt(Utils::abilityHash)
        );
    }

    public void updateGalleryAbilities() {
        // TODO: handle it
        updateAbilityEmbryos(AbilityType.GALLERY, IntSet.of());
    }

    public void updateScriptLuaAbilities() {
        // TODO: handle it
        updateAbilityEmbryos(AbilityType.SCRIPT_LUA, IntSet.of());
    }

    public void updateDynamicAbilities() {
        updateAbilityEmbryos(AbilityType.DYNAMIC, avatar.getPlayer().getAbilityGroupManager().activeGroups()
            .flatMap(group -> group.getAvatarAbilities().stream())
            .mapToInt(Integer::intValue)
        );
    }

    private void initAbilityEmbryos() {
        var defaults = Arrays.stream(GameConstants.DEFAULT_ABILITY_HASHES);
        var info = avatar.getAvatarData().getAbilityEmbryoInfo();
        if (info != null) {
            defaults = IntStream.concat(defaults, Arrays.stream(info.getAbilities()).mapToInt(Utils::abilityHash));
        }
        updateAbilityEmbryos(AbilityType.DEFAULT, defaults);
    }

}
