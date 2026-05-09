package emu.grasscutter.game.ability.embryos;

import emu.grasscutter.GameConstants;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.config.ConfigLevelEntity;
import emu.grasscutter.data.binout.config.fields.ConfigAbilityData;
import emu.grasscutter.data.excels.WorldAreaData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.server.packet.send.PacketAbilityChangeNotify;
import emu.grasscutter.utils.Utils;
import it.unimi.dsi.fastutil.ints.IntSet;
import lombok.NonNull;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class AvatarAbilityManager extends BaseAbilityManager {
    private final Avatar avatar;

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

    public void updateSkillDepotAbilities() {
        var skillDepot = GameData.getAvatarSkillDepotDataMap().get(avatar.getSkillDepotId());
        if (skillDepot == null || skillDepot.getSkillDepotAbilityGroup() == null) return;

        var abilityGroup = GameData.getAbilityGroupMap().get(skillDepot.getSkillDepotAbilityGroup());
        if (abilityGroup != null) {
            var abilityList = abilityGroup.targetAbilities.stream().mapToInt(config -> Utils.abilityHash(config.abilityName));
            updateAbilityEmbryos(AbilityType.SKILL_DEPOT, abilityList);
        }
    }

    public void updateElementalResonanceAbilities() {
        var abilities = avatar.getPlayer().getTeamManager().getElementalResonances().stream()
            .flatMapToInt(config -> getAbilitiesByOpenConfig(config.getOpenConfig()));
        updateAbilityEmbryos(AbilityType.RESONANCE_ELEMENTAL, abilities);
    }

    public void updateMoonsignResonanceAbilities() {
        // TODO: investigate and handle adding moon sign resonance
        updateAbilityEmbryos(AbilityType.RESONANCE_MOON_SIGN, IntSet.of());
    }

    public void updateWeaponAbilities() {
        var abilities = avatar.getStats().getWeaponAffixes().stream().flatMapToInt(affix -> getAbilitiesByOpenConfig(affix.getOpenConfig()));
        updateAbilityEmbryos(AbilityType.WEAPON, abilities);
    }

    public void updateReliquaryAbilities() {
        var abilities = avatar.getStats().getReliquaryAffixes().stream().flatMapToInt(affix -> getAbilitiesByOpenConfig(affix.getOpenConfig()));
        updateAbilityEmbryos(AbilityType.RELIQUARY, abilities);
    }

    public void updateProudSkillAbilities() {
        var stream = avatar.getStats().getProudSkills().stream();
        var abilities = stream.flatMapToInt(affix -> getAbilitiesByOpenConfig(affix.getOpenConfig()));
        updateAbilityEmbryos(AbilityType.PROUD_SKILL, abilities);
    }

    public void updateTalentAbilities() {
        var abilities = avatar.getStats().getTalents().stream().flatMapToInt(affix -> getAbilitiesByOpenConfig(affix.getOpenConfig()));
        updateAbilityEmbryos(AbilityType.TALENT, abilities);
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

    private static IntStream getAbilitiesByOpenConfig(String openConfig) {
        if (openConfig == null || openConfig.isEmpty()) {
            return IntStream.of();
        }

        var entry = GameData.getOpenConfigEntries().get(openConfig);
        if (entry == null) {
            // Add config string to ability skill list anyway. why?
            return IntStream.of(Utils.abilityHash(openConfig));
        }

        if (entry.getAddAbilities() != null) {
            return Arrays.stream(entry.getAddAbilities()).mapToInt(Utils::abilityHash);
        }

        return IntStream.of();
    }
}
