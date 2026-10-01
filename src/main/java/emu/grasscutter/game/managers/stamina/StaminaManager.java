package emu.grasscutter.game.managers.stamina;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityAbilityProperty;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.BasePlayerManager;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.props.LifeState;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketAvatarLifeStateChangeNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import emu.grasscutter.server.packet.send.PacketLifeStateChangeNotify;
import emu.grasscutter.server.packet.send.PacketVehicleStaminaNotify;
import emu.grasscutter.utils.Position;
import lombok.val;
import org.anime_game_servers.multi_proto.gi.messages.gadget.VehicleInteractType;
import org.anime_game_servers.multi_proto.gi.messages.general.PlayerDieType;
import org.anime_game_servers.multi_proto.gi.messages.general.entity.EntityMoveInfo;
import org.anime_game_servers.multi_proto.gi.messages.scene.entity.MotionState;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.*;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

public class StaminaManager extends BasePlayerManager {

    // TODO: Skiff state detection?
    private static final Map<String, Set<MotionState>> MotionStatesCategorized = new HashMap<>() {{
        put("CLIMB", Set.of(
                MotionState.MOTION_CLIMB, // sustained, when not moving no cost no recover
                MotionState.MOTION_STANDBY_TO_CLIMB // NOT OBSERVED, see MOTION_JUMP_UP_WALL_FOR_STANDBY
        ));
        put("DASH", Set.of(
                MotionState.MOTION_DANGER_DASH, // sustained
                MotionState.MOTION_DASH // sustained
        ));
        put("FLY", Set.of(
                MotionState.MOTION_FLY, // sustained
                MotionState.MOTION_FLY_FAST, // sustained
                MotionState.MOTION_FLY_SLOW, // sustained
                MotionState.MOTION_POWERED_FLY // sustained, recover
        ));
        put("RUN", Set.of(
                MotionState.MOTION_DANGER_RUN, // sustained, recover
                MotionState.MOTION_RUN // sustained, recover
        ));
        put("SKIFF", Set.of(
                MotionState.MOTION_SKIFF_BOARDING, // NOT OBSERVED even when boarding
                MotionState.MOTION_SKIFF_DASH, // sustained, observed with waverider entity ID.
                MotionState.MOTION_SKIFF_NORMAL, // sustained, OBSERVED when both normal and dashing
                MotionState.MOTION_SKIFF_POWERED_DASH // sustained, recover
        ));
        put("STANDBY", Set.of(
                MotionState.MOTION_DANGER_STANDBY_MOVE, // sustained, recover
                MotionState.MOTION_DANGER_STANDBY, // sustained, recover
                MotionState.MOTION_LADDER_TO_STANDBY, // NOT OBSERVED
                MotionState.MOTION_STANDBY_MOVE, // sustained, recover
                MotionState.MOTION_STANDBY // sustained, recover
        ));
        put("SWIM", Set.of(
                MotionState.MOTION_SWIM_IDLE, // sustained
                MotionState.MOTION_SWIM_DASH, // immediate and sustained
                MotionState.MOTION_SWIM_JUMP, // NOT OBSERVED
                MotionState.MOTION_SWIM_MOVE // sustained
        ));
        put("WALK", Set.of(
                MotionState.MOTION_DANGER_WALK, // sustained, recover
                MotionState.MOTION_WALK // sustained, recover
        ));
        put("OTHER", Set.of(
                MotionState.MOTION_CLIMB_JUMP, // cost only once if repeated without switching state
                MotionState.MOTION_DASH_BEFORE_SHAKE, // immediate one time sprint charge.
                MotionState.MOTION_FIGHT, // immediate, if sustained then subsequent will be MOTION_NOTIFY
                MotionState.MOTION_JUMP_UP_WALL_FOR_STANDBY, // immediate, observed when RUN/WALK->CLIMB
                MotionState.MOTION_NOTIFY, // can be either cost or recover - check previous state and check skill casting
                MotionState.MOTION_SIT_IDLE, // sustained, recover
                MotionState.MOTION_JUMP // recover
        ));
        put("NOCOST_NORECOVER", Set.of(
                MotionState.MOTION_LADDER_SLIP, // NOT OBSERVED
                MotionState.MOTION_SLIP, // sustained, no cost no recover
                MotionState.MOTION_FLY_IDLE // NOT OBSERVED
        ));
        put("IGNORE", Set.of(
                // these states have no impact on stamina
                MotionState.MOTION_CROUCH_IDLE,
                MotionState.MOTION_CROUCH_MOVE,
                MotionState.MOTION_CROUCH_ROLL,
                MotionState.MOTION_DESTROY_VEHICLE,
                MotionState.MOTION_FALL_ON_GROUND,
                MotionState.MOTION_FOLLOW_ROUTE,
                MotionState.MOTION_FORCE_SET_POS,
                MotionState.MOTION_GO_UPSTAIRS,
                MotionState.MOTION_JUMP_OFF_WALL,
                MotionState.MOTION_LADDER_IDLE,
                MotionState.MOTION_LADDER_MOVE,
                MotionState.MOTION_LAND_SPEED,
                MotionState.MOTION_MOVE_FAIL_ACK,
                MotionState.MOTION_NONE,
                MotionState.MOTION_QUEST_FORCE_DRAG,
                MotionState.MOTION_RESET,
                MotionState.MOTION_STANDBY_TO_LADDER,
                MotionState.MOTION_WATERFALL
        ));
    }};

