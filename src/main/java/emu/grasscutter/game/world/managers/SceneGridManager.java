package emu.grasscutter.game.world.managers;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.Loggers;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.server.Grid;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.scripts.ScriptSystem;
import emu.grasscutter.utils.FileUtils;
import emu.grasscutter.utils.GridPosition;
import emu.grasscutter.utils.JsonUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.NonNull;
import lombok.val;
import org.anime_game_servers.gi_lua.models.Position;
import org.anime_game_servers.gi_lua.models.constants.VisionLevelType;
import org.anime_game_servers.gi_lua.models.scene.block.SceneGroupInfo;
import org.anime_game_servers.gi_lua.models.scene.group.SceneCreature;
import org.anime_game_servers.gi_lua.models.scene.group.SceneGadget;
import org.anime_game_servers.gi_lua.models.scene.group.SceneObject;
import org.slf4j.Logger;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public class SceneGridManager {

    private static final Logger logger = Loggers.getScriptSystem(); // TODO: use different logger
    private static final Int2ObjectMap<List<Grid>> groupGridsCache = new Int2ObjectOpenHashMap<>();

    private static void addEntityGridPosToMap(List<Map<GridPosition, Set<Integer>>> groupPositions, Set<VisionLevelType> visionLevels, SceneObject sceneObject, SceneGroupInfo group){
        visionLevels.add(addEntityGridPosToMap(groupPositions, sceneObject, group));
    }

    private static VisionLevelType addEntityGridPosToMap(List<Map<GridPosition, Set<Integer>>> groupPositions, SceneObject sceneObject, SceneGroupInfo group){
        val visionLevel = switch (sceneObject.getType()){
            case GADGET -> {
                val gadgetLevel = getGadgetVisionLevel(((SceneGadget)sceneObject).getGadgetId());
                val scriptLevel = ((SceneCreature)sceneObject).getVisionLevel();
                if(gadgetLevel.getValue() > scriptLevel.getValue()) yield gadgetLevel;
                else yield scriptLevel;
            }
            case REGION -> VisionLevelType.getDefault();
            default -> {
                if(sceneObject instanceof SceneCreature creature)
                    yield creature.getVisionLevel();
                else
                    yield VisionLevelType.getDefault();
            }
        };
        addGridPositionToMap(groupPositions.get(visionLevel.getValue()), group.getId(), visionLevel, sceneObject.getPos());
        return visionLevel;
    }

    private static void addGridPositionToMap(Map<GridPosition, Set<Integer>> map, int group_id, VisionLevelType vision_level, Position position) {
        //Convert position to grid position
        GridPosition gridPos;
        int width = Grasscutter.getConfig().server.game.visionOptions[vision_level.getValue()].gridWidth;
        gridPos = new GridPosition((int)(position.getX() / width), (int)(position.getZ() / width), width);

        Set<Integer> groups = map.getOrDefault(gridPos, new HashSet<>());
        groups.add(group_id);
        map.put(gridPos, groups);
    }

    private static VisionLevelType getGadgetVisionLevel(int gadget_id) {
        var gadget = GameData.getGadgetDataMap().get(gadget_id);
        if(gadget == null || gadget.getVisionLevel() == null) return VisionLevelType.getDefault();

        var visionOptions = Grasscutter.getConfig().server.game.visionOptions;
        for(int i = 0; i < visionOptions.length; i++)
            if(visionOptions[i].name.compareTo(gadget.getVisionLevel()) == 0) {
                return VisionLevelType.valueOf(visionOptions[i].name);
            }

        return VisionLevelType.getDefault();
    }

    public static List<Grid> getGroupGrids(@NonNull Scene scene) {
        int sceneId = scene.getId();
        if (groupGridsCache.containsKey(sceneId) && groupGridsCache.get(sceneId) != null) {
            logger.debug("Hit cache for scene {}",sceneId);
            return groupGridsCache.get(sceneId);
        } else {
            var path = FileUtils.getCachePath("scene" + sceneId + "_grid.json");
            if (path.toFile().isFile() && !Grasscutter.config.server.game.cacheSceneEntitiesEveryRun) {
                try {
                    var groupGrids = JsonUtils.loadToList(path, Grid.class);
                    groupGridsCache.put(sceneId, groupGrids);
                    if(groupGrids != null) return groupGrids;
                } catch (IOException e) {
                    logger.error("exception during group grid loading", e);
                }
            }

            //otherwise generate the grids
            List<Map<GridPosition, Set<Integer>>> groupPositions = new ArrayList<>();
            for (int i = 0; i < 6; i++) groupPositions.add(new HashMap<>());

            var visionOptions = Grasscutter.config.server.game.visionOptions;
            val scriptLoader = ScriptSystem.getScriptLoader();
            val meta = scene.getScriptManager().getMeta();
            meta.getGroups().values().stream().filter(g -> !g.getGroupInfo().isDynamicLoad()).forEach(group -> {
                val groupInfo = group.getGroupInfo();
                val groupId = groupInfo.getId();
                group.load(scriptLoader);

                //Add all entities here
                Set<VisionLevelType> vision_levels = new HashSet<>();

                val monsters = group.getMonsters();
                if (monsters != null) {
                    monsters.values().forEach(m -> addEntityGridPosToMap(groupPositions, vision_levels, m, groupInfo));
                } else {
                    logger.error("group.monsters null for group {}", groupId);
                }
                val gadgets = group.getGadgets();
                if (gadgets != null) {
                    gadgets.values().forEach(g -> addEntityGridPosToMap(groupPositions, vision_levels, g, groupInfo));
                } else {
                    logger.error("group.gadgets null for group {}", groupId);
                }

                val npcs = group.getNpcs();
                if (npcs != null) {
                    npcs.values().forEach(n -> addEntityGridPosToMap(groupPositions, n, groupInfo));
                } else {
                    logger.error("group.npcs null for group {}", groupId);
                }

                val regions = group.getRegions();
                if (regions != null) {
                    regions.values().forEach(r -> addEntityGridPosToMap(groupPositions, r, groupInfo));
                } else {
                    logger.error("group.regions null for group {}", groupId);
                }

                // TODO should we add those to the grid?
                val garbages = group.getGarbages();
                if (garbages != null && garbages.getGadgets() != null) {
                    garbages.getGadgets().forEach(g -> addEntityGridPosToMap(groupPositions, g, groupInfo));
                }

                var maxVisionLevel = VisionLevelType.VISION_LEVEL_NORMAL;
                if (!vision_levels.isEmpty()) {
                    for (val visionLevel : vision_levels) {
                        if (visionOptions[maxVisionLevel.getValue()].visionRange < visionOptions[visionLevel.getValue()].visionRange)
                            maxVisionLevel = visionLevel;
                    }
                }

                addGridPositionToMap(groupPositions.get(maxVisionLevel.getValue()), groupInfo.getId(), maxVisionLevel, groupInfo.getPos());
            });

            var groupGrids = new ArrayList<Grid>();
            for (int i = 0; i < 6; i++) {
                groupGrids.add(new Grid());
                groupGrids.get(i).grid = groupPositions.get(i);
            }
            groupGridsCache.put(scene.getId(), groupGrids);

            try {
                Files.createDirectories(path.getParent());
            } catch (IOException ignored) {}
            try (var file = new FileWriter(path.toFile())) {
                file.write(JsonUtils.encode(groupGrids));
                logger.info("Scene {} saved grid file.", scene.getId());
            } catch (Exception e) {
                logger.error("Scene {} unable to save grid file.", scene.getId(), e);
            }
            return groupGrids;
        }
    }

}
