package emu.grasscutter.game.entity;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.StackedValue;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityModifierController;
import emu.grasscutter.game.entity.create_config.CreateEntityConfig;
import emu.grasscutter.game.entity.create_config.CreateGadgetEntityConfig;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.SpawnDataEntry;
import emu.grasscutter.game.world.World;
import emu.grasscutter.scripts.data.controller.EntityController;
import emu.grasscutter.server.event.entity.EntityDamageEvent;
import emu.grasscutter.server.event.entity.EntityDeathEvent;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import emu.grasscutter.server.packet.send.PacketEntityTagChangeNotify;
import emu.grasscutter.utils.Position;
import teyvat.ability.config.enums.AbilityState;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.Getter;
import lombok.Setter;
import lombok.val;
import org.anime_game_servers.multi_proto.gi.messages.gadget.GadgetInteractReq;
import org.anime_game_servers.multi_proto.gi.messages.scene.entity.FightPropPair;
import org.anime_game_servers.multi_proto.gi.messages.scene.entity.MotionInfo;
import org.anime_game_servers.multi_proto.gi.messages.scene.entity.MotionState;
import org.anime_game_servers.multi_proto.gi.messages.scene.entity.SceneEntityInfo;

import javax.annotation.Nullable;
import java.util.*;

public abstract class GameEntity<T extends CreateEntityConfig<?>> {
    @Getter private final Scene scene;
    @Getter protected int id;
    @Getter @Setter private T spawnConfig;

    @Getter @Setter private int blockId;
    @Getter @Setter private int configId;
    @Getter @Setter private int groupId;

    @Getter @Setter private MotionState motionState;
    @Getter @Setter private int lastMoveSceneTimeMs;
    @Getter @Setter private int lastMoveReliableSeq;

    @Getter @Setter private boolean combatLockHP;
    @Getter @Setter private int level = 0;

    // Lua controller for specific actions
    @Getter @Setter private EntityController entityController;
    @Getter private ElementType lastAttackType = ElementType.None;

    @Getter private Int2ObjectMap<Ability> instancedAbilities = new Int2ObjectOpenHashMap<>();
    @Getter private Int2ObjectMap<AbilityModifierController> instancedModifiers = new Int2ObjectOpenHashMap<>();
    /** Modifiers of the server buffs held by this entity, by server buff uid */
    @Getter private final Int2ObjectMap<AbilityModifierController> serverBuffModifiers = new Int2ObjectOpenHashMap<>();
    @Getter private Map<String, Float> globalAbilityValues = new HashMap<>();
    @Getter private Set<String> entityTags = new HashSet<>();
    private final Map<EntityAbilityProperty, StackedValue> abilityPropertyMap = new HashMap<>();
    /** The states of the attached modifiers, by modifier, see {@link #hasAbilityState} */
    private final Map<Object, Long> abilityStates = new java.util.concurrent.ConcurrentHashMap<>();
    /** The states held by any attached modifier: or-ed rather than xor-ed, as two modifiers may hold the same state */
    private volatile long abilityStateMask;

    public float getAbilityProperty(EntityAbilityProperty prop) {
        var value = abilityPropertyMap.get(prop);
        return value == null ? prop.getDefaultValue() : value.getValue();
    }

    public int addAbilityProperty(String name, float value) {
        try {
            var key = EntityAbilityProperty.valueOf(name);
            var val = abilityPropertyMap.computeIfAbsent(key, StackedValue::from);
            // TODO: limit with AbilityPropExcelConfig
            return val.add(value, false);
        }
        catch (IllegalArgumentException ex) {
            // logging
        }
        return -1;
    }

    public void removeAbilityProperty(String name, int idx) {
        try {
            // TODO: default value + bounded
            var key = EntityAbilityProperty.valueOf(name);
            var val = abilityPropertyMap.get(key);
            if (val != null) {
                val.del(idx);
            }
        }
        catch (IllegalArgumentException ex) {
            // logging
        }
    }

    protected GameEntity(Scene scene) {
        this.scene = scene;
        this.motionState = MotionState.MOTION_NONE;
    }

