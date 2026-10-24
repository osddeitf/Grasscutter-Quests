package emu.grasscutter.game.world;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.WeatherData;
import emu.grasscutter.data.GameDepot;
import emu.grasscutter.data.binout.SceneNpcBornEntry;
import emu.grasscutter.data.binout.routes.Route;
import emu.grasscutter.data.binout.routes.RouteType;
import emu.grasscutter.data.common.ScenePointArrayData;
import emu.grasscutter.data.excels.CodexAnimalData;
import emu.grasscutter.data.excels.DungeonData;
import emu.grasscutter.data.excels.SceneData;
import emu.grasscutter.data.excels.WorldLevelData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.dungeons.DungeonManager;
import emu.grasscutter.game.dungeons.challenge.WorldChallenge;
import emu.grasscutter.game.dungeons.enums.DungeonPassConditionType;
import emu.grasscutter.game.dungeons.settle_listeners.DungeonSettleListener;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.entity.create_config.CreateEntityConfig;
import emu.grasscutter.game.entity.create_config.CreateGadgetEntityConfig;
import emu.grasscutter.game.entity.create_config.CreateMonsterEntityConfig;
import emu.grasscutter.game.entity.create_config.CreateRegionEntityConfig;
import emu.grasscutter.game.entity.gadget.content.GadgetWorktop;
import emu.grasscutter.game.entity.gadget.platform.ConfigRoute;
import emu.grasscutter.game.modules.widget.manager.WidgetAnchorPointManager;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamInfo;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.quest.QuestGroupSuite;
import emu.grasscutter.game.world.data.TeleportProperties;
import emu.grasscutter.game.world.managers.NpcGroupManager;
import emu.grasscutter.game.world.managers.SceneGroupManager;
import emu.grasscutter.game.world.managers.PlatformManager;
import emu.grasscutter.game.world.managers.PlayerRespawnManager;
import emu.grasscutter.game.world.managers.SpawnManager;
import emu.grasscutter.game.world.managers.seal_battle.SealBattleManager;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.scripts.SceneIndexManager;
import emu.grasscutter.scripts.SceneScriptManager;
import emu.grasscutter.scripts.ScriptRegionManager;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.utils.KahnsSort;
import emu.grasscutter.utils.Position;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import kotlin.Pair;
import lombok.Getter;
import lombok.Setter;
import lombok.val;
import org.anime_game_servers.gi_lua.models.SceneGroupUserData;
import org.anime_game_servers.gi_lua.models.ScriptArgs;
import org.anime_game_servers.gi_lua.models.constants.EventType;
import org.anime_game_servers.gi_lua.models.scene.block.SceneBlock;
import org.anime_game_servers.gi_lua.models.scene.block.SceneGroupInfo;
import org.anime_game_servers.gi_lua.models.scene.group.SceneGadget;
import org.anime_game_servers.gi_lua.models.scene.group.SceneGroup;
import org.anime_game_servers.gi_lua.models.scene.group.SceneInitConfig;
import org.anime_game_servers.gi_lua.utils.GroupUtils;
import org.anime_game_servers.multi_proto.gi.messages.battle.event.AttackResult;
import org.anime_game_servers.multi_proto.gi.messages.gadget.SelectWorktopOptionReq;
import org.anime_game_servers.multi_proto.gi.messages.scene.EnterType;
import org.anime_game_servers.multi_proto.gi.messages.scene.VisionType;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


