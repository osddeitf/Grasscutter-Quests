package emu.grasscutter.data.excels;

import emu.grasscutter.data.GameResource;
import emu.grasscutter.data.ResourceType;
import emu.grasscutter.data.binout.OpenConfigSource;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.props.ElementType;
import lombok.Getter;
import lombok.NonNull;

import java.util.HashMap;
import java.util.List;

@Getter
@ResourceType(name = "TeamResonanceExcelConfigData.json")
public class TeamResonanceData extends GameResource implements OpenConfigSource {
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

    /**
     * @param teamAvatars avatars of the team in team order, trial avatars included
     */
    public boolean isResonanceActive(@NonNull List<Avatar> teamAvatars) {
        // Only the first 4 avatars of the team are taken into account, and resonance needs a full party
        if (teamAvatars.size() < 4) return false;

        var counter = new HashMap<ElementType, Integer>();
        for (var avatar : teamAvatars.subList(0, 4)) {
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