    private final Logger logger = Grasscutter.getLogger();
    public final static int GlobalCharacterMaximumStamina = PlayerProperty.PROP_MAX_STAMINA.getMax();
    public final static int GlobalVehicleMaxStamina = PlayerProperty.PROP_MAX_STAMINA.getMax();
    private Position currentCoordinates = new Position(0, 0, 0);
    private Position previousCoordinates = new Position(0, 0, 0);
    private MotionState currentState = MotionState.MOTION_STANDBY;
    private MotionState previousState = MotionState.MOTION_STANDBY;
    private Timer sustainedStaminaHandlerTimer;
    private GameSession cachedSession = null;
    private GameEntity cachedEntity = null;
    private int staminaRecoverDelay = 0;
    private int diveStaminaRecoverDelay = 0;
    private final HashMap<String, BeforeUpdateStaminaListener> beforeUpdateStaminaListeners = new HashMap<>();
    private final HashMap<String, AfterUpdateStaminaListener> afterUpdateStaminaListeners = new HashMap<>();
    private int vehicleId = -1;
    private int vehicleStamina = GlobalVehicleMaxStamina;
    private static final Set<MotionState> DiveDashStates = Set.of(MotionState.MOTION_DIVE_DASH, MotionState.MOTION_DIVE_SWIM_DASH);
    private static final float CostStaminaMixinInterval = 0.3f;  // s, hardcoded INTERVAL_TIME as in official server

    public static void initialize() {
        // TODO: Initialize foods etc.
    }

    public StaminaManager(Player player) {
        super(player);
    }

    // Accessors

    public int getMaxCharacterStamina() {
        return player.getProperty(PlayerProperty.PROP_MAX_STAMINA);
    }

    public int getCurrentCharacterStamina() {
        return player.getProperty(PlayerProperty.PROP_CUR_PERSIST_STAMINA);
    }

    public int getMaxDiveStamina() {
        return player.getProperty(PlayerProperty.PROP_MAX_DIVE_STAMINA);
    }

    public int getCurrentDiveStamina() {
        return player.getProperty(PlayerProperty.PROP_CUR_PERSIST_DIVE_STAMINA);
    }

    public int getMaxVehicleStamina() {
        return GlobalVehicleMaxStamina;
    }

    public int getCurrentVehicleStamina() {
        return vehicleStamina;
    }

    public boolean addCurrentStamina(int amount) {
        var cur = this.getCurrentCharacterStamina();
        var max = this.getMaxCharacterStamina();
        if (cur >= max) return false;
        var value = cur + amount;
        if (value > max)
            value = max;
        this.player.setProperty(PlayerProperty.PROP_CUR_PERSIST_STAMINA, value);
        return true;
    }

    public boolean registerBeforeUpdateStaminaListener(String listenerName, BeforeUpdateStaminaListener listener) {
        if (beforeUpdateStaminaListeners.containsKey(listenerName)) {
            return false;
        }
        beforeUpdateStaminaListeners.put(listenerName, listener);
        return true;
    }

    public boolean unregisterBeforeUpdateStaminaListener(String listenerName) {
        if (!beforeUpdateStaminaListeners.containsKey(listenerName)) {
            return false;
        }
        beforeUpdateStaminaListeners.remove(listenerName);
        return true;
    }

    public boolean registerAfterUpdateStaminaListener(String listenerName, AfterUpdateStaminaListener listener) {
        if (afterUpdateStaminaListeners.containsKey(listenerName)) {
            return false;
        }
        afterUpdateStaminaListeners.put(listenerName, listener);
        return true;
    }

