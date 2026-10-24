package emu.grasscutter.game.managers.climate;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.config.ConfigClimate;
import emu.grasscutter.data.common.AbilityMath;
import emu.grasscutter.data.common.ClimateAreaData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityTeam;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.BasePlayerManager;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.utils.Position;
import org.slf4j.Logger;
import teyvat.ability.config.enums.ClimateType;
import teyvat.ability.config.mixins.ResistClimateMixin;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The climate meter (e.g. Sheer Cold, Bale Thunder): it rises while in a climate area, fades outside.
 * - In a climate area: rises by the climate's speed (ConfigClimate templates) units per second, by the
 *   ResistClimateMixin ratio, up to 100 units (100 points a unit)
 * - Outside (Normal): fades by ConfigClimate fadeSpeed units per second
 * - AddClimateMeter adds to it, e.g. Dulin's Blood
 * - A different climate clears it (isForceClearDifferentClimateMeter, always as a default)
 * - In a climate area, its server buffs (ConfigClimate): the area buffs and the first level debuff
 * - From the threshold (ConfigClimate thresholdRatio of the max), the meter's climate high level debuff
 * TODO: the mixin's trend, source and type
 */
public class ClimateManager extends BasePlayerManager {
    private static final Logger logger = Grasscutter.getLogger();

    public static final int METER_STEP = 100;  // points per unit
    public static final int METER_MAX = 100 * METER_STEP;

    private ClimateType meterType = ClimateType.Normal;
    private int meter = 0;
    private int areaId = 0;
    private ClimateType areaType = ClimateType.Normal;
    /** The climate whose server buffs were added, null if none yet (buffs aren't persisted) */
    private ClimateType buffType = null;
    /** The climate whose high level debuff was added, null if none */
    private ClimateType highDebuffType = null;
    private Position position;

    /** ResistClimateMixins of the attached modifiers, by modifier controller */
    private final Map<Object, List<Resist>> resists = new ConcurrentHashMap<>();

    private record Resist(int entityId, Set<ClimateType> types, float ratio) {}

    public ClimateManager(Player player) {
        super(player);
    }

    /** Once the scene is fully loaded, e.g. on login or scene change: from the persisted props and the position */
    public void init() {
        this.meter = player.getProperty(PlayerProperty.PROP_CUR_CLIMATE_METER);
        this.meterType = climateTypeOf(player.getProperty(PlayerProperty.PROP_CUR_CLIMATE_TYPE));
        this.areaId = player.getProperty(PlayerProperty.PROP_CUR_CLIMATE_AREA_ID);
        this.areaType = climateTypeOf(player.getProperty(PlayerProperty.PROP_CUR_CLIMATE_AREA_CLIMATE_TYPE));
        this.position = player.getPosition().clone();
    }

    private static ClimateType climateTypeOf(int value) {
        for (var type : ClimateType.values()) {
            if (type.value == value) return type;
        }
        return ClimateType.Normal;
    }

    /** The current avatar moved, the area is looked up on tick */
    public void onMove(Position position) {
        this.position = position;
    }

    public void addResists(Object key, GameEntity<?> entity, List<ResistClimateMixin> mixins, Ability ability) {
        var list = mixins.stream().map(mixin -> {
            Set<ClimateType> types = mixin.climateTypes.isEmpty() ? EnumSet.noneOf(ClimateType.class) : EnumSet.copyOf(mixin.climateTypes);
            return new Resist(entity.getId(), types, AbilityMath.evaluate(mixin.ratio, ability));
        }).toList();
        logger.debug("Climate resists added on entity {}: {}", entity.getId(), list);
        this.resists.put(key, list);
    }

    public void removeResists(Object key) {
        var removed = this.resists.remove(key);
        if (removed != null) logger.debug("Climate resists removed: {}", removed);
    }

    /** The rise ratio, from the current avatar's and the team's resists only (e.g. a team buff is on every avatar) */
    public float getRatio(ClimateType type) {
        var avatar = player.getTeamManager().getCurrentAvatarEntity();
        var team = player.getTeamManager().getEntity();
        int avatarId = avatar != null ? avatar.getId() : 0;
        int teamId = team != null ? team.getId() : 0;

        float sum = 0;
        for (var list : this.resists.values()) {
            for (var resist : list) {
                if ((resist.entityId() == avatarId || resist.entityId() == teamId) && resist.types().contains(type)) {
                    sum += resist.ratio();
                }
            }
        }
        return Math.max(0f, 1f + sum);
    }

    public synchronized void onTick() {
        // Nothing changes while the game is paused (e.g. a menu open), as stamina
        if (player.isPaused() || player.getSceneLoadState() != Player.SceneLoadState.LOADED || this.position == null) return;

        updateArea();
        updateBuffs();

        int newMeter = this.meter;
        var newMeterType = this.meterType;
        if (this.areaType != ClimateType.Normal) {
            if (this.areaType != this.meterType) {
                newMeter = 0;
                newMeterType = this.areaType;
            }
            newMeter = Math.min(METER_MAX, newMeter + Math.round(METER_STEP * getRiseSpeed(newMeterType) * getRatio(newMeterType)));
        } else if (newMeter > 0) {
            newMeter = Math.max(0, newMeter - Math.round(METER_STEP * getFadeSpeed()));
        }

        setMeter(newMeter, newMeterType);
    }

