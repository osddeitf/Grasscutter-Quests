package emu.grasscutter.game.modules.widget.effect;

import emu.grasscutter.data.binout.AbilityGroup;
import emu.grasscutter.game.player.Player;
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
    private int sourceId = 0;

    public AbilityWidgetEffect(@NonNull Player player, int materialId) {
        super(materialId);
        this.player = player;
    }

    @Override
    public void attach() {
        if (sourceId != 0) return;
        var mgr = player.getAbilityGroupManager();
        sourceId = mgr.register("Widget(" + materialId + ")", abilityGroups);
        mgr.commit();
    }

    @Override
    public void detach() {
        if (sourceId == 0) return;
        var mgr = player.getAbilityGroupManager();
        mgr.unregister(sourceId);
        mgr.commit();
        sourceId = 0;
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
