package emu.grasscutter.game.world.managers;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.SceneNpcBornEntry;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.scripts.SceneIndexManager;
import emu.grasscutter.server.packet.send.PacketGroupSuiteNotify;
import emu.grasscutter.server.packet.send.PacketGroupUnloadNotify;
import lombok.val;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class NpcGroupManager {
    private final Scene scene;
    private Set<SceneNpcBornEntry> npcBornEntrySet = ConcurrentHashMap.newKeySet();

    public NpcGroupManager(Scene scene) {
        this.scene = scene;
    }

    public void tick() {
        Set<SceneNpcBornEntry> npcBornEntries = ConcurrentHashMap.newKeySet();
        npcBornEntries.addAll(scene.getPlayers().stream().map(this::loadNpcForPlayer).flatMap(List::stream).toList());

        // clear the unreachable group for client
        val toUnload = this.npcBornEntrySet.stream()
            .filter(i -> !npcBornEntries.contains(i))
            .map(SceneNpcBornEntry::getGroupId)
            .toList();

        if (!toUnload.isEmpty()) {
            scene.broadcastPacket(new PacketGroupUnloadNotify(toUnload));
            Grasscutter.getLogger().debug("Unload NPC Group {}", toUnload);
        }
        // exchange the new npcBornEntry Set
        this.npcBornEntrySet = npcBornEntries;
    }

    public void loadNpcForPlayerEnter(Player player) {
        this.npcBornEntrySet.addAll(loadNpcForPlayer(player));
    }

    private List<SceneNpcBornEntry> loadNpcForPlayer(Player player) {
        val pos = player.getPosition();
        val data = GameData.getSceneNpcBornData().get(scene.getId());
        if (data == null) return List.of();

        val npcList = SceneIndexManager.queryNeighbors(data.getIndex(), pos.toDoubleArray(),
            Grasscutter.getConfig().server.game.loadEntitiesForPlayerRange);

        val sceneNpcBornEntries = npcList.stream().filter(i -> !this.npcBornEntrySet.contains(i)).toList();

        if (!sceneNpcBornEntries.isEmpty()) {
            scene.broadcastPacket(new PacketGroupSuiteNotify(sceneNpcBornEntries));
            Grasscutter.getLogger().debug("Loaded Npc Group Suite {}", sceneNpcBornEntries);
        }
        return npcList;
    }
}