public class Scene {
    @Getter private final World world;
    @Getter private final SceneData sceneData;
    @Getter private final SceneInstanceData sceneInstanceData;
    @Getter private final List<Player> players = new CopyOnWriteArrayList<>();
    @Getter private final Map<Integer, GameEntity<?>> entities = new ConcurrentHashMap<>();
    @Getter private final Map<Integer, GameEntity<?>> weaponEntities = new ConcurrentHashMap<>();
    @Getter private final SceneGroupManager groupManager;
    private final HashSet<Integer> unlockedForces = new HashSet<>();
    private final List<Runnable> afterLoadedCallbacks = new ArrayList<>();
    private final long startWorldTime;
    @Getter DungeonManager dungeonManager;
    @Getter @Setter SealBattleManager sealBattleManager;
    @Getter Int2ObjectMap<Route> sceneRoutes;
    @Getter List<ScenePointArrayData> pointArrays;
    @Getter @Setter private boolean dontDestroyWhenEmpty;
    @Getter private final SceneScriptManager scriptManager;
    @Getter @Setter private WorldChallenge challenge;
    @Getter private final List<DungeonSettleListener> dungeonSettleListeners = new ArrayList<>();
    @Getter @Setter private int prevScene; // id of the previous scene
    @Getter @Setter private int prevScenePoint;
    @Getter @Setter private int killedMonsterCount;
    @Getter @Setter private int killChestCount;
    @Getter private boolean finishedLoading = false;
    @Getter private int tickCount = 0;
    @Getter private boolean isPaused = false;
    /** Scene seconds after which a weather area without players is released */
    public static final int WEATHER_AREA_RELEASE_DELAY = 60;
    /** The weather areas with a player inside, or empty for less than WEATHER_AREA_RELEASE_DELAY */
    @Getter private final Map<Integer, WeatherArea> weatherAreas = new ConcurrentHashMap<>();

    @Getter private final GameEntity sceneEntity;
    @Getter private final SpawnManager spawnManager;
    @Getter private final NpcGroupManager npcGroupManager;
    @Getter private final PlatformManager platformManager;
    @Getter private final PlayerRespawnManager respawnManager;
    @Getter private final ScriptRegionManager regionManager;

    public Scene(World world, SceneData sceneData) {
        this.world = world;
        this.sceneData = sceneData;
        SceneInstanceData data = DatabaseHelper.loadSceneInstanceData(sceneData.getId(), world.getOwner());

        if(data != null)
            this.sceneInstanceData = data;
        else
            this.sceneInstanceData = new SceneInstanceData(this, world.getOwner());

        this.prevScene = 3;
        this.sceneRoutes = GameData.getSceneRoutes(getId());
        this.pointArrays = GameData.getScenePointArrays(getId());

        this.startWorldTime = world.getWorldTime();

        this.scriptManager = new SceneScriptManager(this);
        getWorld().getHost().getActivityManager().triggerSceneLoadForActiveActivity(this);
        this.sealBattleManager = new SealBattleManager(this);
        this.groupManager = new SceneGroupManager(this);
        this.spawnManager = new SpawnManager(this);
        this.npcGroupManager = new NpcGroupManager(this);
        this.platformManager = new PlatformManager(this);
        this.respawnManager = new PlayerRespawnManager(this);
        this.regionManager = new ScriptRegionManager(this);

        //Create scene entity
        this.sceneEntity = new EntityScene(this);
    }

    public int getId() {
        return this.sceneData.getId();
    }

    public SceneType getSceneType() {
        return this.sceneData.getSceneType();
    }

    public int getPlayerCount() {
        return this.players.size();
    }

    public void setDungeonManager(DungeonManager dungeonManager) {
        this.dungeonManager = dungeonManager;
        // the dungeon decides part of the scene entity's abilities
        if (this.sceneEntity instanceof EntityScene entityScene) entityScene.reinitAbilities();
    }

    public GameEntity<?> getEntityById(int id) {
        if (id == this.sceneEntity.getId()) return this.sceneEntity;
        else if (id == this.world.getLevelEntityId()) return this.world.getEntity();

        val teamEntityPlayer = this.players.stream().filter(p -> p.getTeamManager().getEntity().getId() == id).findAny();
        if(teamEntityPlayer.isPresent()) return teamEntityPlayer.get().getTeamManager().getEntity();
        GameEntity<?> entity = this.entities.get(id);
        if (entity == null) entity = this.weaponEntities.get(id);
        if (entity == null && EntityIdType.idFromEntityId(id) == EntityIdType.AVATAR.getId()) {
            entity = this.players.stream().map(p -> p.getTeamManager().getActiveTeam()).flatMap(List::stream)
                .filter(entityAvatar -> entityAvatar.getId() == id)
                .findFirst()
                .map(GameEntity.class::cast).orElse(null);
        }

        return entity;
    }

