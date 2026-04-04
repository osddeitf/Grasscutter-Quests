package emu.grasscutter.game.world.managers;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.entity.EntityRegion;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.entity.create_config.CreateRegionEntityConfig;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.scripts.SceneScriptManager;
import emu.grasscutter.server.packet.send.PacketGroupUnloadNotify;
import emu.grasscutter.utils.KahnsSort;
import kotlin.Pair;
import lombok.Getter;
import lombok.val;
import org.anime_game_servers.gi_lua.models.SceneGroupUserData;
import org.anime_game_servers.gi_lua.models.ScriptArgs;
import org.anime_game_servers.gi_lua.models.constants.EventType;
import org.anime_game_servers.gi_lua.models.scene.block.SceneBlock;
import org.anime_game_servers.gi_lua.models.scene.block.SceneGroupInfo;
import org.anime_game_servers.gi_lua.models.scene.group.SceneGroup;
import org.anime_game_servers.gi_lua.models.scene.group.SceneInitConfig;
import org.anime_game_servers.gi_lua.utils.GroupUtils;
import org.anime_game_servers.multi_proto.gi.messages.scene.VisionType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SceneGroupManager {
    private final Scene scene;
    private final Set<SceneBlock> loadedBlocks = ConcurrentHashMap.newKeySet();
    @Getter private final Set<SceneGroup> loadedGroups = ConcurrentHashMap.newKeySet();
    private final Set<Integer> replacedGroup = ConcurrentHashMap.newKeySet();

    public SceneGroupManager(Scene scene) {
        this.scene = scene;
    }

    private SceneScriptManager getScriptManager() {
        return scene.getScriptManager();
    }

    public void tick() {
        val playerMoved = scene.getPlayers().stream()
            .filter(p -> p.getLastCheckedPosition() == null || !p.getLastCheckedPosition().equal2d(p.getPosition()))
            .peek(p -> p.setLastCheckedPosition(p.getPosition().clone())).findAny();

        if (playerMoved.isEmpty()) return;

        val visible = scene.getPlayers().stream().map(this::getPlayerActiveGroups)
            .flatMap(Collection::stream).collect(Collectors.toSet());

        this.loadedGroups.stream().map(SceneGroup::getGroupInfo).filter(group -> !visible.contains(group.getId()) && !group.isDynamicLoad())
            .forEach(group -> unloadGroup(getScriptManager().getBlocks().get(group.getBlockId()), group.getId()));

        val toLoad = visible.stream().filter(g -> this.loadedGroups.stream().noneMatch(gr -> gr.getGroupInfo().getId() == g))
            .filter(g -> !this.replacedGroup.contains(g)).map(g -> Optional.ofNullable(getScriptManager().getMeta().getGroups().get(g))
                .filter(Objects::nonNull).filter(group -> !group.getGroupInfo().isDynamicLoad()).orElse(null))
            .filter(Objects::nonNull).toList();

        onLoadGroup(toLoad);
        if (!toLoad.isEmpty()) onRegisterGroups();
    }

    private Set<Integer> getPlayerActiveGroups(Player player) {
        return IntStream.range(0, 4)
            .mapToObj(i -> getScriptManager().getGroupGrids().get(i).getNearbyGroups(i, player.getPosition()))
            .flatMap(Set::stream).collect(Collectors.toSet());
    }

    public synchronized boolean loadBlock(SceneBlock block) {
        if (this.loadedBlocks.contains(block)) return false;

        onLoadBlock(block, scene.getPlayers());
        this.loadedBlocks.add(block);
        return true;
    }

    private Set<SceneGroup> onLoadBlock(SceneBlock block, List<Player> players) {
        if (!block.isLoaded()) {
            Grasscutter.getLogger().info("Scene {} Block {} loaded.", scene.getId(), block.getId());
        }
        return getScriptManager().getLoadedGroupSetPerBlock().computeIfAbsent(block.getId(), f -> new HashSet<>());
    }

    private Pair<SceneGroupInfo, SceneGroupUserData> getReplaceableGroupsPair(SceneGroup group) {
        return new Pair<>(group.getGroupInfo(), getScriptManager().getCachedGroupInstanceById(group.getGroupInfo().getId()));
    }

    private Pair<SceneGroupInfo, SceneGroupUserData> getReplaceableGroupsPair(SceneGroupInfo groupInfo) {
        return new Pair<>(groupInfo, getScriptManager().getCachedGroupInstanceById(groupInfo.getId()));
    }

    /**
     * Load specific (dynamic loaded) group
     * don't load the group if it was replaced by other groups
     */
    public int loadDynamicGroup(int groupId) {
        return getScriptManager().getGroupInstanceById(groupId) != null || this.replacedGroup.contains(groupId) ? -1 :
            Optional.ofNullable(getScriptManager().getGroupById(groupId))
                .map(SceneGroup::getInitConfig).map(SceneInitConfig::getSuite).orElse(-1);
    }

    public boolean unregisterDynamicGroup(int groupId) {
        val group = getScriptManager().getGroupById(groupId);
        if (group == null) return false;
        val groupInfo = getReplaceableGroupsPair(group);

        val block = getScriptManager().getBlocks().get(group.getGroupInfo().getBlockId());
        unloadGroup(block, groupId);

        val groupInfoList = block.getGroupInfo().values().stream()
            .map(this::getReplaceableGroupsPair)
            .toList();

        val toRestore = Optional.ofNullable(getScriptManager().getMeta().getGroups().get(groupId))
            .map(g -> GroupUtils.getReplaceableGroups(groupInfo, groupInfoList, GameData.getGroupReplacements()))
            .stream().flatMap(List::stream)
            .filter(replacement -> this.replacedGroup.remove(replacement.getId()))
            .toList();
        if (!toRestore.isEmpty()) {
            onLoadGroupInfo(toRestore);
            Grasscutter.getLogger().info("Unregistered group: {}", groupId);
            Grasscutter.getLogger().info("Replaced groups: {}", this.replacedGroup);
        }
        return true;
    }

    /**
     * Check if a spawning group(1) will replace other groups(2). If so, unload groups(2)
     */
    public void onRegisterGroups() {
        val groupList = new HashSet<Integer>();
        val replacements = GameData.getGroupReplacements().values().stream()
            .filter(replacement -> this.loadedGroups.stream()
                .filter(g -> g.getGroupInfo().isDynamicLoad())
                .anyMatch(group -> group.getGroupInfo().getId() == replacement.getId())).toList();
        replacements.forEach(replacement -> {
            Grasscutter.getLogger().info("Graph ordering: replacement {}", replacement);
            groupList.add(replacement.getId());
            groupList.addAll(replacement.getReplaceGroups());
        });

        val nodes = replacements.stream()
            .map(replacement -> replacement.getReplaceGroups().stream()
                .map(id -> new KahnsSort.Node(replacement.getId(), id))
                .toList())
            .flatMap(List::stream)
            .collect(Collectors.toSet());

        val dynamicGroupsOrdered = KahnsSort.doSort(new KahnsSort.Graph(
            nodes.stream().toList(), groupList.stream().toList()));

        if (dynamicGroupsOrdered == null) {
            Grasscutter.getLogger().error("Graph ordering: failed to order dynamic groups");
            return;
        }
        dynamicGroupsOrdered.stream()
            .map(groupId -> this.loadedGroups.stream().filter(g -> g.getGroupInfo().getId() == groupId).findFirst())
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(targetGroup -> GroupUtils.getReplaceableGroups(getReplaceableGroupsPair(targetGroup), this.loadedGroups.stream().map(this::getReplaceableGroupsPair).toList(), GameData.getGroupReplacements()))
            .flatMap(List::stream)
            .filter(replacement -> !this.replacedGroup.contains(replacement.getId()))
            .forEach(replacement -> {
                this.replacedGroup.add(replacement.getId());
                Grasscutter.getLogger().info("Graph ordering: unloaded {}", replacement.getId());
                Grasscutter.getLogger().info("Replaced groups: {}", this.replacedGroup);
                unloadGroup(getScriptManager().getBlocks().get(replacement.getBlockId()), replacement.getId());
            });
    }

    public void loadTriggerFromGroup(SceneGroup group, String triggerName) {
        getScriptManager().registerTrigger(group.getTriggers().values().stream().filter(p -> p.getName().contains(triggerName)).toList());
        group.getRegions().values().stream().filter(q -> q.getConfigId() == Integer.parseInt(triggerName.substring(13)))
            .map(region -> new EntityRegion(scene, new CreateRegionEntityConfig(region))).forEach(getScriptManager()::registerRegion);
    }

    public void onLoadGroupInfo(List<SceneGroupInfo> groups) {
        onLoadGroup(groups.stream().map(info -> getScriptManager().getMeta().getGroup(info.getId())).toList());
    }

    /**
     * Load specific group(s), used when this
     * 1) group came into player's sight (or visible)
     * 2) group is being registered
     */
    public void onLoadGroup(List<SceneGroup> groups) {
        if (groups == null || groups.isEmpty()) return;

        groups.stream().filter(Objects::nonNull).filter(group -> !this.loadedGroups.contains(group))
            .peek(getScriptManager()::loadGroupFromScript)
            .forEach(group -> onLoadBlock(getScriptManager().getBlocks().get(group.getGroupInfo().getBlockId()), scene.getPlayers()).add(group));

        val entities = new ArrayList<GameEntity<?>>();
        val entitiesBorn = new ArrayList<GameEntity<?>>();
        groups.stream().filter(group -> !this.loadedGroups.contains(group)).filter(group -> group.getInitConfig() != null)
            .map(group -> Optional.ofNullable(getScriptManager().getCachedGroupInstanceById(group.getGroupInfo().getId()))
                .stream().peek(cachedInstance -> cachedInstance.setLuaGroup(group))
                .findFirst().orElse(getScriptManager().getGroupInstanceById(group.getGroupInfo().getId())))
            .peek(gi -> this.loadedGroups.add(gi.getLuaGroup()))
            .forEach(gi -> getScriptManager().refreshGroup(gi, 0, false, entitiesBorn));

        getScriptManager().meetEntities(entities);
        getScriptManager().addEntities(entitiesBorn);
        groups.forEach(g -> getScriptManager().callEvent(new ScriptArgs(g.getGroupInfo().getId(), EventType.EVENT_GROUP_LOAD, g.getGroupInfo().getId())));
        Grasscutter.getLogger().info("Scene {} loaded {} group(s): {}", scene.getId(), groups.size(), groups.stream().map(x -> x.getGroupInfo().getId()).toList());
    }

    /**
     * Remove specific group, used when this
     * 1) group is not within player's sight (or not visible)
     * 2) group is replaced by other groups
     * 3) group is being unregistered
     */
    private void unloadGroup(SceneBlock block, int groupId) {
        scene.removeEntities(scene.getEntities().values().stream().filter(Objects::nonNull).filter(e ->
            e.getBlockId() == block.getId() && e.getGroupId() == groupId).toList(), VisionType.VISION_REMOVE);

        SceneGroup group = getScriptManager().getMeta().getGroups().get(groupId);
        val triggers = group.getTriggers();
        if (triggers != null) {
            triggers.values().forEach(getScriptManager()::deregisterTrigger);
        }
        val regions = group.getRegions();
        if (regions != null) {
            regions.values().forEach(getScriptManager()::deregisterRegion);
        }

        Optional.ofNullable(getScriptManager().getLoadedGroupSetPerBlock().get(block.getId())).ifPresent(s -> s.remove(group));

        this.loadedGroups.remove(group);

        if (getScriptManager().getLoadedGroupSetPerBlock().get(block.getId()).isEmpty()) {
            getScriptManager().getLoadedGroupSetPerBlock().remove(block.getId());
            Grasscutter.getLogger().info("Scene {} Block {} is unloaded.", scene.getId(), block.getId());
        }

        scene.broadcastPacket(new PacketGroupUnloadNotify(List.of(groupId)));
        getScriptManager().unregisterGroup(group);
    }
}