    /**
     * Adds to the meter of a climate, e.g. AddClimateMeter. A negative value only lowers the climate's own meter, a
     * positive one clears a different climate's meter first, as entering its area does.
     * @param units In units, a unit being 100 points
     */
    public synchronized void addMeter(ClimateType type, float units) {
        int delta = Math.round(units * METER_STEP);
        if (type == ClimateType.Normal || delta == 0) return;

        int newMeter = this.meter;
        if (type != this.meterType) {
            if (delta < 0) return;
            newMeter = 0;
        }
        setMeter(Math.max(0, Math.min(METER_MAX, newMeter + delta)), type);
    }

    /** Updates the meter and its climate (Normal once empty), notifies the changes, then the high level debuff */
    private void setMeter(int newMeter, ClimateType newMeterType) {
        if (newMeter == 0) newMeterType = ClimateType.Normal;
        if (newMeter != this.meter || newMeterType != this.meterType) {
            logger.debug("Climate meter {} ({}) -> {} ({}), area {} ({})", this.meter, this.meterType, newMeter, newMeterType,
                this.areaId, this.areaType);
        }

        if (newMeter != this.meter) {
            this.meter = newMeter;
            player.setProperty(PlayerProperty.PROP_CUR_CLIMATE_METER, newMeter);
        }
        if (newMeterType != this.meterType) {
            this.meterType = newMeterType;
            player.setProperty(PlayerProperty.PROP_CUR_CLIMATE_TYPE, newMeterType.value);
        }

        updateHighDebuff();
    }

    /** The climate's increase in its area, in units per second (ConfigClimate template temperatureOptions.speed) */
    private static float getRiseSpeed(ClimateType type) {
        var info = GameData.getConfigClimate().getClimateInfo(type);
        float speed = info != null ? info.getTemperatureOptions().getSpeed() : 0;
        return speed > 0 ? speed : ConfigClimate.DEFAULT_SPEED;
    }

    private static float getFadeSpeed() {
        float fadeSpeed = GameData.getConfigClimate().getCommon().getMiscs().getFadeSpeed();
        return fadeSpeed > 0 ? fadeSpeed : ConfigClimate.DEFAULT_FADE_SPEED;
    }

    /** The player directly affected by an entity's climate effects, e.g. a ResistClimateMixin: of an avatar or a team */
    @Nullable
    public static Player getAffectedPlayer(GameEntity<?> entity) {
        if (entity instanceof EntityAvatar avatar) return avatar.getPlayer();
        if (entity instanceof EntityTeam team) return team.getPlayer();
        return null;
    }

    /** The current area's climate buffs: the area buffs and the first level debuff, the previous climate's removed */
    private void updateBuffs() {
        if (this.areaType == this.buffType) return;

        var buffManager = player.getBuffManager();
        if (this.buffType != null) {
            getClimateBuffs(this.buffType).forEach(buffManager::removeBuff);
        }
        for (int buffId : getClimateBuffs(this.areaType)) {
            if (!buffManager.hasBuff(buffId)) buffManager.addBuff(buffId);
        }
        logger.debug("Climate buffs {} -> {}: {}", this.buffType, this.areaType, getClimateBuffs(this.areaType));
        this.buffType = this.areaType;
    }

    /** The meter's climate high level debuff, while the meter is from the threshold */
    private void updateHighDebuff() {
        float ratio = GameData.getConfigClimate().getCommon().getThresholdRatio();
        if (ratio <= 0) ratio = ConfigClimate.DEFAULT_THRESHOLD_RATIO;

        var type = this.meterType != ClimateType.Normal && this.meter >= ratio * METER_MAX ? this.meterType : null;
        if (type == this.highDebuffType) return;

        var buffManager = player.getBuffManager();
        if (this.highDebuffType != null) {
            var previous = getHighDebuff(this.highDebuffType);
            if (previous != null) buffManager.removeBuff(previous);
        }
        var debuff = type != null ? getHighDebuff(type) : null;
        if (debuff != null && !buffManager.hasBuff(debuff)) buffManager.addBuff(debuff);
        logger.debug("Climate high level debuff {} -> {}: {}", this.highDebuffType, type, debuff);
        this.highDebuffType = type;
    }

    private static Integer getHighDebuff(ClimateType type) {
        var debuffs = GameData.getConfigClimate().getCommon().getDebuffs().getOrDefault(type, List.of());
        return debuffs.size() > 1 ? debuffs.get(1) : null;
    }

    private static List<Integer> getClimateBuffs(ClimateType type) {
        if (type == ClimateType.Normal) return List.of();
        var common = GameData.getConfigClimate().getCommon();
        var buffs = new java.util.ArrayList<>(common.getAreaBuffs().getOrDefault(type, List.of()));
        var debuffs = common.getDebuffs().getOrDefault(type, List.of());
        if (!debuffs.isEmpty()) buffs.add(debuffs.get(0));
        return buffs;
    }

    private void updateArea() {
        var area = findArea(this.position);
        int newAreaId = area != null ? area.area_id : 0;
        var newAreaType = area != null ? area.climateType : ClimateType.Normal;
        if (newAreaId == this.areaId && newAreaType == this.areaType) return;

        logger.debug("Climate area {} ({}) -> {} ({})", this.areaId, this.areaType, newAreaId, newAreaType);
        this.areaId = newAreaId;
        this.areaType = newAreaType;
        player.setProperty(PlayerProperty.PROP_CUR_CLIMATE_AREA_ID, newAreaId);
        player.setProperty(PlayerProperty.PROP_CUR_CLIMATE_AREA_CLIMATE_TYPE, newAreaType.value);
    }

    private ClimateAreaData findArea(Position position) {
        var areaGrid = GameData.getSceneClimateAreas().get(player.getSceneId());
        if (areaGrid == null) return null;
        return areaGrid.get(position).stream().filter(area -> area.isInside(position)).findFirst().orElse(null);
    }
}