    protected GameEntity(Scene scene, T createConfig) {
        this.scene = scene;
        this.spawnConfig = createConfig;
        this.motionState = MotionState.MOTION_NONE;
        this.configId = createConfig.getConfigId();
        this.groupId = createConfig.getGroupId();
        this.blockId = createConfig.getBlockId();
        this.level = createConfig.getLevel();
    }

    public SpawnDataEntry getSpawnEntry() {
        if (spawnConfig != null && spawnConfig.getInitDataSource() instanceof SpawnDataEntry spawnEntry) {
            return spawnEntry;
        }
        return null;
    }

    public EntityType getEntityType() {
        return EntityIdType.fromEntityId(this.getId()).getType();
    }

    public abstract int getEntityTypeId();

    public World getWorld() {
        return this.getScene().getWorld();
    }

    public boolean isAlive() {
        return true;
    }

    public LifeState getLifeState() {
        return this.isAlive() ? LifeState.LIFE_ALIVE : LifeState.LIFE_DEAD;
    }

    @Nullable
    public abstract Int2FloatMap getFightProperties();
    public Optional<Int2FloatMap> getFightPropertiesOpt() {
        return Optional.ofNullable(this.getFightProperties());
    }

    public abstract Position getPosition();

    public abstract Position getRotation();

    public void setFightProperty(FightProperty prop, float value) {
        this.setFightProperty(prop.getId(), value);
    }

    public void setFightProperty(int id, float value) {
        this.getFightPropertiesOpt().ifPresent(
            props -> props.put(id, value)
        );
    }

    public void addFightProperty(FightProperty prop, float value) {
        this.getFightPropertiesOpt().ifPresent(
            props -> props.put(prop.getId(), props.getOrDefault(prop.getId(), 0f) + value)
        );
    }

    public float getFightProperty(FightProperty prop) {
        val props = this.getFightProperties();
        if(props == null) return 0f;
        return props.get(prop.getId());
    }

    public boolean hasFightProperty(FightProperty prop) {
        val props = this.getFightProperties();
        if(props == null) return false;
        return props.containsKey(prop.getId());
    }

    public void addAllFightPropsToEntityInfo(SceneEntityInfo entityInfo) {
        this.getFightPropertiesOpt().ifPresent(map -> entityInfo.setFightPropList(map.int2FloatEntrySet().stream()
            .filter(entry -> entry.getIntKey() != 0)
            .map(entry -> new FightPropPair(entry.getIntKey(), entry.getFloatValue())).toList()));
    }

    protected MotionInfo getMotionInfo() {
        val speed = new org.anime_game_servers.multi_proto.gi.messages.general.Vector();
        return new MotionInfo(this.getPosition().toProto(), this.getRotation().toProto(), speed, this.getMotionState());
    }

    public float heal(float amount) {
        return heal(amount, false);
    }

    public float heal(float amount, boolean mute) {
        if (this.getFightProperties() == null) {
            return 0f;
        }

        float curHp = this.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
        float maxHp = this.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);

        if (curHp >= maxHp) {
            return 0f;
        }

        float healed = Math.min(maxHp - curHp, amount);
        this.addFightProperty(FightProperty.FIGHT_PROP_CUR_HP, healed);

        this.getScene().broadcastPacket(new PacketEntityFightPropUpdateNotify(this, FightProperty.FIGHT_PROP_CUR_HP));

