package emu.grasscutter.data.excels;

import emu.grasscutter.data.GameResource;
import emu.grasscutter.data.ResourceType;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamInfo;
import emu.grasscutter.game.props.ElementType;
import lombok.Getter;
import lombok.NonNull;

import java.util.HashMap;
import java.util.Objects;

@Getter
@ResourceType(name = "TeamResonanceExcelConfigData.json")
public class TeamResonanceData extends GameResource {
    private int teamResonanceId;
    private int teamResonanceGroupId;
    private String openConfig;
    private float[] paramList;

    // elements
    private TeamResonanceCond cond;
    private int electricAvatarCount;
    private int fireAvatarCount;
    private int grassAvatarCount;
    private int iceAvatarCount;
    private int rockAvatarCount;
    private int waterAvatarCount;
    private int windAvatarCount;

    public boolean isResonanceActive(@NonNull Player player, TeamInfo team) {
        var avatars = team.getAvatars().stream().map(player.getAvatars()::getAvatarById).filter(Objects::nonNull).toList();
        if (avatars.size() != 4) return false;

        var counter = new HashMap<ElementType, Integer>();
        for (var avatar : avatars) {
            var elementType = avatar.getSkillDepot().getElementType();
            var count = counter.getOrDefault(elementType, 0);
            if (cond == TeamResonanceCond.TEAM_RESONANCE_COND_ALL_DIFFERENT && count > 0) return false;
            counter.put(elementType, 1 + count);
        }
        if (electricAvatarCount > counter.getOrDefault(ElementType.Electric, 0)) return false;
        if (fireAvatarCount > counter.getOrDefault(ElementType.Fire, 0)) return false;
        if (grassAvatarCount > counter.getOrDefault(ElementType.Grass, 0)) return false;
        if (iceAvatarCount > counter.getOrDefault(ElementType.Ice, 0)) return false;
        if (rockAvatarCount > counter.getOrDefault(ElementType.Rock, 0)) return false;
        if (windAvatarCount > counter.getOrDefault(ElementType.Wind, 0)) return false;
        if (waterAvatarCount > counter.getOrDefault(ElementType.Water, 0)) return false;
        return true;
    }

    @Override
    public int getId() {
        return teamResonanceId;
    }

    private enum TeamResonanceCond {
        TEAM_RESONANCE_COND_NONE,
        TEAM_RESONANCE_COND_ALL_DIFFERENT,
    }
}
