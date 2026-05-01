package emu.grasscutter.game.ability.embryos;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.WorldAreaData;
import emu.grasscutter.game.entity.EntityTeam;
import emu.grasscutter.server.packet.send.PacketAbilityChangeNotify;
import emu.grasscutter.utils.Utils;
import lombok.NonNull;

import java.util.List;
import java.util.Objects;

public class TeamAbilityManager extends BaseAbilityManager {
    private final EntityTeam team;

    public TeamAbilityManager(EntityTeam team) {
        this.team = team;
        initAbilityEmbryos();
    }

    public void notifyChanges() {
        if (!abilityChanged) return;
        abilityChanged = false;
        team.getPlayer().sendPacket(new PacketAbilityChangeNotify(team));
    }

    public void updateWorldAreaAbilities(@NonNull WorldAreaData area) {
        var type = area.getAreaID2() == 0
            ? AbilityType.WORLD_AREA_LEVEL_1
            : AbilityType.WORLD_AREA_LEVEL_2;
        List<String> groupList = Objects.requireNonNullElse(area.getTeamAbilityGroupList(), List.of());
        updateAbilityEmbryos(type, groupList.stream()
            .map(GameData.getAbilityGroupMap()::get)
            .filter(Objects::nonNull)
            .flatMap(group -> group.getTeamAbilities().stream())
            .mapToInt(Integer::intValue)
        );
    }

    public void updateDynamicAbilities() {
        updateAbilityEmbryos(AbilityType.DYNAMIC, team.getPlayer().getAbilityManager().getDynamicAbilityGroups().stream()
            .flatMap(group -> group.getTeamAbilities().stream())
            .mapToInt(Integer::intValue)
        );
    }

    private void initAbilityEmbryos() {
        var defaults = GameData.getConfigGlobalCombat().getDefaultAbilities().getDefaultTeamAbilities();
        if (defaults != null) {
            updateAbilityEmbryos(AbilityType.DEFAULT, defaults.stream().mapToInt(Utils::abilityHash));
        }
    }
}