    public GameEntity<?> getEntityByConfigId(int configId) {
        return this.entities.values().stream()
            .filter(x -> x.getConfigId() == configId)
            .findFirst()
            .orElse(null);
    }

    @Nullable
    public GameEntity<?> getEntityByConfigId(int configId, int groupId) {
        return this.entities.values().stream()
            .filter(x -> x.getConfigId() == configId && x.getGroupId() == groupId)
            .findFirst()
            .orElse(null);
    }

    @Nullable
    public Route getSceneRouteById(int routeId) {
        return this.sceneRoutes.get(routeId);
    }

    @Nullable
    public ScenePointArrayData getPointArrayById(int routeId) {
        return this.pointArrays.stream().filter(x -> x.getPointArrayId() == routeId).findFirst().orElse(null);
    }

    public void setPaused(boolean paused) {
        if (this.isPaused != paused) {
            this.isPaused = paused;
            broadcastPacket(new PacketSceneTimeNotify(this));
        }
    }

    public int getSceneTime() {
        return (int) (this.world.getWorldTime() - this.startWorldTime);
    }

    public int getSceneTimeSeconds() {
        return getSceneTime() / 1000;
    }

    public void addDungeonSettleObserver(DungeonSettleListener dungeonSettleListener) {
        this.dungeonSettleListeners.add(dungeonSettleListener);
    }

    public void triggerDungeonEvent(DungeonPassConditionType conditionType, int... params) {
        Optional.ofNullable(this.dungeonManager).ifPresent(m -> m.triggerEvent(conditionType, params));
    }

    /** Whether the entity is spawned in this scene, unlike {@link #getEntityById} also finding the team avatars off the field */
    public boolean isInScene(GameEntity entity) {
        return this.entities.containsKey(entity.getId());
    }

    public synchronized void addPlayer(Player player) {
        // Check if player already in
        if (this.players.contains(player)) return;

        // Remove player from prev scene
        if (player.getScene() != null) player.getScene().removePlayer(player);

        // Add
        this.players.add(player);
        player.setSceneId(getId());
        player.setScene(this);
        player.visitScene(getId());

        setupPlayerAvatars(player);
        WidgetAnchorPointManager.spawnSavedEntities(player);

        //update weathers
        player.updateWeather(this);
    }

    public synchronized void removePlayer(Player player) {
        // Remove from challenge if leaving, challenge has progress check
        Optional.ofNullable(this.challenge).ifPresent(WorldChallenge::fail);

        // Off any vehicle, without packets to the leaving player (e.g. the vehicle's server buff is removed).
        // TODO: the official mechanism is unclear, other players in the scene may need notifying (e.g. the vehicle's
        //  members changed)
        getVehiclesOf(player).forEach(vehicle -> vehicle.removeMember(player));

        // Out of its weather area
        Optional.ofNullable(this.weatherAreas.get(player.getWeatherAreaId())).ifPresent(area -> area.leaveArea(player));
        player.setWeatherAreaId(0);

        // Remove player from scene
        this.players.remove(player);
        player.setScene(null);

        // Remove player avatars
        removePlayerAvatars(player);

        // Remove player gadgets
        player.getTeamManager().getGadgets().forEach(this::removeEntity);

        // Deregister scene if not in use
        if (getPlayerCount() <= 0 && !this.dontDestroyWhenEmpty) {
            this.scriptManager.onDestroy();
            this.world.deregisterScene(this);
        }
        saveGroups();
        saveSceneInstanceData();
    }

