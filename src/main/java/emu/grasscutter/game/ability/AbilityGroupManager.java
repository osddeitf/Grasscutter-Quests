package emu.grasscutter.game.ability;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.AbilityGroup;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamManager;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Multi-source dynamic ability-group store. Each logical contribution (one widget,
 * one region, etc.) gets its own auto-incrementing source id; the manager exposes
 * the union to consumers. Designed to be safe when the same {@link AbilityGroup}
 * registry instance is contributed by multiple overlapping sources.
 */
public class AbilityGroupManager {

    private final Player player;
    private final Map<Integer, Source> sources = new HashMap<>();
    private int nextSourceId = 1;
    private boolean dirty;

    public AbilityGroupManager(Player player) {
        this.player = player;
    }

    public int register(String name) {
        int id = nextSourceId++;
        sources.put(id, new Source(name, List.of()));
        return id;
    }

    public int register(String name, Collection<AbilityGroup> groups) {
        int id = nextSourceId++;
        var snapshot = (groups == null || groups.isEmpty()) ? List.<AbilityGroup>of() : List.copyOf(groups);
        sources.put(id, new Source(name, snapshot));
        if (!snapshot.isEmpty()) dirty = true;
        return id;
    }

    public int registerByNames(String name, Collection<String> groupNames) {
        return register(name, resolve(groupNames));
    }

    public boolean update(int id, Collection<AbilityGroup> groups) {
        var src = sources.get(id);
        if (src == null) return false;
        src.groups = (groups == null || groups.isEmpty()) ? List.of() : List.copyOf(groups);
        dirty = true;
        return true;
    }

    public boolean updateByNames(int id, Collection<String> groupNames) {
        return update(id, resolve(groupNames));
    }

    public boolean unregister(int id) {
        var src = sources.remove(id);
        if (src == null) return false;
        if (!src.groups.isEmpty()) dirty = true;
        return true;
    }

    public Stream<AbilityGroup> activeGroups() {
        return sources.values().stream().flatMap(s -> s.groups.stream());
    }

    public void commit() {
        if (!dirty) return;
        dirty = false;

        boolean loaded = player.getSceneLoadState() == Player.SceneLoadState.LOADED;

        TeamManager.updateCurrentTeamAvatars(player, avatar -> {
            avatar.getAbilities().updateDynamicAbilities();
            if (loaded) avatar.getAbilities().notifyChanges();
        });

        var team = player.getTeamManager().getEntity();
        if (team != null) {
            team.getAbilities().updateDynamicAbilities();
            if (loaded) team.getAbilities().notifyChanges();
        }
    }

    private static Collection<AbilityGroup> resolve(Collection<String> groupNames) {
        if (groupNames == null || groupNames.isEmpty()) return List.of();
        return groupNames.stream()
            .map(GameData.getAbilityGroupMap()::get)
            .filter(Objects::nonNull)
            .toList();
    }

    private static class Source {
        final String name;
        Collection<AbilityGroup> groups;

        Source(String name, Collection<AbilityGroup> groups) {
            this.name = name;
            this.groups = groups;
        }
    }
}
