package emu.grasscutter.game.modules.widget.effect;

import emu.grasscutter.data.binout.AbilityGroup;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamManager;
import emu.grasscutter.utils.Utils;
import lombok.NonNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AbilityWidgetEffect extends BaseWidgetEffect {

    protected final Player player;
    protected final Set<AbilityGroup> abilityGroups = new HashSet<>();

    public AbilityWidgetEffect(@NonNull Player player, int materialId) {
        super(materialId);
        this.player = player;
    }

    @Override
    public void attach() {
        player.getAbilityManager().getDynamicAbilityGroups().addAll(abilityGroups);
        notifyAbilityChanges();
    }

    @Override
    public void detach() {
        player.getAbilityManager().getDynamicAbilityGroups().removeAll(abilityGroups);
        notifyAbilityChanges();
    }

    private void notifyAbilityChanges() {
        var notifyTeam = abilityGroups.stream().anyMatch(group -> group.targetType == AbilityGroup.AbilityGroupTarget.ABILITY_GROUP_TARGET_TEAM);
        var notifyAvatars = abilityGroups.stream().anyMatch(group -> group.targetType == AbilityGroup.AbilityGroupTarget.ABILITY_GROUP_TARGET_AVATAR);

        if (notifyAvatars) {
            TeamManager.updateCurrentTeamAvatars(player, avatar -> {
                avatar.getAbilities().updateDynamicAbilities();
                if (player.getSceneLoadState() != Player.SceneLoadState.LOADED) return;
                avatar.getAbilities().notifyChanges();
            });
        }

        if (notifyTeam) {
            var team = player.getTeamManager().getEntity();
            if (team != null) {
                team.getAbilities().updateDynamicAbilities();
                if (player.getSceneLoadState() != Player.SceneLoadState.LOADED) return;
                team.getAbilities().notifyChanges();
            }
        }
    }

    protected void compat_addAbilityGroup(AbilityGroup.AbilityGroupTarget target, Collection<String> names, Collection<Integer> hashes) {
        var stream = Stream.concat(
            Optional.ofNullable(hashes).stream().flatMap(Collection::stream),
            Optional.ofNullable(names).stream().flatMap(Collection::stream).map(Utils::abilityHash)
        );
        var abilities = stream.collect(Collectors.toSet());
        if (!abilities.isEmpty()) {
            var group = AbilityGroup.of(target, abilities);
            abilityGroups.add(group);
        }
    }
}