    private void setupPlayerAvatars(@NotNull Player player) {
        val activeTeam = player.getTeamManager().getActiveTeam();
        // Re-entering this scene, e.g. a teleport within it: the official server keeps the avatar entities and their
        // ids, so the client keeps their ability state (e.g. Hu Tao's C6 cooldown) instead of re-initializing them
        val kept = activeTeam.stream().filter(entity -> entity.getScene() == this)
            .collect(Collectors.toMap(entity -> entity.getAvatar().getAvatarId(), entity -> entity, (a, b) -> a));
        // Clear entities from old team
        activeTeam.clear();
        Optional.ofNullable(player.getTeamManager().getCurrentTeamInfo()).map(TeamInfo::getAvatars).stream()
            .flatMap(List::stream).map(player.getAvatars()::getAvatarById)
            .filter(Objects::nonNull)
            .map(avatar -> Optional.ofNullable(kept.get(avatar.getAvatarId())).orElseGet(() -> new EntityAvatar(player.getScene(), avatar)))
            .forEach(activeTeam::add);
    }

    private synchronized void removePlayerAvatars(@NotNull Player player) {
        removeEntities(player.getTeamManager().getActiveTeam(), VisionType.VISION_MISS);
    }

    public void spawnPlayer(Player player) {
        val teamManager = player.getTeamManager();
        val curAvatarEntity = teamManager.getCurrentAvatarEntity();
        if (isInScene(curAvatarEntity)) return;

        if (curAvatarEntity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP) <= 0f) {
            curAvatarEntity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 1f);
        }

        addEntity(curAvatarEntity);

        // Notify the client of any extra skill charges
        teamManager.getActiveTeam().stream().map(EntityAvatar::getAvatar).forEach(Avatar::sendSkillExtraChargeMap);
    }

    public void addEntityDirectly(GameEntity entity) {
        this.entities.put(entity.getId(), entity);
        entity.onCreate(); // Call entity create event
    }

    public synchronized void addEntity(GameEntity entity) {
        addEntityDirectly(entity);
        broadcastPacket(new PacketSceneEntityAppearNotify(entity));
        entity.afterCreate(this.players);
    }
    public synchronized void addEntity(CreateEntityConfig config) {
        GameEntity<?> entity = null;
        if(config.getClass() == CreateMonsterEntityConfig.class) {
            entity = new EntityMonster(this, (CreateMonsterEntityConfig) config);
        } else if(config.getClass() == CreateGadgetEntityConfig.class) {
            entity = new EntityGadget(this, (CreateGadgetEntityConfig) config);
        }

        if(entity == null) {
            Grasscutter.getLogger().warn("Unknown/Unhandled entity config type: {}", config.getClass());
            return;
        }

        addEntityDirectly(entity);
        broadcastPacket(new PacketSceneEntityAppearNotify(entity));
    }

    public synchronized void addEntityToSingleClient(Player player, GameEntity entity) {
        addEntityDirectly(entity);
        player.sendPacket(new PacketSceneEntityAppearNotify(entity));
        entity.afterCreate(List.of(player));
    }

    public void addEntities(Collection<? extends GameEntity> entities) {
        addEntities(entities, VisionType.VISION_BORN);
    }

    public void updateEntity(GameEntity entity) {
        this.broadcastPacket(new PacketSceneEntityUpdateNotify(entity));
    }

    public void updateEntity(GameEntity entity, VisionType type) {
        this.broadcastPacket(new PacketSceneEntityUpdateNotify(List.of(entity), type));
    }

    private static <T> List<List<T>> chopped(List<T> list, final int L) {
        return IntStream.range(0, (list.size() + L - 1) / L)
            .mapToObj(i -> list.subList(i * L, Math.min(list.size(), (i + 1) * L)))
            .toList();
    }

    private int getGadgetOwner(GameEntity entity) {
        if (entity.getSpawnConfig().getInitDataSource() instanceof SceneGadget g) {
            return g.getOwner();
        }
        return 0;
    }

    public synchronized void addEntities(Collection<? extends GameEntity> entities, VisionType visionType) {
        if (entities == null || entities.isEmpty()) return;

        entities.forEach(this::addEntityDirectly);
        entities.forEach(entity -> {
            if (!(entity instanceof EntityGadget)) return;
            int owner = getGadgetOwner(entity);
            if (owner == 0) return;
            var parent = getEntityByConfigId(owner);
            if (parent instanceof EntityBaseGadget gadget) {
                gadget.getChildren().add(entity);
            }
            // TODO: what if parent spawn later?
        });

        chopped(entities.stream().toList(), 100).forEach(l -> {
            broadcastPacket(new PacketSceneEntityAppearNotify(l, visionType));
            l.forEach(x -> x.afterCreate(this.players));
        });
    }

    public GameEntity removeEntityDirectly(GameEntity entity) {
        val removed = this.entities.remove(entity.getId());
        Optional.ofNullable(removed).ifPresent(GameEntity::onRemoved); //Call entity remove event
        // an avatar leaves the scene on a switch, still alive: its bound entities die with it, on death
        if (removed != null && !(removed instanceof EntityAvatar)) {
            removed.killLifeBoundEntities();
        }

        //if(entity instanceof EntityWeapon) {
        //    Grasscutter.getLogger().warn("Weapon removed {}: ", entity.getId());
//
        //    for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
        //        Grasscutter.getLogger().warn(ste.toString());
        //    }
        //}
        return removed;
    }

    public void removeEntity(GameEntity entity) {
        removeEntity(entity, VisionType.VISION_DIE);
    }

    public synchronized void removeEntity(GameEntity entity, VisionType visionType) {
        Optional.ofNullable(removeEntityDirectly(entity)).ifPresent(removed ->
            broadcastPacket(new PacketSceneEntityDisappearNotify(removed, visionType)));
    }

    public synchronized void removeEntities(Collection<? extends GameEntity> entity, VisionType visionType) {
        val toRemove = entity.stream().filter(Objects::nonNull)
            .map(this::removeEntityDirectly).filter(Objects::nonNull)
            .toList();
        if (!toRemove.isEmpty()) {
            this.broadcastPacket(new PacketSceneEntityDisappearNotify(toRemove, visionType));
        }
    }

    public synchronized void replaceEntity(EntityAvatar oldEntity, EntityAvatar newEntity) {
        removeEntityDirectly(oldEntity);
        addEntityDirectly(newEntity);
        broadcastPacket(new PacketSceneEntityDisappearNotify(oldEntity, VisionType.VISION_REPLACE));
        broadcastPacket(new PacketSceneEntityAppearNotify(newEntity, VisionType.VISION_REPLACE, oldEntity.getId()));
    }

    /** Adds the avatar replacing one that died, which was already removed on death */
    public synchronized void replaceDeadAvatar(EntityAvatar deadEntity, EntityAvatar newEntity) {
        addEntityDirectly(newEntity);
        broadcastPacket(new PacketSceneEntityAppearNotify(newEntity, VisionType.VISION_REPLACE_DIE, deadEntity.getId()));
    }

    /** Adds a vehicle, kept by the world beyond this scene for some types: the vehicles it evicts are removed */
    public void addVehicle(EntityVehicle vehicle) {
        // e.g. the owner's previous skiff, or the oldest saurians over the limit
        val evicted = getWorld().getVehicleStore().add(vehicle);
        evicted.forEach(this::removeVehicle);

        addEntity(vehicle);
    }

    /** Removes a vehicle that's replaced, not killed: control returns to its members first */
    public void removeVehicle(EntityVehicle vehicle) {
        vehicle.ejectMembers();
        removeEntities(List.of(vehicle), VisionType.VISION_REMOVE);
    }

    /** The vehicles the player is in */
    public List<EntityVehicle> getVehiclesOf(Player player) {
        return this.entities.values().stream()
            .filter(EntityVehicle.class::isInstance).map(EntityVehicle.class::cast)
            .filter(vehicle -> vehicle.hasMember(player.getUid()))
            .toList();
    }

    /**
     * Synchronized like adding and removing entities: an entity removed meanwhile (e.g. by a group's load script) would
     * otherwise be shown again after its disappear notify, and linger on the client
     */
    public synchronized void showOtherEntities(Player player) {
        val entities = this.entities.values().stream().filter(entity ->
            entity != player.getTeamManager().getCurrentAvatarEntity()).toList();

        player.sendPacket(new PacketSceneEntityAppearNotify(entities, VisionType.VISION_MEET));
        entities.forEach(x -> x.afterCreate(List.of(player)));
    }

    public void handleAttack(AttackResult result) {
        //GameEntity attacker = getEntityById(result.getAttackerId());
        val target = getEntityById(result.getDefenseId());
        val attackType = ElementType.getTypeByValue(result.getElementType());
        if (target == null) return;

        // God-mode check
        if (target instanceof EntityAvatar entityAvatar) {
            if (entityAvatar.getPlayer().inGodmode()) return;

            if (result.getDamage() != result.getDamageShield()) { // when avatar actually have a shield
                //TODO check
                Optional.ofNullable(this.challenge).ifPresent(c ->
                    c.onDamageMonsterOrShield(getEntityById(result.getAttackerId()), result.getDamageShield() - result.getDamage()));
            }
        }
        target.damage(result.getDamage(), result.getAttackerId(), attackType);
        if (target instanceof EntityGadget gadget) {
            Optional.ofNullable(this.challenge).ifPresent(c -> c.onGadgetDamage(gadget));
        }
    }

    public void killEntity(GameEntity target) {
        killEntity(target, 0);
    }

    public void killEntity(GameEntity target, int attackerId) {
        GameEntity attacker = null;

        if (attackerId > 0) attacker = getEntityById(attackerId);

        if (attacker != null) {
            // Check codex
            if (attacker instanceof EntityClientGadget gadgetAttacker) {
                val clientGadgetOwner = getEntityById(gadgetAttacker.getOwnerEntityId());
                if (clientGadgetOwner instanceof EntityAvatar) {
                    gadgetAttacker.getOwner().getCodex().checkAnimal(target, CodexAnimalData.CountType.CODEX_COUNT_TYPE_KILL);
                }
            } else if (attacker instanceof EntityAvatar avatarAttacker) {
                avatarAttacker.getPlayer().getCodex().checkAnimal(target, CodexAnimalData.CountType.CODEX_COUNT_TYPE_KILL);
            }


        }

        // Packet
        broadcastPacket(new PacketLifeStateChangeNotify(attackerId, target, LifeState.LIFE_DEAD));

        // Remove entity from world
        removeEntity(target);

        // Death event
        target.onDeath(attackerId);
        // Reward drop
        if (target instanceof EntityMonster monster) {
            if (getSceneType() != SceneType.SCENE_DUNGEON && attacker != null) {
                getWorld().getServer().getDropSystem().callDrop(monster);
            }
            triggerDungeonEvent(DungeonPassConditionType.DUNGEON_COND_KILL_MONSTER_COUNT, ++this.killedMonsterCount);
        } else if (target instanceof EntityGadget gadget) {
            Optional.ofNullable(gadget.getGadgetData())
                .filter(data -> data.getType() == EntityType.Chest)
                .ifPresent(data -> this.killChestCount++);
        }
    }

    public void onTick() {
        // disable script for home
        if (getSceneType() == SceneType.SCENE_HOME_WORLD || getSceneType() == SceneType.SCENE_HOME_ROOM) {
            finishLoading();
            return;
        }
        if (this.scriptManager.isInit()) {
            //this.checkBlocks();
            if (this.tickCount % 2 == 0) {
                groupManager.tick();
            }
        } else {
            // TEMPORARY
            spawnManager.tick();
        }
        // Triggers
        this.regionManager.checkRegions();

        Optional.ofNullable(this.challenge).ifPresent(WorldChallenge::onCheckTimeOut);

        this.entities.values().forEach(e -> e.onTick(getSceneTimeSeconds()));

        platformManager.tick();
        scriptManager.tickTimeAxes();
        npcGroupManager.tick();
        tickWeatherAreas();
        finishLoading();
        respawnManager.tick();
        this.getSealBattleManager().onTick();
        this.tickCount++;
    }

    private void finishLoading() {
        if (this.finishedLoading) return;

        this.finishedLoading = true;
        this.afterLoadedCallbacks.forEach(Runnable::run);
        this.afterLoadedCallbacks.clear();
    }

    public void runWhenFinished(Runnable runnable) {
        if (this.finishedLoading) {
            runnable.run();
            return;
        }
        this.afterLoadedCallbacks.add(runnable);
    }

    // Gadgets

    public void onPlayerCreateGadget(EntityClientGadget gadget) {
        // Directly add
        addEntityDirectly(gadget);

        // Add to owner's gadget list
        gadget.getOwner().getTeamManager().getGadgets().add(gadget);

        // Optimization
        if (this.players.stream().anyMatch(p -> p != gadget.getOwner())) { // if there is other players in scene
            broadcastPacketToOthers(gadget.getOwner(), new PacketSceneEntityAppearNotify(gadget));
        }
    }

    public void onPlayerDestroyGadget(int entityId) {
        if (!(this.entities.get(entityId) instanceof EntityClientGadget gadget)) return;

        // Get and remove entity
        removeEntityDirectly(gadget);

        // Remove from owner's gadget list
        gadget.getOwner().getTeamManager().getGadgets().remove(gadget);

        // Optimization
        if (this.players.stream().anyMatch(p -> p != gadget.getOwner())) { // if there is other players in scene
            broadcastPacketToOthers(gadget.getOwner(), new PacketSceneEntityDisappearNotify(gadget, VisionType.VISION_DIE));
        }
    }

    /**
     * Send packets to the scene/worlds owner
     * */
    public boolean sendPacketHost(BasePacket packet) {
        val host = world.getHost();
        if(!players.contains(host)){
            return false;
        }
        world.getHost().sendPacket(packet);
        return true;
    }


    /**
     * Send packets to the players in targetUids
     * */
    public void sendPacketTargeted(BasePacket packet, List<Integer> targetUids) {
        players.stream()
            .filter(player -> targetUids.contains(player.getUid()))
            .forEach(player -> player.sendPacket(packet));
    }

    /**
     * Send packets to the players in targetUids
     * */
    public void sendPacketTargeted(BasePacket packet, int targetUid) {
        players.stream()
            .filter(player -> targetUid == player.getUid())
            .findFirst()
            .ifPresent(player -> player.sendPacket(packet));
    }

    // Broadcasting
    /**
     * Send packets to all players in world
     * */
    public void broadcastPacket(BasePacket packet) {
        // Send to all players - might have to check if player has been sent data packets
        broadcastPacketToOthers(null, packet);
    }

    /**
     * Send packets to all players in world excluding one, which normally is the owner
     * */
    public void broadcastPacketToOthers(Player excludedPlayer, BasePacket packet) {
        this.players.stream().filter(p -> p != excludedPlayer).forEach(p -> p.sendPacket(packet));
    }

    public void addItemEntity(int itemId, int amount, GameEntity bornForm) {
        val itemData = GameData.getItemDataMap().get(itemId);
        if (itemData == null) return;

        if (itemData.isEquip()) {
            val range = (1.5f + (.05f * amount));
            for (int i = 0; i < amount; i++) {
                val pos = bornForm.getPosition().nearby2d(range).addZ(.9f);  // Why Z?
                val createConfig = new CreateGadgetEntityConfig(itemData, 1)
                    .setBornPos(pos);
                addEntity(new EntityItem(this, createConfig));
            }
        } else {
            val createConfig = new CreateGadgetEntityConfig(itemData, amount)
                .setBornPos(bornForm.getPosition().clone().addZ(.9f));
            addEntity(new EntityItem(this, createConfig));
        }
    }

    public void loadGroupForQuest(List<QuestGroupSuite> sceneGroupSuite) {
        if (!this.scriptManager.isInit()) return;

        sceneGroupSuite.stream().filter(i -> this.scriptManager.getGroupById(i.getGroup()) != null)
            .forEach(i -> this.scriptManager.refreshGroup(i.getGroup(), i.getSuite(), false));
    }

    public void unlockForce(int force) {
        this.unlockedForces.add(force);
        broadcastPacket(new PacketSceneForceUnlockNotify(force, true));
    }

    public void lockForce(int force) {
        this.unlockedForces.remove(force);
        broadcastPacket(new PacketSceneForceLockNotify(force));
    }

    public void selectWorktopOptionWith(SelectWorktopOptionReq req) {
        val entity = getEntityById(req.getGadgetEntityId());
        Optional.ofNullable(entity)
            .filter(EntityGadget.class::isInstance).map(EntityGadget.class::cast)
            .map(EntityGadget::getContent)
            .filter(GadgetWorktop.class::isInstance).map(GadgetWorktop.class::cast)
            .filter(worktop -> worktop.onSelectWorktopOption(req))
            .ifPresent(worktop -> entity.getScene().removeEntity(entity, VisionType.VISION_REMOVE));
    }

    public void saveGroups() {
        this.scriptManager.getCachedGroupInstances().values().forEach(SceneGroupInstance::save);
    }

    public void saveSceneInstanceData() {
        this.sceneInstanceData.save();
    }

    public int getCurDungeonId(){
        return dungeonManager != null ? dungeonManager.getDungeonData().getId() : 0;
    }

    /**
     * The weather area at a position: the one turned on with the highest priority containing it, created if none
     * @return null if none
     */
    @Nullable
    public WeatherArea getWeatherArea(Position position) {
        val areaGrid = GameData.getSceneWeatherAreas().get(getId());
        if(areaGrid == null) return null;

        WeatherData found = null;
        // the areas of the position's grid tile, in the files' order
        for (val area : areaGrid.get(position)) {
            if (!area.isInside(position)) continue;
            val config = GameData.getWeatherDataMap().get(area.getArea_id());
            if (config == null || !isWeatherAreaEnabled(config) || !config.isUnderMaxHeight(position.getY())) continue;
            if (found == null || config.getPriority() > found.getPriority()) found = config;
        }
        if (found == null) return null;

        val config = found;
        return weatherAreas.computeIfAbsent(config.getAreaID(), id -> new WeatherArea(this, config));
    }

    /** On if turned on by a script or quest, else as its isDefaultValid */
    public boolean isWeatherAreaEnabled(WeatherData config) {
        val state = this.sceneInstanceData.getWeatherAreaState(config.getAreaID());
        return state != null ? state : config.isDefaultValid();
    }

    /**
     * Turns a weather area on or off, e.g. by SetWeatherAreaState: persisted per scene, the players change area on
     * their next weather check
     * @return False if the area doesn't exist
     */
    public boolean setWeatherAreaEnabled(int areaId, boolean on) {
        if (!GameData.getWeatherDataMap().containsKey(areaId)) return false;

        this.sceneInstanceData.setWeatherAreaState(areaId, on);
        Grasscutter.getLogger().debug("Weather area {} turned {} in scene {}", areaId, on ? "on" : "off", getId());
        if (!on) {
            val area = this.weatherAreas.remove(areaId);
            if (area != null) area.remove();
        }
        return true;
    }

    /** The weather areas' random changes, and the release of the ones empty for WEATHER_AREA_RELEASE_DELAY */
    private void tickWeatherAreas() {
        int sceneTime = getSceneTimeSeconds();
        this.weatherAreas.values().removeIf(area -> {
            boolean release = area.getPlayers().isEmpty()
                && area.getEmptySince() >= 0 && sceneTime - area.getEmptySince() >= WEATHER_AREA_RELEASE_DELAY;
            if (release) Grasscutter.getLogger().debug("Weather area {} released in scene {}, empty since {}", area.getAreaId(), getId(), area.getEmptySince());
            return release;
        });
        this.weatherAreas.values().forEach(area -> area.onTick(sceneTime));
    }
}
