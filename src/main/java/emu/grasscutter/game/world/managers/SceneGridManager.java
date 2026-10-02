package emu.grasscutter.game.world.managers;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.Loggers;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.ResourceLoader;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.data.excels.SceneData;
import emu.grasscutter.data.server.Grid;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.scripts.ScriptLoaderLib;
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
import org.anime_game_servers.gi_lua.models.scene.SceneMeta;
import org.anime_game_servers.gi_lua.models.scene.block.SceneGroupInfo;
import org.anime_game_servers.gi_lua.models.scene.group.SceneCreature;
import org.anime_game_servers.gi_lua.models.scene.group.SceneGadget;
import org.anime_game_servers.gi_lua.models.scene.group.SceneObject;
import org.slf4j.Logger;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.IntStream;

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

    /**
     * Gets the group grids of the scene from the cache file, generated beforehand with the -cache-scene-grids startup argument.
     * Generating them here would load every static group script of the scene into the game session.
     */
    public static List<Grid> getGroupGrids(@NonNull Scene scene) {
        int sceneId = scene.getId();
        var cached = groupGridsCache.get(sceneId);
        if (cached != null) {
            logger.debug("Hit cache for scene {}", sceneId);
            return cached;
        }

        var path = getGridCachePath(sceneId);
        List<Grid> groupGrids = null;
        if (path.toFile().isFile()) {
            try {
                groupGrids = JsonUtils.loadToList(path, Grid.class);
            } catch (IOException e) {
                logger.error("exception during group grid loading", e);
            }
        }
        if (groupGrids == null) {
            logger.error("Scene {} has no grid cache at {}, run the server with -cache-scene-grids={} to generate it", sceneId, path, sceneId);
            // Empty grids, so this isn't retried every tick
            groupGrids = createGrids(IntStream.range(0, 6).<Map<GridPosition, Set<Integer>>>mapToObj(i -> new HashMap<>()).toList());
        }
        groupGridsCache.put(sceneId, groupGrids);
        return groupGrids;
    }

    /**
     * Generates the grid cache files of the given scenes, or of all scenes if the parameter is null.
     * @param parameter Comma separated scene ids.
     */
    public static void generateGridCache(String parameter) {
        // Only what the grids need: gadget vision levels, and the scene list
        ResourceLoader.loadResources(GadgetData.class, SceneData.class);
        val sceneIds = parameter == null ? GameData.getSceneDataMap().keySet().intStream().sorted().boxed().toList()
            : Arrays.stream(parameter.split(",")).map(String::trim).map(Integer::parseInt).toList();
        val scriptLoader = ScriptSystem.getScriptLoader();
        for (val sceneId : sceneIds) {
            try {
                // A fresh meta, not the one cached by ScriptSystem, so the loaded groups are dropped after each scene
                val meta = SceneMeta.of(sceneId, scriptLoader);
                if (meta == null) {
                    logger.warn("Scene {} has no scene meta, skipped", sceneId);
                    continue;
                }
                saveGroupGrids(sceneId, buildGroupGrids(meta, scriptLoader));
            } catch (Exception e) {
                logger.error("Scene {} unable to generate grid file.", sceneId, e);
            } finally {
                scriptLoader.clearCache();
            }
        }
    }

    private static Path getGridCachePath(int sceneId) {
        return FileUtils.getCachePath("scene" + sceneId + "_grid.json");
    }

    private static List<Grid> createGrids(List<Map<GridPosition, Set<Integer>>> groupPositions) {
        var groupGrids = new ArrayList<Grid>();
        for (val positions : groupPositions) {
            val grid = new Grid();
            grid.grid = positions;
            groupGrids.add(grid);
        }
        return groupGrids;
    }

    private static List<Grid> buildGroupGrids(SceneMeta meta, ScriptLoaderLib scriptLoader) {
        List<Map<GridPosition, Set<Integer>>> groupPositions = new ArrayList<>();
        for (int i = 0; i < 6; i++) groupPositions.add(new HashMap<>());

        var visionOptions = Grasscutter.config.server.game.visionOptions;
        val groupIterator = meta.getGroups().values().iterator();
        while (groupIterator.hasNext()) {
            val group = groupIterator.next();
            if (group.getGroupInfo().isDynamicLoad()) continue;
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

            // Only the positions are needed, close the lua state, and drop the group from this throwaway meta
            ScriptLoaderLib.closeScript(group.getScript());
            groupIterator.remove();
            scriptLoader.clearCache();
        }

        return createGrids(groupPositions);
    }

    private static void saveGroupGrids(int sceneId, List<Grid> groupGrids) {
        var path = getGridCachePath(sceneId);
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException ignored) {}
        try (var file = new FileWriter(path.toFile())) {
            file.write(JsonUtils.encode(groupGrids));
            logger.info("Scene {} saved grid file.", sceneId);
        } catch (Exception e) {
            logger.error("Scene {} unable to save grid file.", sceneId, e);
        }
    }

}