        return healed;
    }

    public void damage(float amount) {
        this.damage(amount, 0, ElementType.None);
    }

    public void damage(float amount, ElementType attackType) {
        this.damage(amount, 0, attackType);
    }

    /** Whether the HP is locked: by the entity's config, or by an attached modifier's LockHP state */
    public boolean isHpLocked() {
        return this.combatLockHP || hasAbilityState(AbilityState.LockHP);
    }

    public void damage(float amount, int killerId, ElementType attackType) {
        // Check if the entity has properties.
        if (!hasFightProperty(FightProperty.FIGHT_PROP_CUR_HP)) {
            return;
        }
        // e.g. during an avatar's elemental burst, the client reports the hits and leaves the immunity to the server
        if (hasAbilityState(AbilityState.Invincible)) {
            return;
        }

        // Invoke entity damage event.
        EntityDamageEvent event = new EntityDamageEvent(this, amount, attackType, this.getScene().getEntityById(killerId));
        event.call();
        if (event.isCanceled()) {
            return; // If the event is canceled, do not damage the entity.
        }

        float curHp = getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
        // A locked HP never changes, e.g. the platforms the boss lands on must not die
        if (curHp != Float.POSITIVE_INFINITY && !isHpLocked()) {
            // Add negative HP to the current HP property.
            this.addFightProperty(FightProperty.FIGHT_PROP_CUR_HP, -(event.getDamage()));
        }

        this.lastAttackType = attackType;

        // Check if dead
        boolean isDead = false;
        if (this.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP) <= 0f) {
            // a modifier's Limbo state survives the fatal hit at 1 HP, the client then removes the modifier
            if (hasAbilityState(AbilityState.Limbo)) {
                this.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 1f);
            } else {
                this.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0f);
                isDead = true;
            }
        }
        callLuaHPEvent(event);
        callAbilityBeHurt(event);

        // Packets
        this.getScene().broadcastPacket(new PacketEntityFightPropUpdateNotify(this, FightProperty.FIGHT_PROP_CUR_HP));

        // Check if dead.
        if (isDead) {
            this.getScene().killEntity(this, killerId);
        }
    }

    public void callLuaHPEvent(EntityDamageEvent event) {
        if (entityController != null) {
            entityController.onBeHurt(this, event.getAttackElementType(), true);//todo is host handling
        }
    }

    public void callAbilityBeHurt(EntityDamageEvent event) {
        //instancedAbilities.forEach(ability -> ability.onBeingHit(event));
    }

    public void changeTag(String tag, boolean isAdd) {
        var wasChanges = false;
        if (isAdd) {
            wasChanges = this.entityTags.add(tag);
        } else {
            wasChanges = this.entityTags.remove(tag);
        }
        if(wasChanges){
            scene.broadcastPacket(new PacketEntityTagChangeNotify(getId(), isAdd, tag));
        }
    }

    /**
     * Move this entity to a new position.
     *
     * @param position The new position.
     * @param rotation The new rotation.
     */
    public void move(Position position, Position rotation) {
        // Set the position and rotation.
        this.getPosition().set(position);
        this.getRotation().set(rotation);
    }

    /**
     * Called when a player interacts with this entity
     *
     * @param player      Player that is interacting with this entity
     * @param interactReq Interact request protobuf data
     */
    public void onInteract(Player player, GadgetInteractReq interactReq) {

    }

    /**
     * Called when this entity is added to the world
     */
    public void onCreate() {

    }

    /**
     * Called right after this entity is added to the world
     */
    public void afterCreate(List<Player> players) {

    }

    public void onRemoved() {

    }

    /**
     * A count within the range, e.g. "1" or "1;3". A fractional count is an expected value, e.g. "1.02": 1, and 2% for
     * one more
     */
    private int rollCount(String range, java.util.Random random) {
        var split = range.split(";");
        float min = Float.parseFloat(split[0]);
        float max = split.length > 1 ? Float.parseFloat(split[1]) : min;
        if (min == (int) min && max == (int) max) {
            return (int) min + random.nextInt((int) max - (int) min + 1);
        }
        float count = min + random.nextFloat() * (max - min);
        int whole = (int) count;
        return whole + (random.nextFloat() < count - whole ? 1 : 0);
    }

    public boolean dropSubfieldItem(int dropId) {
        var drop = GameData.getDropSubfieldMappingMap().get(dropId);
        if(drop == null) {
            Grasscutter.getLogger().warn("Subfield drop {} not found in DropSubfieldMapping, entity {}", dropId, getEntityTypeId());
            return false;
        }
        var dropTableEntry = GameData.getDropTableExcelConfigDataMap().get(drop.getItemId());
        if(dropTableEntry == null) {
            Grasscutter.getLogger().warn("Subfield drop {}: drop table {} not found, entity {}", dropId, drop.getItemId(), getEntityTypeId());
            return false;
        }

        var dropRandom = getScene().getWorld().getWorldRandomGenerator();

        Int2ObjectMap<Integer> itemsToDrop = new Int2ObjectOpenHashMap<>();
        switch(dropTableEntry.getRandomType()) {
            case 0: //select one
                {
                    int weightCount = 0;
                    for(var entry : dropTableEntry.getDropVec()) weightCount += entry.getWeight();

                    int randomValue = dropRandom.nextInt(weightCount);

                    weightCount = 0;
                    for(var entry : dropTableEntry.getDropVec()) {
                        if(randomValue >= weightCount && randomValue < (weightCount + entry.getWeight())) {
                            itemsToDrop.put(entry.getItemId(), Integer.valueOf(rollCount(entry.getCountRange(), dropRandom)));
                        }
                    }
                }
                break;
            case 1: //Select various, each by its weight out of 10000
                {
                    for(var entry : dropTableEntry.getDropVec()) {
                        if(dropRandom.nextInt(10000) < entry.getWeight()) {
                            itemsToDrop.put(entry.getItemId(), Integer.valueOf(rollCount(entry.getCountRange(), dropRandom)));
                        }
                    }
                }
                break;
            default:
                Grasscutter.getLogger().warn("Subfield drop {}: drop table {} random type {} not handled", dropId, dropTableEntry.getId(), dropTableEntry.getRandomType());
        }

        Grasscutter.getLogger().debug("Subfield drop {} (table {}) on entity {}: {}", dropId, dropTableEntry.getId(), getEntityTypeId(), itemsToDrop);

        for (var entry : itemsToDrop.int2ObjectEntrySet()) {
            val itemData = GameData.getItemDataMap().get(entry.getIntKey());
            if (itemData == null) {
                // TODO: a drop vector entry may be another drop table (nested), not handled
                Grasscutter.getLogger().warn("Subfield drop {}: item {} not found", dropId, entry.getIntKey());
                continue;
            }
            val createConfig = new CreateGadgetEntityConfig(itemData, entry.getValue())
                .setBornPos(getPosition().nearby2d(1f).addY(0.5f));
            EntityItem item = new EntityItem(scene, createConfig);

            scene.addEntity(item);
        }

        return true;
    }

    public boolean dropSubfield(String subfieldName) {
        var subfieldMapping = GameData.getSubfieldMappingMap().get(getEntityTypeId());
        if(subfieldMapping == null || subfieldMapping.getSubfields() == null) {
            Grasscutter.getLogger().warn("Subfield {}: entity {} not found in SubfieldMapping", subfieldName, getEntityTypeId());
            return false;
        }

        for(var entry : subfieldMapping.getSubfields()) {
            if(entry.getSubfieldName().compareTo(subfieldName) == 0) {
                return dropSubfieldItem(entry.getDrop_id());
            }
        }

        Grasscutter.getLogger().warn("Subfield {} not found for entity {} in SubfieldMapping", subfieldName, getEntityTypeId());
        return false;
    }

    public void onTick(int sceneTime) {
        if (entityController != null) {
            entityController.onTimer(this, sceneTime);
        }
    }

    public int onClientExecuteRequest(int param1, int param2, int param3) {
        if (entityController != null) {
            return entityController.onClientExecuteRequest(this, param1, param2, param3);
        }
        return 0;
    }

    public void addAbilityState(Object key, AbilityState state) {
        this.abilityStates.put(key, state.value);
        updateAbilityStateMask();
    }

    public void removeAbilityState(Object key) {
        if (this.abilityStates.remove(key) != null) updateAbilityStateMask();
    }

    public boolean hasAbilityState(AbilityState state) {
        return (this.abilityStateMask & state.value) != 0;
    }

    private void updateAbilityStateMask() {
        this.abilityStateMask = this.abilityStates.values().stream().reduce(0L, (a, b) -> a | b);
    }

    /**
     * Called when this entity dies
     *
     * @param killerId Entity id of the entity that killed this entity
     */
    public void onDeath(int killerId) {
        // Invoke entity death event.
        EntityDeathEvent event = new EntityDeathEvent(this, killerId);
        event.call();

        if (entityController != null) {
            entityController.onDie(this, getLastAttackType());
        }
    }

    public abstract SceneEntityInfo toProto();
}
