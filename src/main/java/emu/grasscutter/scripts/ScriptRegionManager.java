package emu.grasscutter.scripts;

import emu.grasscutter.Loggers;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityRegion;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.entity.create_config.CreateRegionEntityConfig;
import emu.grasscutter.game.props.EntityType;
import emu.grasscutter.game.quest.enums.QuestCond;
import emu.grasscutter.game.world.Scene;
import org.anime_game_servers.gi_lua.models.ScriptArgs;
import org.anime_game_servers.gi_lua.models.constants.EventType;
import org.anime_game_servers.gi_lua.models.scene.group.SceneGroup;
import org.anime_game_servers.gi_lua.models.scene.group.SceneRegion;
import org.anime_game_servers.gi_lua.models.scene.group.SceneSuite;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ScriptRegionManager {
    private static final Logger logger = Loggers.getScriptSystem();
    private final Scene scene;
    private final Map<Integer, EntityRegion> regions = new ConcurrentHashMap<>();

    public ScriptRegionManager(Scene scene) {
        this.scene = scene;
    }

    public EntityRegion getRegionByConfigId(int configId, int groupId) {
        return this.regions.values().stream()
            .filter(x -> x.getConfigId() == configId && x.getGroupId() == groupId)
            .findFirst()
            .orElse(null);
    }

    public EntityRegion getRegionById(int id) {
        return regions.get(id);
    }

    public void registerRegion(EntityRegion region) {
        regions.put(region.getId(), region);
        logger.debug("Registered region {} from group {}", region.getConfigId(), region.getGroupId());
    }

    public void registerRegionInGroupSuite(SceneGroup group, SceneSuite suite) {
        suite.getSceneRegions().stream()
            .map(region -> new EntityRegion(scene, new CreateRegionEntityConfig(region)))
            .forEach(this::registerRegion);
    }

    public synchronized void deregisterRegion(SceneRegion region) {
        var instance = regions.values().stream()
            .filter(r -> r.getConfigId() == region.getConfigId() && r.getGroupId() == region.getGroupId())
            .findFirst();
        instance.ifPresent(entityRegion -> regions.remove(entityRegion.getId()));
    }

    public void checkRegions() {
        if (this.regions.isEmpty()) {
            return;
        }

        for (var region : this.regions.values()) {
            region.clearDeadEntities();

            scene.getEntities().values().stream()
                .filter(e -> e.getEntityType() == EntityType.Avatar)
                .filter(e -> region.isPosInRegion(e.getPosition()) && !region.getEntities().contains(e))
                .forEach(region::addEntity);

            scene.getEntities().values().stream()
                .filter(e -> e.getEntityType() == EntityType.Avatar)
                .filter(e -> !region.isPosInRegion(e.getPosition()) && !region.getNotContainEntities().contains(e))
                .forEach(region::removeEntity);

            // call enter region events for new entities
            region.getNewEntities().forEach(entity -> callRegionEvent(region, EventType.EVENT_ENTER_REGION, entity));
            region.resetNewEntities();

            // call leave region events for left entities
            region.getLeftEntities().forEach(entity -> callRegionEvent(region, EventType.EVENT_LEAVE_REGION, entity));
            region.resetEntityLeave();
        }
    }

    private void callRegionEvent(EntityRegion region, int eventType, GameEntity<?> entity) {
        scene.getScriptManager().callEvent(new ScriptArgs(region.getGroupId(), eventType, region.getConfigId())
            .setEventSource(entity.getEntityType().getValue())
            .setSourceEntityId(region.getId())
            .setTargetEntityId(entity.getId())
        );
        if (eventType == EventType.EVENT_ENTER_REGION && entity instanceof EntityAvatar avatar) {
            avatar.getPlayer().getQuestManager().queueEvent(QuestCond.QUEST_COND_PLAYER_ENTER_REGION, region.getGroupId(), region.getConfigId());
        }
    }
}
