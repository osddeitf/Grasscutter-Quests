package emu.grasscutter.scripts;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.Loggers;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.WorldLevelData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.entity.create_config.CreateGadgetEntityConfig;
import emu.grasscutter.game.entity.create_config.CreateMonsterEntityConfig;
import emu.grasscutter.game.entity.create_config.CreateNpcEntityConfig;
import emu.grasscutter.game.props.EntityType;
import emu.grasscutter.game.quest.GameQuest;
import emu.grasscutter.game.quest.QuestGroupSuite;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.SceneGroupInstance;
import emu.grasscutter.scripts.lua_engine.GroupEventLuaContext;
import emu.grasscutter.scripts.lua_engine.service.ScriptMonsterSpawnService;
import emu.grasscutter.scripts.lua_engine.service.ScriptMonsterTideService;
import emu.grasscutter.server.packet.send.PacketGroupSuiteNotify;
import io.netty.util.concurrent.FastThreadLocalThread;
import kotlin.Pair;
import lombok.Getter;
import lombok.val;
import org.anime_game_servers.gi_lua.GIScriptHandler;
import org.anime_game_servers.gi_lua.models.ScriptArgs;
import org.anime_game_servers.gi_lua.models.constants.EventType;
import org.anime_game_servers.gi_lua.models.scene.SceneConfig;
import org.anime_game_servers.gi_lua.models.scene.SceneMeta;
import org.anime_game_servers.gi_lua.models.scene.block.SceneBlock;
import org.anime_game_servers.gi_lua.models.scene.group.*;
import org.anime_game_servers.lua.engine.LuaValue;
import org.anime_game_servers.lua.models.BooleanLuaValue;
import org.anime_game_servers.multi_proto.gi.messages.scene.VisionType;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import emu.grasscutter.utils.Position;
import javax.script.ScriptException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.anime_game_servers.gi_lua.models.constants.EventType.EVENT_TIMER_EVENT;
import static org.anime_game_servers.gi_lua.models.scene.group.SceneTrigger.INF_TRIGGERS;

public class SceneScriptManager {
    private static final Logger logger = Loggers.getScriptSystem();
    private final Scene scene;
    private final Map<String, Integer> variables;
    @Getter private SceneMeta meta;
    private boolean isInit;
    /**
     * current triggers controlled by RefreshGroup
     */
    private final Map<Integer, Set<SceneTrigger>> currentTriggers;
    private final Map<String, Set<SceneTrigger>> triggersByGroupScene;
    private final Map<Integer, Set<Pair<String, Integer>>> activeGroupTimers;
    /** The running time axes, by group and time axis key, see {@link #tickTimeAxes} */
    private final Map<Integer, Map<String, TimeAxis>> activeTimeAxes = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> triggerInvocations;
    private final Map<Integer, SceneGroupInstance> sceneGroupsInstances;
    private final Map<Integer, SceneGroupInstance> cachedSceneGroupsInstances;
    private ScriptMonsterTideService scriptMonsterTideService;
    private ScriptMonsterSpawnService scriptMonsterSpawnService;
    public static final ExecutorService eventExecutor;

