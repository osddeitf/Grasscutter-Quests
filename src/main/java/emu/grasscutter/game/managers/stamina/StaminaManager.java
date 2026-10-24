package emu.grasscutter.game.managers.stamina;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityAbilityProperty;
import emu.grasscutter.game.entity.EntityVehicle;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.BasePlayerManager;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.props.LifeState;
import emu.grasscutter.game.props.PlayerProperty;
import teyvat.ability.config.enums.PhlogistonChangeType;
import emu.grasscutter.server.packet.send.PacketAvatarLifeStateChangeNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import emu.grasscutter.server.packet.send.PacketLifeStateChangeNotify;
import emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify;
import emu.grasscutter.server.packet.send.PacketVehiclePhlogistonNotify;
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
    private GameEntity cachedEntity = null;
    private int staminaRecoverDelay = 0;
    private int diveStaminaRecoverDelay = 0;
    private final HashMap<String, BeforeUpdateStaminaListener> beforeUpdateStaminaListeners = new HashMap<>();
    private final HashMap<String, AfterUpdateStaminaListener> afterUpdateStaminaListeners = new HashMap<>();
    private int vehicleId = -1;
    private int vehicleStamina = GlobalVehicleMaxStamina;
    private static final Set<MotionState> DiveDashStates = Set.of(MotionState.MOTION_DIVE_DASH, MotionState.MOTION_DIVE_SWIM_DASH);
    private static final float CostStaminaMixinInterval = 0.3f;  // s, hardcoded INTERVAL_TIME as in official server
    private static final int SustainedTickInterval = 200;  // ms, the sustained stamina timer's period
    private static final int TicksPerSecond = 1000 / SustainedTickInterval;

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

    public int updateStaminaRelative(Consumption consumption, boolean isCharacterStamina) {
        int currentStamina = isCharacterStamina ? getCurrentCharacterStamina() : getCurrentVehicleStamina();
        if (consumption.amount == 0) {
            return currentStamina;
        }
        // Costs only, not recovering, and the avatar's ability property doesn't apply to a vehicle.
        // Actor_CostStaminaDelta is not supported, as no ability has it
        float ratio = 1;
        if (isCharacterStamina && consumption.amount < 0) {
            ratio = getStaminaCostRatio(EntityAbilityProperty.Actor_CostStaminaRatio);
            consumption.amount = Math.round(consumption.amount * ratio);
        }

        // notify will update
        for (Map.Entry<String, BeforeUpdateStaminaListener> listener : beforeUpdateStaminaListeners.entrySet()) {
            Consumption overriddenConsumption = listener.getValue().onBeforeUpdateStamina(consumption.type.toString(), consumption, isCharacterStamina);
            if ((overriddenConsumption.type != consumption.type) && (overriddenConsumption.amount != consumption.amount)) {
                logger.debug("Stamina update relative ({}, {}) overridden to ({}, {}) by {}",
                    consumption.type, consumption.amount, overriddenConsumption.type, overriddenConsumption.amount, listener.getKey());
                return currentStamina;
            }
        }
        int maxStamina = isCharacterStamina ? getMaxCharacterStamina() : getMaxVehicleStamina();
        int newStamina = currentStamina + consumption.amount;
        if (newStamina < 0) {
            newStamina = 0;
        } else if (newStamina > maxStamina) {
            newStamina = maxStamina;
        }
        if (logger.isTraceEnabled()) {  // isPlayerMoving() isn't free
            logger.trace("{} stamina {} {}{}: {} -> {}/{}, {}{}",
                isCharacterStamina ? "Character" : "Vehicle", consumption.type, consumption.amount,
                ratio != 1 ? " (ratio " + ratio + ")" : "", currentStamina, newStamina, maxStamina,
                currentState, isPlayerMoving() ? ", moving" : "");
        }
        return setStamina(consumption.type.toString(), newStamina, isCharacterStamina);
    }

    public int updateStaminaAbsolute(String reason, int newStamina, boolean isCharacterStamina) {
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
        return setStamina(reason, newStamina, isCharacterStamina);
    }

    // Returns new stamina and sends PlayerPropNotify or VehicleStaminaNotify
    public int setStamina(String reason, int newStamina, boolean isCharacterStamina) {
        // Target Player
        if (!GAME_OPTIONS.staminaUsage || player.getUnlimitedStamina()) {
            newStamina = getMaxCharacterStamina();
        }

        // set stamina if is character stamina
        if (isCharacterStamina) {
            player.setProperty(PlayerProperty.PROP_CUR_PERSIST_STAMINA, newStamina);
        } else {
            vehicleStamina = newStamina;
            player.sendPacket(new PacketVehicleStaminaNotify(vehicleId, ((float) newStamina) / 100));
        }
        // notify updated
        int s = newStamina;
        afterUpdateStaminaListeners.forEach((k, v) -> v.onAfterUpdateStamina(reason, s, isCharacterStamina));
        return newStamina;
    }

    // Kills avatar, removes entity and sends notification.
    // TODO: Probably move this to Avatar class? since other components may also need to kill avatar.
    public void killAvatar(GameEntity entity, PlayerDieType dieType) {
        player.sendPacket(new PacketAvatarLifeStateChangeNotify(player.getTeamManager().getCurrentAvatarEntity().getAvatar(),
                LifeState.LIFE_DEAD, dieType));
        player.sendPacket(new PacketLifeStateChangeNotify(entity, LifeState.LIFE_DEAD, dieType));
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0);
        entity.getWorld().broadcastPacket(new PacketEntityFightPropUpdateNotify(entity, FightProperty.FIGHT_PROP_CUR_HP));
        entity.getWorld().broadcastPacket(new PacketLifeStateChangeNotify(0, entity, LifeState.LIFE_DEAD));
        player.getScene().removeEntity(entity);
        ((EntityAvatar) entity).onDeath(dieType, 0);
    }

    public void startSustainedStaminaHandler() {
        if (!player.isPaused() && sustainedStaminaHandlerTimer == null) {
            sustainedStaminaHandlerTimer = new Timer();
            sustainedStaminaHandlerTimer.scheduleAtFixedRate(new SustainedStaminaHandler(), 0, SustainedTickInterval);
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

    // One-off cost from ability action DeductStamina, in stamina points (e.g. a special jump, or a monster's hit)
    public void handleDeductStamina(float cost) {
        costFightStamina("DeductStamina", cost);
    }

    /** The reverse of DeductStamina, e.g. Mavuika's and Xilonen's special sprints: not delaying the stamina recovery */
    public void handleReviveStamina(float value) {
        if (value <= 0) return;
        int amount = Math.round(value * 100);
        logger.debug("ReviveStamina revives stamina {}", amount);
        updateStaminaRelative(new Consumption(ConsumptionType.FIGHT, amount), true);
    }

    // Phlogiston lives in the team's server global value, in points; the player property (hundredths) persists it
    public static final String PHLOGISTON_SGV = "SGV_PlayerTeam_Phlogiston";
    public static final float MAX_PHLOGISTON = PlayerProperty.PROP_CUR_PHLOGISTON.getMax() / 100f;

    public float getCurrentPhlogiston() {
        var team = player.getTeamManager().getEntity();
        Float value = team == null ? null : (Float) team.getGlobalAbilityValues().get(PHLOGISTON_SGV);
        return value != null ? value : player.getProperty(PlayerProperty.PROP_CUR_PHLOGISTON) / 100f;
    }

    // One-off phlogiston change from ability action ChangePhlogiston, in points, on the entity it runs on
    public void handleChangePhlogiston(GameEntity<?> entity, PhlogistonChangeType changeType, float points) {
        changePhlogiston(entity, changeType, points);
    }

    // Sustained phlogiston change from ability mixin ChangePhlogistonMixin, speed in points per second.
    // Like CostStaminaMixin, the client invokes it repeatedly while active, each invoke changes by one tick interval
    public void handleChangePhlogistonMixin(GameEntity<?> entity, PhlogistonChangeType changeType, float speed, float tickInterval) {
        if (tickInterval <= 0) {
            logger.warn("ChangePhlogistonMixin without a tick interval: {}", tickInterval);
            return;
        }
        changePhlogiston(entity, changeType, speed * tickInterval);
    }

    // The changed phlogiston, unclamped
    private static float changedPhlogiston(PhlogistonChangeType changeType, float current, float points) {
        // TODO: Actor_CostPhlogistonRatio (cost reduction) isn't supported yet, only activity abilities use it (e.g. V5_5_AtkDfs)
        return switch (changeType) {
            case Cost -> current - points;
            case Add -> current + points;
            case Set -> points;
        };
    }

    private void changePhlogiston(GameEntity<?> entity, PhlogistonChangeType changeType, float points) {
        // On a vehicle (e.g. an indwelled saurian) its own phlogiston changes instead
        if (entity instanceof EntityVehicle vehicle) {
            changeVehiclePhlogiston(vehicle, changeType, points);
        } else {
            changeTeamPhlogiston(changeType, points);
        }
    }

    private void changeTeamPhlogiston(PhlogistonChangeType changeType, float points) {
        var team = player.getTeamManager().getEntity();
        if (team == null) return;

        float current = getCurrentPhlogiston();
        float value = Math.max(0f, Math.min(MAX_PHLOGISTON, changedPhlogiston(changeType, current, points)));
        if (value == current) return;
        logger.debug("Phlogiston {} {}: {} -> {}", changeType, points, current, value);

        team.getGlobalAbilityValues().put(PHLOGISTON_SGV, value);
        player.getScene().broadcastPacket(new PacketServerGlobalValueChangeNotify(team, PHLOGISTON_SGV, value));
        player.setProperty(PlayerProperty.PROP_CUR_PHLOGISTON, Math.round(value * 100), false);
    }

    // The excess handling is assumed
    private void changeVehiclePhlogiston(EntityVehicle vehicle, PhlogistonChangeType changeType, float points) {
        float current = vehicle.getCurPhlogiston();
        // A Cost on an empty vehicle is taken from the team's phlogiston
        if (current <= 0 && changeType == PhlogistonChangeType.Cost) {
            changeTeamPhlogiston(changeType, points);
            return;
        }

        float changed = changedPhlogiston(changeType, current, points);
        float value = Math.max(0f, Math.min(EntityVehicle.MAX_PHLOGISTON, changed));
        if (value != current) {
            logger.debug("Vehicle {} phlogiston {} {}: {} -> {}", vehicle.getId(), changeType, points, current, value);
            vehicle.setCurPhlogiston(value);
            player.getScene().broadcastPacket(new PacketVehiclePhlogistonNotify(vehicle.getId(), value));
        }

        if (changeType == PhlogistonChangeType.Set) return;
        // An Add overflowing the vehicle goes to the team's phlogiston, a Cost beyond what the vehicle has left is discarded
        float excess = changed - value;
        if (excess > 0) {
            changeTeamPhlogiston(PhlogistonChangeType.Add, excess);
        }
    }

    // Sustained cost from ability mixin CostStaminaMixin (e.g. claymore charged attack), in stamina points per second.
    // The mixin is dedicated to sustained cost: the client invokes it repeatedly while active,
    // each invoke charges a fixed interval, as the official server does.
    public void handleCostStaminaMixin(float costPerSecond) {
        costFightStamina("CostStaminaMixin", costPerSecond * CostStaminaMixinInterval);
    }

    private void costFightStamina(String reason, float cost) {
        if (cost <= 0) return;
        int amount = Math.round(cost * 100);
        logger.debug("{} costs stamina {}", reason, amount);
        staminaRecoverDelay = 0;
        updateStaminaRelative(new Consumption(ConsumptionType.FIGHT, -amount), true);
    }

    public void handleCombatInvocationsNotify(@NotNull EntityMoveInfo moveInfo, @NotNull GameEntity entity) {
        // cache info for later use in SustainedStaminaHandler tick
        cachedEntity = entity;
        val motionInfo = moveInfo.getMotionInfo();
        val motionState = motionInfo.getState();
        int notifyEntityId = entity.getId();
        int currentAvatarEntityId = player.getTeamManager().getCurrentAvatarEntity().getId();
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
        handleImmediateStamina(motionState);
    }

    public void handleVehicleInteractReq(int vehicleId, VehicleInteractType vehicleInteractType) {
        if (vehicleInteractType == VehicleInteractType.VEHICLE_INTERACT_IN) {
            this.vehicleId = vehicleId;
            // Reset character stamina here to prevent falling into water immediately on ejection if char stamina is
            //      close to empty when boarding.
            updateStaminaAbsolute("board vehicle", getMaxCharacterStamina(), true);
            updateStaminaAbsolute("board vehicle", getMaxVehicleStamina(), false);
        } else {
            this.vehicleId = -1;
        }
    }

    // Internal handler

    // Start costs, charged once when entering a state (motionState is already the current state)
    private void handleImmediateStamina(@NotNull MotionState motionState) {
        // Don't double dip on sustained stamina start costs
        if (previousState == currentState) {
            return;
        }
        if (DiveDashStates.contains(motionState)) {
            updateDiveStaminaRelative(ConsumptionType.DIVE_DASH_START.amount);
            return;
        }
        var consumption = switch (motionState) {
            case MOTION_CLIMB -> new Consumption(ConsumptionType.CLIMB_START);
            case MOTION_DASH_BEFORE_SHAKE -> new Consumption(ConsumptionType.SPRINT,
                costWithDelta(ConsumptionType.SPRINT.amount, getStaminaCostDelta(EntityAbilityProperty.Actor_CostDashBsStaminaDelta)));
            case MOTION_CLIMB_JUMP -> new Consumption(ConsumptionType.CLIMB_JUMP);
            case MOTION_SWIM_DASH -> new Consumption(ConsumptionType.SWIM_DASH_START);
            default -> null;
        };
        if (consumption == null) return;

        // delay recovering, like other costs
        staminaRecoverDelay = 0;
        updateStaminaRelative(consumption, true);
    }

    // Dive stamina is a separate pool: dashing underwater costs it, it recovers otherwise,
    // 1 second after leaving a dash or a dolphin jump
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
        if (diveStaminaRecoverDelay < TicksPerSecond) {
            diveStaminaRecoverDelay++;
            return;
        }
        updateDiveStaminaRelative(ConsumptionType.DIVE_RECOVER.amount);
    }

    private void updateDiveStaminaRelative(int amount) {
        int max = getMaxDiveStamina();
        int current = getCurrentDiveStamina();
        if (amount < 0) {
            amount = Math.round(amount * getStaminaCostRatio(EntityAbilityProperty.Actor_CostDiveStaminaRatio));  // costs only, not recovering
        }
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

                // Delay 1 seconds before starts recovering stamina
                if (consumption.amount != 0 && cachedEntity != null) {
                    if (consumption.amount < 0) {
                        staminaRecoverDelay = 0;
                    }
                    if (consumption.amount > 0
                            && consumption.type != ConsumptionType.POWERED_FLY
                            && consumption.type != ConsumptionType.POWERED_SKIFF) {
                        // For POWERED_* recover immediately - things like Amber's gliding exam and skiff challenges may require this.
                        if (staminaRecoverDelay < TicksPerSecond) {
                            // For others recover after 1 second - as official server does.
                            staminaRecoverDelay++;
                            consumption.amount = 0;
                            logger.trace("Delaying recovery: " + staminaRecoverDelay);
                        }
                    }
                    updateStaminaRelative(consumption, isCharacterStamina);
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
                killAvatar(cachedEntity, PlayerDieType.PLAYER_DIE_DRAWN);
            }
        }
    }

    // Consumption Calculators
    // Stamina Consumption Reduction: https://genshin-impact.fandom.com/wiki/Stamina
    /** A cost ratio from the current avatar's abilities, e.g. Freeze uses -999 to cancel the cost */
    private float getStaminaCostRatio(EntityAbilityProperty property) {
        var entity = player.getTeamManager().getCurrentAvatarEntity();
        return Math.max(0f, 1f + entity.getAbilityProperty(property));
    }

    /** A cost delta from the current avatar's abilities: in stamina points there, in hundredths here */
    private float getStaminaCostDelta(EntityAbilityProperty property) {
        var entity = player.getTeamManager().getCurrentAvatarEntity();
        return entity.getAbilityProperty(property) * 100;
    }

    /** A cost (negative) raised by a delta (positive costs more), never turning into a gain */
    private static int costWithDelta(int cost, float delta) {
        return Math.min(0, Math.round(cost - delta));
    }

    private Consumption getClimbConsumption() {
        Consumption consumption = new Consumption();
        if (currentState == MotionState.MOTION_CLIMB && isPlayerMoving()) {
            consumption.type = ConsumptionType.CLIMBING;
            consumption.amount = ConsumptionType.CLIMBING.amount;
        }
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
        return consumption;
    }

    private Consumption getDashConsumption() {
        Consumption consumption = new Consumption();
        if (currentState == MotionState.MOTION_DASH) {
            consumption.type = ConsumptionType.DASH;
            // The delta is likely per second (e.g. MoonGravity -11 of the 18 per second)
            consumption.amount = costWithDelta(ConsumptionType.DASH.amount,
                getStaminaCostDelta(EntityAbilityProperty.Actor_CostDashStaminaDelta) / TicksPerSecond);
        }
        return consumption;
    }

    private Consumption getFlyConsumption() {
        // POWERED_FLY, e.g. wind tunnel
        if (currentState == MotionState.MOTION_POWERED_FLY) {
            return new Consumption(ConsumptionType.POWERED_FLY);
        }
        Consumption consumption = new Consumption(ConsumptionType.FLY);
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