    public boolean unregisterAfterUpdateStaminaListener(String listenerName) {
        if (!afterUpdateStaminaListeners.containsKey(listenerName)) {
            return false;
        }
        afterUpdateStaminaListeners.remove(listenerName);
        return true;
    }

    private boolean isPlayerMoving() {
        float diffX = currentCoordinates.getX() - previousCoordinates.getX();
        float diffY = currentCoordinates.getY() - previousCoordinates.getY();
        float diffZ = currentCoordinates.getZ() - previousCoordinates.getZ();
        logger.trace("isPlayerMoving: " + previousCoordinates + ", " + currentCoordinates +
                ", " + diffX + ", " + diffY + ", " + diffZ);
        return Math.abs(diffX) > 0.3 || Math.abs(diffY) > 0.2 || Math.abs(diffZ) > 0.3;
    }

    public int updateStaminaRelative(GameSession session, Consumption consumption, boolean isCharacterStamina) {
        int currentStamina = isCharacterStamina ? getCurrentCharacterStamina() : getCurrentVehicleStamina();
        if (consumption.amount == 0) {
            return currentStamina;
        }
        // notify will update
        for (Map.Entry<String, BeforeUpdateStaminaListener> listener : beforeUpdateStaminaListeners.entrySet()) {
            Consumption overriddenConsumption = listener.getValue().onBeforeUpdateStamina(consumption.type.toString(), consumption, isCharacterStamina);
            if ((overriddenConsumption.type != consumption.type) && (overriddenConsumption.amount != consumption.amount)) {
                logger.debug("Stamina update relative(" +
                        consumption.type.toString() + ", " + consumption.amount + ") overridden to relative(" +
                        consumption.type.toString() + ", " + consumption.amount + ") by: " + listener.getKey());
                return currentStamina;
            }
        }
        int maxStamina = isCharacterStamina ? getMaxCharacterStamina() : getMaxVehicleStamina();
        logger.trace((isCharacterStamina ? "C " : "V ") + currentStamina + "/" + maxStamina + "\t" + currentState + "\t" +
                (isPlayerMoving() ? "moving" : "      ") + "\t(" + consumption.type + "," +
                consumption.amount + ")");
        int newStamina = currentStamina + consumption.amount;
        if (newStamina < 0) {
            newStamina = 0;
        } else if (newStamina > maxStamina) {
            newStamina = maxStamina;
        }
        return setStamina(session, consumption.type.toString(), newStamina, isCharacterStamina);
    }

    public int updateStaminaAbsolute(GameSession session, String reason, int newStamina, boolean isCharacterStamina) {
        int currentStamina = isCharacterStamina ? getCurrentCharacterStamina() : getCurrentVehicleStamina();
        // notify will update
        for (Map.Entry<String, BeforeUpdateStaminaListener> listener : beforeUpdateStaminaListeners.entrySet()) {
            int overriddenNewStamina = listener.getValue().onBeforeUpdateStamina(reason, newStamina, isCharacterStamina);
            if (overriddenNewStamina != newStamina) {
                logger.debug("Stamina update absolute(" +
                        reason + ", " + newStamina + ") overridden to absolute(" +
                        reason + ", " + newStamina + ") by: " + listener.getKey());
                return currentStamina;
            }
        }
        int maxStamina = isCharacterStamina ? getMaxCharacterStamina() : getMaxVehicleStamina();
        if (newStamina < 0) {
            newStamina = 0;
        } else if (newStamina > maxStamina) {
            newStamina = maxStamina;
        }
        return setStamina(session, reason, newStamina, isCharacterStamina);
    }

    // Returns new stamina and sends PlayerPropNotify or VehicleStaminaNotify
    public int setStamina(GameSession session, String reason, int newStamina, boolean isCharacterStamina) {
        // Target Player
        if (!GAME_OPTIONS.staminaUsage || session.getPlayer().getUnlimitedStamina()) {
            newStamina = getMaxCharacterStamina();
        }

        // set stamina if is character stamina
        if (isCharacterStamina) {
            player.setProperty(PlayerProperty.PROP_CUR_PERSIST_STAMINA, newStamina);
        } else {
            vehicleStamina = newStamina;
            session.send(new PacketVehicleStaminaNotify(vehicleId, ((float) newStamina) / 100));
        }
        // notify updated
        int s = newStamina;
        afterUpdateStaminaListeners.forEach((k, v) -> v.onAfterUpdateStamina(reason, s, isCharacterStamina));
        return newStamina;
    }