    static {
        eventExecutor = new ThreadPoolExecutor(4, 4,
            60, TimeUnit.SECONDS, new LinkedBlockingDeque<>(10000),
            r -> {
                Thread thread = new FastThreadLocalThread(r);
                thread.setUncaughtExceptionHandler((t, e) ->
                    Loggers.getScriptSystem().error("Uncaught exception in script handling", e)
                );
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    }
    public SceneScriptManager(Scene scene) {
        this.scene = scene;
        this.currentTriggers = new ConcurrentHashMap<>();
        this.triggersByGroupScene = new ConcurrentHashMap<>();
        this.activeGroupTimers = new ConcurrentHashMap<>();
        this.triggerInvocations = new ConcurrentHashMap<>();

        this.variables = new ConcurrentHashMap<>();
        this.sceneGroupsInstances = new ConcurrentHashMap<>();
        this.cachedSceneGroupsInstances = new ConcurrentHashMap<>();
        this.scriptMonsterSpawnService = new ScriptMonsterSpawnService(this);

        // TEMPORARY
        if (this.getScene().getId() < 10 && !Grasscutter.getConfig().server.game.enableScriptInBigWorld) {
            return;
        }

        // Create
        this.init();
    }

    public Scene getScene() {
        return scene;
    }

    public SceneConfig getConfig() {
        if (!isInit) {
            return null;
        }
        return meta.getConfig();
    }

    public Map<Integer, SceneBlock> getBlocks() {
        return meta.getBlocks();
    }

    @Nullable
    public Map<String, Integer> getVariables(int group_id) {
        if(getCachedGroupInstanceById(group_id) == null) return Collections.emptyMap();
        return getCachedGroupInstanceById(group_id).getCachedVariables();
    }

    public Set<SceneTrigger> getTriggersByEvent(int eventId) {
        return currentTriggers.computeIfAbsent(eventId, e -> ConcurrentHashMap.newKeySet());
    }
    public int getTriggerCount() {
        return currentTriggers.size();
    }
    public void registerTrigger(List<SceneTrigger> triggers) {
        triggers.forEach(this::registerTrigger);
    }
    public void registerTrigger(SceneTrigger trigger) {
        triggerInvocations.put(trigger.getName(), new AtomicInteger(0));
        getTriggersByEvent(trigger.getEvent()).add(trigger);
        logger.debug("Registered trigger {}", trigger.getName());
    }

    public void deregisterTrigger(List<SceneTrigger> triggers) {
        triggers.forEach(this::deregisterTrigger);
    }
    public void deregisterTrigger(SceneTrigger trigger) {
        getTriggersByEvent(trigger.getEvent()).remove(trigger);
        logger.debug("deregistered trigger {}", trigger.getName());
    }

    public void resetTriggers(int eventId) {
        currentTriggers.put(eventId, ConcurrentHashMap.newKeySet());
    }

    public void resetTriggersForGroupSuite(SceneGroupInstance groupInstance, int suiteIndex) {
        val groupId = groupInstance.getGroupId();
        val luaGroup = meta.getGroups().get(groupId);
        logger.debug("reset triggers for group {} suite {}", groupId, suiteIndex);
        var suite = luaGroup.getSuiteByIndex(suiteIndex);
        if (suite == null) {
            logger.warn("Trying to load null suite Triggers for group {} with suiteindex {}", groupId, suiteIndex);
            return;
        }

        var groupSceneTriggers = triggersByGroupScene.get(groupId+"_"+suiteIndex);
        if(groupSceneTriggers == null){
            groupSceneTriggers = new HashSet<>();
        }

        if(!groupSceneTriggers.isEmpty()) {
            for (var trigger : groupSceneTriggers) {
                currentTriggers.get(trigger.getEvent()).remove(trigger);
            }
            groupSceneTriggers.clear();
        }

        if (!suite.getSceneTriggers().isEmpty()) {
            groupSceneTriggers.addAll(suite.getSceneTriggers());
            for (var trigger : groupSceneTriggers) {
                registerTrigger(trigger);
                /*this.currentTriggers.computeIfAbsent(trigger.event, k -> ConcurrentHashMap.newKeySet())
                    .add(trigger);*/
            }
        }
        triggersByGroupScene.put(groupId+"_"+suiteIndex, groupSceneTriggers);
    }

    public void refreshGroup(int groupId, int suiteIndex, boolean excludePrevSuite) {
        refreshGroup(getGroupInstanceById(groupId));
    }
    public void refreshGroup(SceneGroupInstance groupInstance) {
        if(groupInstance == null || groupInstance.getLuaGroup().getSuites()==null){
            return;
        }
        //for (int i = 1; i<= group.suites.size();i++){
            //refreshGroup(group, i);
            refreshGroup(groupInstance, groupInstance.getActiveSuiteId(), false); //Refresh the last group triggers
        //}
    }
    public int refreshGroup(SceneGroupInstance groupInstance, int suiteIndex, boolean excludePrevSuite) {
        return refreshGroup(groupInstance, suiteIndex, excludePrevSuite, null, false, true);
    }
    public int refreshGroup(SceneGroupInstance groupInstance, int suiteIndex, boolean excludePrevSuite, boolean dontLoad) {
        return refreshGroup(groupInstance, suiteIndex, excludePrevSuite, null, dontLoad, true);
    }
    /** Sets up a group being loaded: its suite without EVENT_GROUP_REFRESH, the load has its own EVENT_GROUP_LOAD */
    public int refreshGroup(SceneGroupInstance groupInstance, int suiteIndex, boolean excludePrevSuite, List<GameEntity<?>> entitiesAdded) {
        return refreshGroup(groupInstance, suiteIndex, excludePrevSuite, entitiesAdded, false, false);
    }

    public int refreshGroup(SceneGroupInstance groupInstance, int suiteIndex, boolean excludePrevSuite, List<GameEntity<?>> entitiesAdded,
                            boolean dontLoad, boolean callRefreshEvent) {
        val groupId = groupInstance.getGroupId();
        SceneGroup group = meta.getGroups().get(groupId);
        if(suiteIndex == 0) {
            val currentSuiteId = groupInstance.getActiveSuiteId();
            val excludeIndex = excludePrevSuite ? currentSuiteId : 0;
            suiteIndex = group.findInitSuiteIndex(currentSuiteId, excludeIndex);
        }
        if(suiteIndex == 0) return 0;

        var suiteData = group.getSuiteByIndex(suiteIndex);
        if (suiteData == null) {
            logger.warn("Group {} suite {} not found", groupId, suiteIndex);
            return 0;
        }

        int prevSuiteIndex = groupInstance.getActiveSuiteId();
        boolean waitForOne = false;
        SceneSuite prevSuiteData = null;
        if(prevSuiteIndex != 0) {
            prevSuiteData = group.getSuiteByIndex(prevSuiteIndex);
            if (prevSuiteData != null) {
                if(prevSuiteData.getBanRefresh() && !suiteData.getBanRefresh()) {
                    waitForOne = true;
                }
            }
        }

        if(waitForOne && (groupInstance.getTargetSuiteId() == 0 || prevSuiteIndex != groupInstance.getTargetSuiteId())) {
            groupInstance.setTargetSuiteId(suiteIndex);
            logger.debug("Group {} suite {} wating one more refresh", groupId, suiteIndex);
            return 0;
        }

        groupInstance.setTargetSuiteId(0);

        if(!dontLoad) {
            if(prevSuiteData != null) {
                removeGroupSuite(group, prevSuiteData);
            } //Remove old group suite

            addGroupSuite(groupInstance, suiteData, entitiesAdded);
        }

        //Refesh variables here
        group.getVariables().forEach(variable -> {
            if(!variable.getNoRefresh())
                groupInstance.getCachedVariables().put(variable.getName(), variable.getValue());
        });

        groupInstance.setActiveSuiteId(suiteIndex);
        groupInstance.setLastTimeRefreshed(getScene().getWorld().getGameTime());
        if (callRefreshEvent) callEvent(new ScriptArgs(groupId, EventType.EVENT_GROUP_REFRESH));
        return suiteIndex;
    }

    public boolean refreshGroupSuite(int groupId, int suiteId) {
        var targetGroupInstance = getGroupInstanceById(groupId);
        if (targetGroupInstance == null) {
            getGroupById(groupId); //Load the group, this ensures an instance is created and the if neccesary unloaded, but the suite data is stored
            targetGroupInstance = getGroupInstanceById(groupId);
            suiteId = refreshGroup(targetGroupInstance, suiteId, false, true); //If suiteId is zero, the value of suiteId changes
            logger.debug("trying to regresh group suite {} in an unloaded and uncached group {} in scene {}", suiteId, groupId, getScene().getId());
        } else {
            logger.debug("Refreshing group {} suite {}", groupId, suiteId);
            suiteId = refreshGroup(targetGroupInstance, suiteId, false); //If suiteId is zero, the value of suiteId changes
        }
        scene.broadcastPacket(new PacketGroupSuiteNotify(groupId, suiteId));

        return true;
    }
    public boolean refreshGroupSuite(int groupId, int suiteId, GameQuest quest) {
        val result = refreshGroupSuite(groupId, suiteId);
        if(suiteId != 0 && quest != null) {
            quest.getMainQuest().getQuestGroupSuites().add(QuestGroupSuite.of()
                .scene(getScene().getId())
                .group(groupId)
                .suite(suiteId)
                .build());
        }

        return result;
    }

    public boolean refreshGroupMonster(int groupId) {
        val groupInstance = getGroupInstanceById(groupId);
        if (groupInstance == null) {
            logger.warn("trying to refresh monster group in unloaded and uncached group {} in scene {}", groupId, getScene().getId());
            return false;
        }

        val group = groupInstance.getLuaGroup();
        val monsters = group.getMonsters();

        if (monsters == null){
            logger.warn("trying to refresh monster group without monsters {} in scene {}", groupId, getScene().getId());
            return false;
        }
        val monstersToSpawn = monsters.values().stream()
            .filter(m -> {
                var entity = scene.getEntityByConfigId(m.getConfigId(), groupId);
                return (entity == null || entity.getGroupId()!=group.getGroupInfo().getId());/*&& !groupInstance.getDeadEntities().contains(entity); */ //TODO: Investigate the usage of deadEntities
            })
            .map(mob -> createMonster(mob))
            .toList();//TODO check if it interferes with bigworld or anything else
        this.addEntities(monstersToSpawn);

        return true;
    }

    // TODO optimize
    public SceneGroup getGroupById(int groupId) {
        val luaGroup = meta.getGroups().get(groupId);

        if(luaGroup == null) {
            logger.error("Group {} not found in scene {}", groupId, getScene().getId());
            return null;
        }
        if (!this.sceneGroupsInstances.containsKey(groupId)) {
            getScene().getGroupManager().onLoadGroup(List.of(luaGroup));
            getScene().getGroupManager().onRegisterGroups();
        }
        return luaGroup;
    }

    public SceneGroupInstance getGroupInstanceById(int groupId) {
        return sceneGroupsInstances.getOrDefault(groupId, null);
    }

    public Map<Integer, SceneGroupInstance> getCachedGroupInstances() {
        return cachedSceneGroupsInstances;
    }

    public SceneGroupInstance getCachedGroupInstanceById(int groupId) {
        var instance = cachedSceneGroupsInstances.getOrDefault(groupId, null);
        if(instance == null) {
            instance = DatabaseHelper.loadGroupInstance(groupId, scene.getWorld().getHost());
            if(instance != null) cachedSceneGroupsInstances.put(groupId, instance);
        }

        return instance;
    }

    private void init() {
        val scriptSystem = getScene().getWorld().getHost().getServer().getScriptSystem();
        var meta = scriptSystem.getSceneMeta(getScene().getId());
        if (meta == null) {
            return;
        }
        this.meta = meta;
        val activeActivities = this.getScene().getWorld().getHost().getActivityManager().getActiveActivityIds();
        activeActivities.forEach(activityId -> {
            meta.loadActivity(ScriptSystem.getScriptLoader(), activityId);
        });

        // TEMP
        this.isInit = true;
    }

    public boolean isInit() {
        return isInit;
    }

    public void loadGroupFromScript(SceneGroup group) {
        val scriptLoader = ScriptSystem.getScriptLoader();
        val groupId = group.getGroupInfo().getId();
        group.load(scriptLoader);

        if(this.getCachedGroupInstanceById(groupId) != null) {
            this.sceneGroupsInstances.put(groupId, this.cachedSceneGroupsInstances.get(groupId));
            this.cachedSceneGroupsInstances.get(groupId).setCached(false);
            this.cachedSceneGroupsInstances.get(groupId).setLuaGroup(group);
        } else {
            var instance = new SceneGroupInstance(group, getScene().getWorld().getHost());
            this.sceneGroupsInstances.put(groupId, instance);
            this.cachedSceneGroupsInstances.put(groupId, instance);
            instance.save(); //Save the instance
        }

        if (group.getVariables() != null) {
            group.getVariables().forEach(variable -> {
                val variables = this.getVariables(groupId);
                if(variables != null && !variables.containsKey(variable.getName()))
                    variables.put(variable.getName(), variable.getValue());
            });
        }
    }

    public void unregisterGroup(SceneGroup group) {
        this.sceneGroupsInstances.values().removeIf(i -> i.getLuaGroup().equals(group));
        this.cachedSceneGroupsInstances.values().stream().filter(i -> Objects.equals(i.getLuaGroup(),group)).forEach(s -> s.setCached(true));
    }

    public List<EntityGadget> getGadgetsInGroupSuite(SceneGroupInstance groupInstance, SceneSuite suite) {
        val group = groupInstance.getLuaGroup();
        val groupId =  group.getGroupInfo().getId();
        return suite.getSceneGadgets().stream()
            .filter(m -> {
                val entity = scene.getEntityByConfigId(m.getConfigId(), groupId);
                return (entity == null || entity.getGroupId()!=groupId) && (!m.isOneOff() || !m.getPersistent() || !groupInstance.getDeadEntities().contains(m.getConfigId()));
            })
            .map(g -> createGadget(g, groupInstance.getCachedGadgetState(g)))
            .filter(Objects::nonNull)
            .peek(groupInstance::cacheGadgetState)
            .toList();
    }
    public List<EntityMonster> getMonstersInGroupSuite(SceneGroupInstance groupInstance, SceneSuite suite) {
        val group = groupInstance.getLuaGroup();
        val groupId = group.getGroupInfo().getId();
        return suite.getSceneMonsters().stream()
            .filter(m -> {
                var entity = scene.getEntityByConfigId(m.getConfigId(), groupId);
                return (entity == null || entity.getGroupId()!=groupId);/*&& !groupInstance.getDeadEntities().contains(entity); */ //TODO: Investigate the usage of deadEntities
            }) //TODO: Add persistent monster cached data
            .map(mob -> createMonster(mob))
            .filter(Objects::nonNull)
            .toList();
    }

    public void addGroupSuite(SceneGroupInstance groupInstance, SceneSuite suite) {
        addGroupSuite(groupInstance, suite, null);
    }
    public void addGroupSuite(SceneGroupInstance groupInstance, SceneSuite suite, List<GameEntity<?>> entities) {
        // we added trigger first
        registerTrigger(suite.getSceneTriggers());

        var group = groupInstance.getLuaGroup();
        var toCreate = new ArrayList<GameEntity<?>>();
        toCreate.addAll(getGadgetsInGroupSuite(groupInstance, suite));
        toCreate.addAll(getMonstersInGroupSuite(groupInstance, suite));
        if(entities != null)
            entities.addAll(toCreate);
        else
            addEntities(toCreate);

        scene.getRegionManager().registerRegionInGroupSuite(group, suite);
    }
    public void refreshGroupSuite(SceneGroupInstance groupInstance, SceneSuite suite) {
        // we added trigger first
        registerTrigger(suite.getSceneTriggers());

        var group = groupInstance.getLuaGroup();
        var toCreate = new ArrayList<GameEntity<?>>();
        toCreate.addAll(getGadgetsInGroupSuite(groupInstance, suite));
        toCreate.addAll(getMonstersInGroupSuite(groupInstance, suite));
        addEntities(toCreate);

        scene.getRegionManager().registerRegionInGroupSuite(group, suite);
    }
    public void removeGroupSuite(SceneGroup group, SceneSuite suite) {
        deregisterTrigger(suite.getSceneTriggers());
        removeMonstersInGroup(group, suite);
        removeGadgetsInGroup(group, suite);

        suite.getSceneRegions().forEach(scene.getRegionManager()::deregisterRegion);
    }
    public void killGroupSuite(SceneGroup group, SceneSuite suite) {
        deregisterTrigger(suite.getSceneTriggers());

        killMonstersInGroup(group, suite);
        killGadgetsInGroup(group, suite);

        suite.getSceneRegions().forEach(scene.getRegionManager()::deregisterRegion);
    }

    public void startMonsterTideInGroup(int challengeIndex, SceneGroup group, List<Integer> ordersConfigId, int tideSize, int spawnThreshold, int spawnLimit) {
        unloadCurrentMonsterTide();
        this.scriptMonsterTideService =
                new ScriptMonsterTideService(this, challengeIndex, group, ordersConfigId, tideSize, spawnThreshold, spawnLimit);

    }
    public void unloadCurrentMonsterTide() {
        if (this.getScriptMonsterTideService() == null) {
            return;
        }
        this.getScriptMonsterTideService().unload();
    }
    public void spawnMonstersByConfigId(SceneGroup group, int configId, int delayTime) {
        spawnMonstersByConfigId(group, configId, delayTime, null, null);
    }

    /** Spawns the group's monster, at the given position and rotation instead of its own when not null */
    public void spawnMonstersByConfigId(SceneGroup group, int configId, int delayTime,
                                        @Nullable Position pos, @Nullable Position rot) {
        // TODO delay
        val groupId = group.getGroupInfo().getId();
        var entity = scene.getEntityByConfigId(configId, groupId);
        if(entity!=null && entity.getGroupId() == groupId){
            logger.debug("entity already exists failed in group {} with config {}", groupId, configId);
            return;
        }
        val groupMonsters = group.getMonsters();
        if(groupMonsters == null){
            logger.warn("monsters in group {} are null, unable to get configId {}", groupId, configId);
            return;
        }
        val monster = groupMonsters.get(configId);
        if(monster == null){
            logger.warn("configId {} not found in group {}", configId, groupId);
            return;
        }
        entity = createMonster(monster, pos, rot);
        if(entity!=null){
            getScene().addEntity(entity);
        } else {
            logger.warn("failed to create entity with group {} and config {}", groupId, configId);
        }
    }
    // Events
    public void callEvent(int groupId, int eventType) {
        callEvent(new ScriptArgs(groupId, eventType));
    }
    public void callEvent(@Nonnull ScriptArgs params) {
        /**
         * We use ThreadLocal to trans SceneScriptManager context to ScriptLib, to avoid eval script for every groups' trigger in every scene instances.
         * But when callEvent is called in a ScriptLib func, it may cause NPE because the inner call cleans the ThreadLocal so that outer call could not get it.
         * e.g. CallEvent -> set -> ScriptLib.xxx -> CallEvent -> set -> remove -> NPE -> (remove)
         * So we use thread pool to clean the stack to avoid this new issue.
         */
        eventExecutor.execute(() -> this.realCallEvent(params));
    }

    private void realCallEvent(@Nonnull ScriptArgs params) {
        try {
            int eventType = params.type;
            Set<SceneTrigger> relevantTriggers = this.getTriggersByEvent(eventType).stream()
                .filter(t -> params.getGroupId() == 0 || t.getGroupId() == params.getGroupId())
                .filter(t ->  (t.getSource().isEmpty() || t.getSource().equals(params.getEventSource())))
                .collect(Collectors.toSet());

            for (SceneTrigger trigger : relevantTriggers) {
                handleEventForTrigger(params, trigger);
            }
        } catch (Throwable throwable){
            logger.error("Condition Trigger {} triggered exception", params.type, throwable);
        }
    }

    private boolean handleEventForTrigger(ScriptArgs params, SceneTrigger trigger ){
        logger.debug("checking trigger {} for event {}", trigger.getName(), params.type);
        try {
            val group = trigger.getSceneMeta().getGroup(trigger.getGroupId());
            if (evaluateTriggerCondition(trigger, group, params)) {
                callTrigger(trigger, group, params);
                return true;
            } else {
                logger.debug("Condition Trigger {} returned false", trigger.getCondition());
            }
            //TODO some ret do not bool
            return false;
        }
        catch (Throwable ex){
            logger.error("Condition Trigger {} triggered exception", trigger.getName(), ex);
            return false;
        }
    }

    /**
     * Checks if the trigger has a condition and calls it if it does.
     * @param trigger
     * @param params
     * @return true if there is no condition, otherwise the result of the condition call as boolean
     */
    private boolean evaluateTriggerCondition(SceneTrigger trigger, SceneGroup group, ScriptArgs params){
        logger.trace("Call Condition Trigger {}, [{},{},{}]", trigger.getCondition(), params.param1,
            params.getSourceEntityId(), params.getTargetEntityId());
        val condition = trigger.getCondition();
        if(condition == null || condition.isBlank()){
            return true;
        }
        val ret = this.callScriptFunc(trigger.getCondition(), group, params);
        return ret.isBoolean() && ret.asBoolean();
    }

    private void callTrigger(SceneTrigger trigger, SceneGroup group, ScriptArgs params){
        val action = trigger.getAction();
        if(action != null && !action.isBlank()){
            // the SetGroupVariableValueByGroup in tower need the param to record the first stage time
            val result = this.callScriptFunc(trigger.getAction(), group, params);
            // mostly a ScriptLib call failing in the action (the script logs its own warning first), or an early exit,
            // a Lua error being logged on its own: it doesn't end the trigger
            if (result.isInteger() && result.asInteger() != 0) {
                logger.warn("[LUA] Trigger {} of group {}: action {} returned {}",
                    trigger.getName(), group.getGroupInfo().getId(), action, result.asInteger());
            }
        }

        val invocationsCounter = triggerInvocations.get(trigger.getName());
        val invocations = invocationsCounter.incrementAndGet();
        logger.trace("Call Action Trigger {}", trigger.getAction());


        val activeChallenge = scene.getChallenge();
        if(activeChallenge!=null){
            activeChallenge.onGroupTriggerDeath(trigger);
        }

        val triggerData = GameData.getQuestTriggerDataByName(params.getGroupId(), trigger.getName());
        if (triggerData != null && triggerData.getGroupId() == params.getGroupId()) {
            getScene().getPlayers().forEach(p -> {
                p.getQuestManager().queueEvent(QuestContent.QUEST_CONTENT_TRIGGER_FIRE,
                    triggerData.getId(), 0);
            });
        }

        if(trigger.getEvent() == EVENT_TIMER_EVENT){
            cancelGroupTimerEvent(trigger.getGroupId(), trigger.getSource());
        }
        // only deregister once the count is reached: scripts return non-zero (e.g. -1) for "nothing to do"
        if(trigger.getTriggerCount() > INF_TRIGGERS && invocations >= trigger.getTriggerCount()) {
            deregisterTrigger(trigger);
        }
    }

    public boolean callGroupLuaFunction(String funcName, ScriptArgs params, Object... args) {
        val groupId = params.groupId;
        val group = getGroupById(groupId);
        if (group == null) {
            logger.error("callLuaFunction group is null");
            return false;
        }

        val script = group.getScript();
        if (script == null) {
            logger.warn("callScriptFunc script is null");
            return false;
        }

        val context = new GroupEventLuaContext(script.getEngine(), group, params, this);
        try{
            val result = GIScriptHandler.callGroupFunction(script, funcName, context, args);
            return true;
        } catch (RuntimeException | ScriptException | NoSuchMethodException error) {
            logger.error("[LUA] call trigger failed in group {} with {},{}", group.getGroupInfo().getId(), funcName, params, error);
            return false;
        }
    }

    /**
     * Calls a ServerLuaCall function from the group's script, or from the generic ServerLuaCall scripts
     * when the group is missing (e.g. a spawned monster's group 0) or doesn't define the function.
     */
    public boolean callServerLuaFunction(String funcName, ScriptArgs params, Object... args) {
        val group = params.groupId != 0 ? getGroupById(params.groupId) : null;
        var script = group != null ? group.getScript() : null;
        if (script == null || !script.hasMethod(funcName)) {
            script = getScene().getWorld().getServer().getScriptSystem().getServerLuaCallScript(funcName);
        }
        if (script == null) {
            logger.error("[LUA] ServerLuaCall function {} not found in group {} nor the generic scripts", funcName, params.groupId);
            return false;
        }

        val context = new GroupEventLuaContext(script.getEngine(), group, params, this);
        try {
            GIScriptHandler.callGroupFunction(script, funcName, context, args);
            return true;
        } catch (RuntimeException | ScriptException | NoSuchMethodException error) {
            logger.error("[LUA] ServerLuaCall {} failed in group {}, {}", funcName, params.groupId, params, error);
            return false;
        }
    }

    private LuaValue callScriptFunc(@Nonnull String funcName, SceneGroup group, ScriptArgs params) {
        val script = group.getScript();
        if(script==null){
            logger.warn("callScriptFunc script is null");
            return BooleanLuaValue.FALSE;
        }

        val context = new GroupEventLuaContext(script.getEngine(), group, params, this);
        try{
            return GIScriptHandler.callGroupFunction(script, funcName, context, params);
        } catch (RuntimeException | ScriptException | NoSuchMethodException error){
            //groups that end up here because of errors in the lua: 302001042
            logger.error("[LUA] call trigger failed in group {} with {},{}",group.getGroupInfo().getId(),funcName,params,error);
            return BooleanLuaValue.FALSE;
        }
    }

    public ScriptMonsterTideService getScriptMonsterTideService() {
        return scriptMonsterTideService;
    }

    public ScriptMonsterSpawnService getScriptMonsterSpawnService() {
        return scriptMonsterSpawnService;
    }

    public EntityGadget createGadget(SceneGadget g) {
        return createGadget(g, g.getState());
    }

    public EntityGadget createGadget(SceneGadget g, int state) {
        if (g.isOneOff()) {
            var hasEntity = getScene().getEntities().values().stream()
                .filter(e -> e instanceof EntityGadget)
                .filter(e -> e.getGroupId() == g.getGroupId())
                .filter(e -> e.getConfigId() == g.getConfigId())
                .findFirst();
            if (hasEntity.isPresent()) {
                return null;
            }
        }
        val createConfig = new CreateGadgetEntityConfig(g)
            .setGadgetState(state);
        // A saurian placed by the group is a vehicle anyone may get in.
        // TODO: not kept by the world as players' vehicles are, should persist like the group's other entities
        val gadgetData = createConfig.getGadgetData();
        EntityGadget entity = gadgetData != null && gadgetData.getType() == EntityType.Vehicle
            ? new EntityVehicle(getScene(), createConfig)
            : new EntityGadget(getScene(), createConfig);

        if (entity.getGadgetData() == null) {
            return null;
        }

        return entity;
    }
    public EntityNPC createNPC(SceneNPC npc) {
        return new EntityNPC(getScene(), new CreateNpcEntityConfig(npc));
    }
    public EntityMonster createMonster(SceneMonster monster) {
        return createMonster(monster, null, null);
    }

    /** Creates the group's monster, at the given position and rotation instead of its own when not null */
    public EntityMonster createMonster(SceneMonster monster, @Nullable Position pos, @Nullable Position rot) {
        if (monster == null) {
            return null;
        }

        val config = new CreateMonsterEntityConfig(monster);
        if (pos != null) config.setBornPos(pos).setPos(pos.clone());
        if (rot != null) config.setBornRot(rot).setRot(rot.clone());
        val data = config.getMonsterData();

        if (data == null) {
            return null;
        }

        // Calculate level
        int level = monster.getLevel();

        // should monster.level take priority?
        if (getScene().getDungeonManager() != null) {
            level = getScene().getDungeonManager().getLevelForMonster(monster.getConfigId());
        } else if (getScene().getWorld().getWorldLevel() > 0) {
            WorldLevelData worldLevelData = GameData.getWorldLevelDataMap().get(getScene().getWorld().getWorldLevel());

            if (worldLevelData != null) {
                level = worldLevelData.getMonsterLevel();
            }
        }

        config.setLevel(level);
        // Spawn mob
        EntityMonster entity = new EntityMonster(getScene(), config);

        this.getScriptMonsterSpawnService()
                .onMonsterCreatedListener.forEach(action -> action.onNotify(entity));

        return entity;
    }

    public void addEntity(GameEntity<?> gameEntity) {
        getScene().addEntity(gameEntity);
    }

    public void meetEntities(List<? extends GameEntity<?>> gameEntity) {
        getScene().addEntities(gameEntity, VisionType.VISION_MEET);
    }

    public void addEntities(List<? extends GameEntity<?>> gameEntity) {
        getScene().addEntities(gameEntity);
    }

    public void removeEntities(List<? extends GameEntity<?>> gameEntity) {
        getScene().removeEntities(gameEntity.stream().map(e -> (GameEntity<?>) e).collect(Collectors.toList()), VisionType.VISION_REFRESH);
    }

    public void removeMonstersInGroup(SceneGroup group, SceneSuite suite) {
        var configSet = suite.getSceneMonsters().stream()
                .map(SceneObject::getConfigId)
                .collect(Collectors.toSet());
        var toRemove = getScene().getEntities().values().stream()
                .filter(e -> e instanceof EntityMonster)
                .filter(e -> e.getGroupId() == group.getGroupInfo().getId())
                .filter(e -> configSet.contains(e.getConfigId()))
                .toList();

        getScene().removeEntities(toRemove, VisionType.VISION_MISS);
    }
    public void removeGadgetsInGroup(SceneGroup group, SceneSuite suite) {
        var configSet = suite.getSceneGadgets().stream()
                .map(SceneObject::getConfigId)
                .collect(Collectors.toSet());
        var toRemove = getScene().getEntities().values().stream()
                .filter(e -> e instanceof EntityGadget)
                .filter(e -> e.getGroupId() == group.getGroupInfo().getId())
                .filter(e -> configSet.contains(e.getConfigId()))
                .toList();

        getScene().removeEntities(toRemove, VisionType.VISION_MISS);
    }

    public void killMonstersInGroup(SceneGroup group, SceneSuite suite) {
        var configSet = suite.getSceneMonsters().stream()
                .map(SceneObject::getConfigId)
                .collect(Collectors.toSet());
        var toRemove = getScene().getEntities().values().stream()
                .filter(e -> e instanceof EntityMonster)
                .filter(e -> e.getGroupId() == group.getGroupInfo().getId())
                .filter(e -> configSet.contains(e.getConfigId()))
                .toList();

        toRemove.forEach(getScene()::killEntity);
    }
    public void killGadgetsInGroup(SceneGroup group, SceneSuite suite) {
        var configSet = suite.getSceneGadgets().stream()
                .map(SceneObject::getConfigId)
                .collect(Collectors.toSet());
        var toRemove = getScene().getEntities().values().stream()
                .filter(e -> e instanceof EntityGadget)
                .filter(e -> e.getGroupId() == group.getGroupInfo().getId())
                .filter(e -> configSet.contains(e.getConfigId()))
                .toList();

        toRemove.forEach(getScene()::killEntity);
    }

    public int createGroupTimerEvent(int groupID, String source, double time) {
        //TODO also remove timers when refreshing and test
        var group = getGroupById(groupID);
        if(group == null || group.getTriggers() == null){
            logger.warn("trying to create a timer for unknown group with id {} and source {}", groupID, source);
            return 1;
        }
        logger.info("creating group timer event for group {} with source {} and time {}",
            groupID, source, time);
        cancelGroupTimerEvent(groupID, source);
        var taskIdentifier = Grasscutter.getGameServer().getScheduler().scheduleDelayedRepeatingTask(() ->
            callEvent(new ScriptArgs(groupID, EVENT_TIMER_EVENT)
                .setEventSource(source)), (int)time, (int)time);
        var groupTasks = activeGroupTimers.computeIfAbsent(groupID, k -> new HashSet<>());
        groupTasks.add(new Pair<>(source, taskIdentifier));
        return 0;
    }
    public int cancelGroupTimerEvent(int groupID, String source) {
        //TODO test
        var groupTimers = activeGroupTimers.get(groupID);
        if(groupTimers!=null && !groupTimers.isEmpty()) {
            for (var timer : new HashSet<>(groupTimers)) {
                if (timer.component1().equals(source)) {
                    Grasscutter.getGameServer().getScheduler().cancelTask(timer.component2());
                    groupTimers.remove(timer);
                    return 0;
                }
            }
        }

        logger.warn("trying to cancel a timer that's not active {} {}", groupID, source);
        return 0;
    }

    /** A group's time axis: its points are times in milliseconds from the start of a run, in scene time */
    private static final class TimeAxis {
        private final int groupId;
        private final String key;
        private final long[] points;
        private final boolean loop;
        /** Scene time the current run started */
        private long startTime;
        /** Index of the next point to pass */
        private int next;
        /** Scene time it was paused, -1 if running */
        private long pausedAt = -1;

        private TimeAxis(int groupId, String key, long[] points, boolean loop, long startTime) {
            this.groupId = groupId;
            this.key = key;
            this.points = points;
            this.loop = loop;
            this.startTime = startTime;
        }
    }

    /**
     * Fires EVENT_TIME_AXIS_PASS for each point of the axis, at its time in seconds from now: the axis key as the
     * source, the point's 1-based index as param1. A looping axis starts over from its last point. The axis runs on
     * scene time, so a paused game pauses it too.
     */
    public int initTimeAxis(int groupId, String key, List<Float> points, boolean loop) {
        endTimeAxis(groupId, key);
        if (points.isEmpty()) return 0;

        val millis = points.stream().mapToLong(seconds -> Math.round(seconds * 1000)).toArray();
        activeTimeAxes.computeIfAbsent(groupId, k -> new ConcurrentHashMap<>())
            .put(key, new TimeAxis(groupId, key, millis, loop, scene.getSceneTime()));
        return 0;
    }

    public int endTimeAxis(int groupId, String key) {
        val groupAxes = activeTimeAxes.get(groupId);
        if (groupAxes != null) groupAxes.remove(key);
        return 0;
    }

    public int endAllTimeAxis(int groupId) {
        activeTimeAxes.remove(groupId);
        return 0;
    }

    public int pauseTimeAxis(int groupId, String key) {
        val axis = getTimeAxis(groupId, key);
        if (axis == null) return 1;
        if (axis.pausedAt < 0) axis.pausedAt = scene.getSceneTime();
        return 0;
    }

    /** Resumes a paused axis, its remaining points keeping their spacing */
    public int continueTimeAxis(int groupId, String key) {
        val axis = getTimeAxis(groupId, key);
        if (axis == null) return 1;
        if (axis.pausedAt >= 0) {
            axis.startTime += scene.getSceneTime() - axis.pausedAt;
            axis.pausedAt = -1;
        }
        return 0;
    }

    @Nullable
    private TimeAxis getTimeAxis(int groupId, String key) {
        val groupAxes = activeTimeAxes.get(groupId);
        return groupAxes != null ? groupAxes.get(key) : null;
    }

    /**
     * Passes the time axes' points due by now, on the scene tick: so up to a tick late.
     * TODO points are mostly whole seconds, but as fine as 0.1s in some scripts (26 of 1000 literal points, e.g. the
     *  "shining" axis of group 133302049: 0.1, 0.2, 0.4, 0.6, 0.8). On the current 1s scene tick those fire together,
     *  so a 100ms tick for the time axes would be needed for exact timers
     */
    public void tickTimeAxes() {
        long now = scene.getSceneTime();
        // an axis replaced or ended by a script meanwhile is left alone: removeIf only tests the current one
        activeTimeAxes.values().forEach(groupAxes -> groupAxes.values().removeIf(axis -> advanceTimeAxis(axis, now)));
    }

    /** Fires the axis's points due by now, in order, returning whether it ended */
    private boolean advanceTimeAxis(TimeAxis axis, long now) {
        if (axis.pausedAt >= 0) return false;
        while (axis.startTime + axis.points[axis.next] <= now) {
            callEvent(new ScriptArgs(axis.groupId, EventType.EVENT_TIME_AXIS_PASS)
                .setParam1(axis.next + 1).setEventSource(axis.key));
            if (++axis.next < axis.points.length) continue;
            if (!axis.loop) return true;
            // a new run from the last point, not from now, so a looping axis doesn't drift with the tick
            val runLength = axis.points[axis.points.length - 1];
            axis.startTime += runLength;
            axis.next = 0;
            if (runLength <= 0) break;  // a zero-length loop would never catch up
        }
        return false;
    }

    // todo use killed monsters instead of spawned entites for check?
    public boolean isClearedGroupMonsters(int groupId) {
        val groupInstance = getGroupInstanceById(groupId);
        if (groupInstance == null || groupInstance.getLuaGroup() == null) return false;

        val monsters = groupInstance.getLuaGroup().getMonsters();

        if(monsters == null || monsters.isEmpty()) return true;

        return monsters.values().stream().noneMatch(m -> {
            val entity = scene.getEntityByConfigId(m.getConfigId(), groupId);
            return entity != null && entity.getGroupId() == groupId;
        });
    }

    public void onDestroy(){
        activeGroupTimers.forEach((gid,times) -> times.forEach((e)->Grasscutter.getGameServer().getScheduler().cancelTask(e.getSecond())));
        activeGroupTimers.clear();
        activeTimeAxes.clear();
    }
}
