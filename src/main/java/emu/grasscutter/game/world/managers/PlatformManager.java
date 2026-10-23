package emu.grasscutter.game.world.managers;

import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.gadget.platform.ConfigRoute;
import emu.grasscutter.game.world.Scene;
import lombok.val;
import org.anime_game_servers.gi_lua.models.ScriptArgs;
import org.anime_game_servers.gi_lua.models.constants.EventType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlatformManager {
    private final Scene scene;
    private final Map<Integer, Double> scheduledPlatforms = new ConcurrentHashMap<>();

    public PlatformManager(Scene scene) {
        this.scene = scene;
    }

    public void schedule(int entityId, double time) {
        scheduledPlatforms.put(entityId, time);
    }

    public void unschedule(int entityId) {
        scheduledPlatforms.remove(entityId);
    }

    public void tick() {
        var iter = scheduledPlatforms.entrySet().iterator();
        val curTime = scene.getSceneTime();
        while (iter.hasNext()) {
            val entry = iter.next();
            val key = entry.getKey();
            val value = entry.getValue();
            if (value < curTime) {
                scheduledPlatforms.remove(key);
                val entity = scene.getEntityById(key);
                // Deleted entity
                if (entity == null) continue;
                // Not a platform
                if (!(entity instanceof EntityGadget entityGadget)) continue;
                // No configRoute
                if (!(entityGadget.getRouteConfig() instanceof ConfigRoute configRoute)) continue;
                // No route in file
                val route = scene.getSceneRouteById(configRoute.getRouteId());
                if (route == null) continue;

                val points = route.getPoints();

                // Move on to the next point, along the route's direction
                var index = configRoute.nextIndex(route);
                configRoute.setStartIndex(index);

                // Update position
                entity.getPosition().set(points[index].getPos());

                // If the point has a Reach Event:
                if (points[index].isHasReachEvent()) {
                    callPlatformEvent(key);
                }

                //if there is a Reach Stop, or we have reached the end, call stop
                if (points[index].isReachStop() || configRoute.isAtEnd(route)) {
                    entityGadget.stopPlatform();
                    //otherwise, schedule the next one: a Reciprocate route turns around at either end
                } else {
                    configRoute.turnAtEnd(route);
                    double distance = points[index].getPos().computeDistance(points[configRoute.nextIndex(route)].getPos());
                    double time = 1000 * distance / points[index].getTargetVelocity();
                    scheduledPlatforms.put(key, value + time);
                }
            }
        }
    }

    public boolean callPlatformEvent(int entityId) {
        val entity = scene.getEntityById(entityId);
        if (entity == null) return false;
        if (!(entity instanceof EntityGadget entityGadget)) return false;
        if (!(entityGadget.getRouteConfig() instanceof ConfigRoute configRoute)) return false;

        val groupId = entityGadget.getGroupId();
        val configId = entityGadget.getConfigId();
        val routeId = configRoute.getRouteId();
        val index = configRoute.getStartIndex();

        scene.getScriptManager().callEvent(new ScriptArgs(groupId, EventType.EVENT_PLATFORM_REACH_POINT, configId, routeId)
                .setParam3(index)
                .setEventSource(configId));
        return true;
    }
}