    // Kills avatar, removes entity and sends notification.
    // TODO: Probably move this to Avatar class? since other components may also need to kill avatar.
    public void killAvatar(GameSession session, GameEntity entity, PlayerDieType dieType) {
        session.send(new PacketAvatarLifeStateChangeNotify(player.getTeamManager().getCurrentAvatarEntity().getAvatar(),
                LifeState.LIFE_DEAD, dieType));
        session.send(new PacketLifeStateChangeNotify(entity, LifeState.LIFE_DEAD, dieType));
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0);
        entity.getWorld().broadcastPacket(new PacketEntityFightPropUpdateNotify(entity, FightProperty.FIGHT_PROP_CUR_HP));
        entity.getWorld().broadcastPacket(new PacketLifeStateChangeNotify(0, entity, LifeState.LIFE_DEAD));
        player.getScene().removeEntity(entity);
        ((EntityAvatar) entity).onDeath(dieType, 0);
    }

    public void startSustainedStaminaHandler() {
        if (!player.isPaused() && sustainedStaminaHandlerTimer == null) {
            sustainedStaminaHandlerTimer = new Timer();
            sustainedStaminaHandlerTimer.scheduleAtFixedRate(new SustainedStaminaHandler(), 0, 200);
            logger.debug("[MovementManager] SustainedStaminaHandlerTimer started");
        }
    }

    public void stopSustainedStaminaHandler() {
        if (sustainedStaminaHandlerTimer != null) {
            sustainedStaminaHandlerTimer.cancel();
            sustainedStaminaHandlerTimer = null;
            logger.debug("[MovementManager] SustainedStaminaHandlerTimer stopped");
        }
    }

    // Handlers

    // External trigger handler

    // One-off cost from ability action AvatarSkillStart, in stamina points (e.g. 25 for sword charged attack)
    public void handleSkillStartCostStamina(int skillId, float cost) {
        costFightStamina("Skill " + skillId, cost);
    }

    // Sustained cost from ability mixin CostStaminaMixin (e.g. claymore charged attack), in stamina points per second.
    // The mixin is dedicated to sustained cost: the client invokes it repeatedly while active,
    // each invoke charges a fixed interval, as the official server does.
    public void handleCostStaminaMixin(float costPerSecond) {
        costFightStamina("CostStaminaMixin", costPerSecond * CostStaminaMixinInterval);
    }

    private void costFightStamina(String reason, float cost) {
        if (cost <= 0) return;
        int amount = Math.round(cost * 100 * getStaminaCostRatio());
        logger.debug("{} costs stamina {}", reason, amount);
        staminaRecoverDelay = 0;
        updateStaminaRelative(player.getSession(), new Consumption(ConsumptionType.FIGHT, -amount), true);
    }

    public void handleCombatInvocationsNotify(@NotNull GameSession session, @NotNull EntityMoveInfo moveInfo, @NotNull GameEntity entity) {
        // cache info for later use in SustainedStaminaHandler tick
        cachedSession = session;
        cachedEntity = entity;
        val motionInfo = moveInfo.getMotionInfo();
        val motionState = motionInfo.getState();
        int notifyEntityId = entity.getId();
        int currentAvatarEntityId = session.getPlayer().getTeamManager().getCurrentAvatarEntity().getId();
        if (notifyEntityId != currentAvatarEntityId && notifyEntityId != vehicleId) {
            return;
        }
        this.previousState = currentState;
        this.currentState = motionState;
        // logger.trace(currentState + "\t" + (notifyEntityId == currentAvatarEntityId ? "character" : "vehicle"));
        val posVector = motionInfo.getPos();
        if (posVector != null){
            Position newPos = new Position(posVector);
            if (newPos.getX() != 0 && newPos.getY() != 0 && newPos.getZ() != 0) {
                currentCoordinates = newPos;
            }
        }
        startSustainedStaminaHandler();
        handleImmediateStamina(session, motionState);
    }

    public void handleVehicleInteractReq(GameSession session, int vehicleId, VehicleInteractType vehicleInteractType) {
        if (vehicleInteractType == VehicleInteractType.VEHICLE_INTERACT_IN) {
            this.vehicleId = vehicleId;
            // Reset character stamina here to prevent falling into water immediately on ejection if char stamina is
            //      close to empty when boarding.
            updateStaminaAbsolute(session, "board vehicle", getMaxCharacterStamina(), true);
            updateStaminaAbsolute(session, "board vehicle", getMaxVehicleStamina(), false);
        } else {
            this.vehicleId = -1;
        }
    }

    // Internal handler

    private void handleImmediateStamina(GameSession session, @NotNull MotionState motionState) {
        // Don't double dip on sustained stamina start costs
        if (previousState == currentState) {
            return;
        }
        if (DiveDashStates.contains(motionState)) {
            updateDiveStaminaRelative(ConsumptionType.DIVE_DASH_START.amount);
            return;
        }
        if (currentState == motionState) {
            if (motionState.equals(MotionState.MOTION_CLIMB_JUMP)) {
                updateStaminaRelative(session, new Consumption(ConsumptionType.CLIMB_JUMP), true);
            }
            return;
        }
        switch (motionState) {
            case MOTION_CLIMB ->
                    updateStaminaRelative(session, new Consumption(ConsumptionType.CLIMB_START), true);
            case MOTION_DASH_BEFORE_SHAKE ->
                    updateStaminaRelative(session, new Consumption(ConsumptionType.SPRINT), true);
            case MOTION_CLIMB_JUMP ->
                    updateStaminaRelative(session, new Consumption(ConsumptionType.CLIMB_JUMP), true);
            case MOTION_SWIM_DASH ->
                    updateStaminaRelative(session, new Consumption(ConsumptionType.SWIM_DASH_START), true);
            default -> {}
        }
    }

    // Dive stamina is a separate pool: dashing underwater costs it, it recovers otherwise,
    // 1 second (5 ticks) after leaving a dash or a dolphin jump
    private void updateSustainedDiveStamina() {
        if (DiveDashStates.contains(currentState)) {
            diveStaminaRecoverDelay = 0;
            updateDiveStaminaRelative(ConsumptionType.DIVE_DASH.amount);
            return;
        }
        if (currentState == MotionState.MOTION_DIVE_DOLPHIN) {
            diveStaminaRecoverDelay = 0;
            return;
        }
        if (diveStaminaRecoverDelay < 5) {
            diveStaminaRecoverDelay++;
            return;
        }
        updateDiveStaminaRelative(ConsumptionType.DIVE_RECOVER.amount);
    }

    private void updateDiveStaminaRelative(int amount) {
        int max = getMaxDiveStamina();
        int current = getCurrentDiveStamina();
        int value = Math.max(0, Math.min(max, current + amount));
        if (!GAME_OPTIONS.staminaUsage || player.getUnlimitedStamina()) {
            value = max;
        }
        if (value == current) return;
        player.setProperty(PlayerProperty.PROP_CUR_PERSIST_DIVE_STAMINA, value);
    }

    private class SustainedStaminaHandler extends TimerTask {
        public void run() {
            updateSustainedDiveStamina();
            boolean moving = isPlayerMoving();
            int currentCharacterStamina = getCurrentCharacterStamina();
            int maxCharacterStamina = getMaxCharacterStamina();
            int currentVehicleStamina = getCurrentVehicleStamina();
            int maxVehicleStamina = getMaxVehicleStamina();
            if (moving || (currentCharacterStamina < maxCharacterStamina) || (currentVehicleStamina < maxVehicleStamina)) {
                logger.trace("Player moving: " + moving + ", stamina full: " +
                        (currentCharacterStamina >= maxCharacterStamina) + ", recalculate stamina");
                boolean isCharacterStamina = true;
                Consumption consumption;
                if (MotionStatesCategorized.get("CLIMB").contains(currentState)) {
                    consumption = getClimbConsumption();
                } else if (MotionStatesCategorized.get("DASH").contains(currentState)) {
                    consumption = getDashConsumption();
                } else if (MotionStatesCategorized.get("FLY").contains(currentState)) {
                    consumption = getFlyConsumption();
                } else if (MotionStatesCategorized.get("RUN").contains(currentState)) {
                    consumption = new Consumption(ConsumptionType.RUN);
                } else if (MotionStatesCategorized.get("SKIFF").contains(currentState)) {
                    consumption = getSkiffConsumption();
                    isCharacterStamina = false;
                } else if (MotionStatesCategorized.get("STANDBY").contains(currentState)) {
                    consumption = new Consumption(ConsumptionType.STANDBY);
                } else if (MotionStatesCategorized.get("SWIM").contains(currentState)) {
                    consumption = getSwimConsumptions();
                } else if (MotionStatesCategorized.get("WALK").contains(currentState)) {
                    consumption = new Consumption(ConsumptionType.WALK);
                } else if (MotionStatesCategorized.get("NOCOST_NORECOVER").contains(currentState)) {
                    consumption = new Consumption();
                } else if (MotionStatesCategorized.get("OTHER").contains(currentState)) {
                    consumption = new Consumption();
                } else { // ignore
                    return;
                }

                logger.debug("Consume stamina {} with state {}", consumption.amount, currentState);
                // Delay 1 seconds before starts recovering stamina
                if (consumption.amount != 0 && cachedSession != null) {
                    if (consumption.amount < 0) {
                        staminaRecoverDelay = 0;
                    }
                    if (consumption.amount > 0
                            && consumption.type != ConsumptionType.POWERED_FLY
                            && consumption.type != ConsumptionType.POWERED_SKIFF) {
                        // For POWERED_* recover immediately - things like Amber's gliding exam and skiff challenges may require this.
                        if (staminaRecoverDelay < 5) {
                            // For others recover after 1 seconds (5 ticks) - as official server does.
                            staminaRecoverDelay++;
                            consumption.amount = 0;
                            logger.trace("Delaying recovery: " + staminaRecoverDelay);
                        }
                    }
                    updateStaminaRelative(cachedSession, consumption, isCharacterStamina);
                }
            }
            previousState = currentState;
            previousCoordinates = currentCoordinates.clone();
        }
    }

    private void handleDrowning() {
        // TODO: fix drowning waverider entity
        int stamina = getCurrentCharacterStamina();
        if (stamina < 10) {
            logger.trace(getCurrentCharacterStamina() + "/" +
                    getMaxCharacterStamina() + "\t" + currentState);
            if (currentState != MotionState.MOTION_SWIM_IDLE) {
                killAvatar(cachedSession, cachedEntity, PlayerDieType.PLAYER_DIE_DRAWN);
            }
        }
    }

    // Consumption Calculators
    // Stamina Consumption Reduction: https://genshin-impact.fandom.com/wiki/Stamina
    private float getStaminaCostRatio() {
        var entity = player.getTeamManager().getCurrentAvatarEntity();
        var ratio = entity.getAbilityProperty(EntityAbilityProperty.Actor_CostStaminaRatio);
        var value = Math.max(0f, 1f + ratio);  // e.g. Freeze uses -999 to cancel the cost
        logger.info("Current stamina cost ratio: {}", value);
        return value;
    }

    private Consumption getClimbConsumption() {
        Consumption consumption = new Consumption();
        if (currentState == MotionState.MOTION_CLIMB && isPlayerMoving()) {
            consumption.type = ConsumptionType.CLIMBING;
            consumption.amount = ConsumptionType.CLIMBING.amount;
        }
        consumption.amount *= getStaminaCostRatio();
        return consumption;
    }

    private Consumption getSwimConsumptions() {
        handleDrowning();
        Consumption consumption = new Consumption();
        if (currentState == MotionState.MOTION_SWIM_MOVE) {
            consumption.type = ConsumptionType.SWIMMING;
            consumption.amount = ConsumptionType.SWIMMING.amount;
        }
        if (currentState == MotionState.MOTION_SWIM_DASH) {
            consumption.type = ConsumptionType.SWIM_DASH;
            consumption.amount = ConsumptionType.SWIM_DASH.amount;
        }
        consumption.amount *= getStaminaCostRatio();
        return consumption;
    }

    private Consumption getDashConsumption() {
        Consumption consumption = new Consumption();
        if (currentState == MotionState.MOTION_DASH) {
            consumption.type = ConsumptionType.DASH;
            consumption.amount = ConsumptionType.DASH.amount;
            consumption.amount *= getStaminaCostRatio();
        }
        return consumption;
    }

    private Consumption getFlyConsumption() {
        // POWERED_FLY, e.g. wind tunnel
        if (currentState == MotionState.MOTION_POWERED_FLY) {
            return new Consumption(ConsumptionType.POWERED_FLY);
        }
        Consumption consumption = new Consumption(ConsumptionType.FLY);
        consumption.amount *= getStaminaCostRatio();
        return consumption;
    }

    private Consumption getSkiffConsumption() {
        // No known reduction for skiffing.
        return switch (currentState) {
            case MOTION_SKIFF_DASH -> new Consumption(ConsumptionType.SKIFF_DASH);
            case MOTION_SKIFF_POWERED_DASH -> new Consumption(ConsumptionType.POWERED_SKIFF);
            case MOTION_SKIFF_NORMAL -> new Consumption(ConsumptionType.SKIFF);
            default -> new Consumption();
        };
    }
}
