package emu.grasscutter.data.binout;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.utils.Utils;

import java.util.Collection;
import java.util.List;

public class AbilityGroup {
    @SerializedName(value="abilityGroupSourceType")
    public AbilityGroupSource sourceType;

    @SerializedName(value="abilityGroupTargetType")
    public AbilityGroupTarget targetType;

    @SerializedName(value="abilities", alternate={"targetAbilities"})
    public List<AvatarConfigAbility> targetAbilities;

    // for custom ability groups (useful when group or ability names are unknown)
    private List<Integer> hashedAbilities;

    public enum AbilityGroupSource {
        ABILITY_GROUP_SOURCE_NONE,
        ABILITY_GROUP_SOURCE_CHALLENGE,
        ABILITY_GROUP_SOURCE_QUEST,
        ABILITY_GROUP_SOURCE_ACTIVITY,
        ABILITY_GROUP_SOURCE_HUNTING,
        ABILITY_GROUP_SOURCE_WATCHER,
        ABILITY_GROUP_SOURCE_AVATAR_SKILL_DEPOT,
        ABILITY_GROUP_SOURCE_GALLERY,
        ABILITY_GROUP_SOURCE_WIDGET,
        ABILITY_GROUP_SOURCE_FISHING,
        ABILITY_GROUP_SOURCE_LEVEL_BANK,
        ABILITY_GROUP_SOURCE_WORLD_AREA,
        ABILITY_GROUP_SOURCE_TOWER_HALF_LEVEL,
    }

    public enum AbilityGroupTarget {
        ABILITY_GROUP_TARGET_NONE,
        ABILITY_GROUP_TARGET_AVATAR,
        ABILITY_GROUP_TARGET_TEAM,
    }

    public static class AvatarConfigAbility {
        public String abilityName;
        public String toString() {
            return abilityName;
        }
    }

    /** In config order, the order abilities are added in */
    public List<Integer> getAvatarAbilities() {
        if (targetType != AbilityGroupTarget.ABILITY_GROUP_TARGET_AVATAR) return List.of();
        return getAbilities();
    }

    /** In config order, the order abilities are added in */
    public List<Integer> getTeamAbilities() {
        if (targetType != AbilityGroupTarget.ABILITY_GROUP_TARGET_TEAM) return List.of();
        return getAbilities();
    }

    private List<Integer> getAbilities() {
        if (hashedAbilities != null) return hashedAbilities;
        return targetAbilities.stream().map(Object::toString).map(Utils::abilityHash).distinct().toList();
    }

    public static AbilityGroup of(AbilityGroupTarget target, Collection<Integer> abilityHashes) {
        var group = new AbilityGroup();
        group.targetType = target;
        group.sourceType = AbilityGroupSource.ABILITY_GROUP_SOURCE_NONE;
        group.hashedAbilities = abilityHashes.stream().distinct().toList();
        return group;
    }
}
