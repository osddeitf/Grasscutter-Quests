package emu.grasscutter.game.world.managers;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.GameDepot;
import emu.grasscutter.data.excels.WorldLevelData;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.entity.create_config.CreateGadgetEntityConfig;
import emu.grasscutter.game.entity.create_config.CreateMonsterEntityConfig;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.SpawnDataEntry;
import emu.grasscutter.server.packet.send.PacketSceneEntityAppearNotify;
import emu.grasscutter.server.packet.send.PacketSceneEntityDisappearNotify;
import lombok.Getter;
import lombok.val;
import org.anime_game_servers.multi_proto.gi.messages.scene.VisionType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SpawnManager {
    private final Scene scene;
    private final Set<SpawnDataEntry> spawnedEntities = ConcurrentHashMap.newKeySet();
    @Getter private final Set<SpawnDataEntry> deadSpawnedEntities = ConcurrentHashMap.newKeySet();
    private Set<SpawnDataEntry.GridBlockId> loadedGridBlocks = new HashSet<>();

    public SpawnManager(Scene scene) {
        this.scene = scene;
    }

    public void tick() {
        val loadedGridBlocks = scene.getPlayers().stream()
            .map(p -> SpawnDataEntry.GridBlockId.getAdjacentGridBlockIds(p.getSceneId(), p.getPosition()))
            .flatMap(Arrays::stream).collect(Collectors.toSet());

        // Don't recalculate static spawns if nothing has changed
        if (this.loadedGridBlocks.containsAll(loadedGridBlocks)) return;

        this.loadedGridBlocks = loadedGridBlocks;
        val visible = loadedGridBlocks.stream().map(GameDepot.getSpawnLists()::get)
            .filter(Objects::nonNull).flatMap(List::stream).collect(Collectors.toSet());

        // World level
        final int worldLevelOverride = Optional.ofNullable(GameData.getWorldLevelDataMap().get(scene.getWorld().getWorldLevel()))
            .map(WorldLevelData::getMonsterLevel).orElse(0);

        List<GameEntity> toAdd = new ArrayList<>();
        List<GameEntity> toRemove = new ArrayList<>();
        for (SpawnDataEntry entry : visible) {
            // If spawn entry is in our view and hasn't been spawned/killed yet, we should spawn it
            if (spawnedEntities.contains(entry) || this.deadSpawnedEntities.contains(entry)) continue;
            GameEntity<?> entity = null;

            if (entry.getMonsterId() > 0) {
                final int level = getEntityLevel(entry.getLevel(), worldLevelOverride);
                val config = new CreateMonsterEntityConfig(entry)
                    .setLevel(level);
                val data = config.getMonsterData();
                if (data == null) continue;

                entity = new EntityMonster(scene, config);
            } else if (entry.getGadgetId() > 0) {
                val createConfig = new CreateGadgetEntityConfig(entry);
                val gadget = new EntityGadget(scene, createConfig);

                gadget.setFightProperty(FightProperty.FIGHT_PROP_BASE_HP, Float.POSITIVE_INFINITY);
                gadget.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, Float.POSITIVE_INFINITY);
                gadget.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, Float.POSITIVE_INFINITY);

                entity = gadget;
            }

            if (entity == null) continue;

            toAdd.add(entity);
            spawnedEntities.add(entry);
        }

        scene.getEntities().values().stream().filter(entity -> entity.getSpawnEntry() != null)
            .filter(entity -> !(entity instanceof EntityWeapon))
            .filter(entity -> !visible.contains(entity.getSpawnEntry()))
            .peek(toRemove::add).map(GameEntity::getSpawnEntry).forEach(spawnedEntities::remove);

        if (!toAdd.isEmpty()) {
            toAdd.forEach(scene::addEntityDirectly);
            scene.broadcastPacket(new PacketSceneEntityAppearNotify(toAdd, VisionType.VISION_BORN));
            toAdd.forEach(x -> x.afterCreate(scene.getPlayers()));
        }
        if (!toRemove.isEmpty()) {
            toRemove.forEach(scene::removeEntityDirectly);
            scene.broadcastPacket(new PacketSceneEntityDisappearNotify(toRemove, VisionType.VISION_REMOVE));
        }
    }

    private int getEntityLevel(int baseLevel, int worldLevelOverride) {
        return Math.max(1, Math.min(Math.max(worldLevelOverride + baseLevel - 22, baseLevel), 100));
    }
}
