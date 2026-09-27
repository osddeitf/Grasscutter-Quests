package emu.grasscutter.game.avatar;

import dev.morphia.annotations.*;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.OpenConfigEntry;
import emu.grasscutter.data.binout.OpenConfigEntry.SkillPointModifier;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.ability.embryos.AvatarAbilityManager;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityWeapon;
import emu.grasscutter.game.entity.create_config.CreateGadgetEntityConfig;
import emu.grasscutter.game.inventory.EquipType;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ElementType;
import emu.grasscutter.game.props.FetterState;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.utils.ProtoHelper;
import it.unimi.dsi.fastutil.ints.*;
import lombok.Getter;
import lombok.Setter;
import lombok.val;
import org.anime_game_servers.multi_proto.gi.messages.general.PropValue;
import org.anime_game_servers.multi_proto.gi.messages.general.avatar.*;
import org.anime_game_servers.multi_proto.gi.messages.general.avatar.FetterData;
import org.bson.types.ObjectId;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;
import static emu.grasscutter.game.props.PlayerProperty.PROP_SATIATION_VAL;

@Entity(value = "avatars", useDiscriminator = false)
public class Avatar {
    @Id private ObjectId id;
    @Indexed @Getter private int ownerId;	// id of player that this avatar belongs to

    transient private Player owner;
    transient @Getter protected AvatarData data;
    transient @Getter protected long guid;	// Player unique id
    @Getter protected int avatarId;			// id of avatar

    @Getter @Setter protected int level = 1;
    @Getter @Setter protected int exp;
    @Getter @Setter protected int promoteLevel;
    @Getter @Setter protected int satiation; // ?
    @Getter @Setter protected int satiationPenalty; // ?
    @Getter @Setter protected float currentHp;
    @Getter protected float currentEnergy;

    transient @Getter protected final Int2ObjectMap<GameItem> equips = new Int2ObjectOpenHashMap<>();
    transient @Getter protected final Int2FloatOpenHashMap fightProperties = new Int2FloatOpenHashMap();
    transient @Getter protected final Int2FloatOpenHashMap fightPropOverrides = new Int2FloatOpenHashMap();

    @Getter protected final Set<Integer> fetters = new HashSet<>();

    protected final Map<Integer, Integer> skillLevelMap = new Int2IntArrayMap(7); // Talent levels. TODO: keep levels of different elements for Traveler?
    transient @Getter protected final Map<Integer, Integer> skillExtraChargeMap = new Int2IntArrayMap(2); // Charges
    transient protected final Map<Integer, Integer> proudSkillBonusMap = new Int2IntArrayMap(2); // Talent bonus levels (from const)
    @Getter protected int skillDepotId;
    protected Set<Integer> talentIdList; // Constellation id list

    @Getter @Setter private int weaponSkin;
    @Getter @Setter private int flyCloak;
    @Getter @Setter private int costume;
    @Getter private int bornTime;

    @Getter @Setter private int fetterLevel = 1;
    @Getter @Setter private int fetterExp;

    @Getter @Setter private int nameCardRewardId;
    @Getter @Setter private int nameCardId;

    transient @Getter private AvatarStatsManager stats;
    transient @Getter protected AvatarAbilityManager abilities;

    // Character passives - derived from stats manager
    public Set<Integer> getProudSkillList() {
        if (this.stats == null) return Set.of();
        return this.stats.getProudSkills().stream()
            .map(ProudSkillData::getId)
            .collect(Collectors.toSet());
    }

    public AvatarSkillDepotData getSkillDepot() {
        return GameData.getAvatarSkillDepotDataMap().get(skillDepotId);
    }

    @Deprecated // Do not use. Morphia only!
    public Avatar() {
    }

    // On creation
    public Avatar(int avatarId) {
        this(GameData.getAvatarDataMap().get(avatarId));
    }

    public Avatar(AvatarData data) {
        this();
        this.avatarId = data.getId();
        this.nameCardRewardId = data.getNameCardRewardId();
        this.nameCardId = data.getNameCardId();
        this.bornTime = (int) (System.currentTimeMillis() / 1000);
        this.flyCloak = 140001;
        this.talentIdList = new HashSet<>();

        // Combat properties
        Stream.of(FightProperty.values())
            .map(FightProperty::getId)
            .filter(id -> (id > 0) && (id < 3000))
            .forEach(id -> this.setFightProperty(id, 0f));

        // Init
        this.setSkillDepotData(data.getSkillDepotId());
        this.setAvatarData(data);

        // Set stats
        this.recalcStats();
        this.currentHp = getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, this.currentHp);
        this.currentEnergy = 0f;
        // Load handler
        this.onLoad();
    }

    public Player getPlayer() {
        return this.owner;
    }

    public ObjectId getObjectId() {
        return id;
    }

    public AvatarData getAvatarData() {
        return data;
    }

    protected void setAvatarData(AvatarData data) {
        if (this.data != null) return;
        this.data = data;
        this.stats = new AvatarStatsManager(this);
        this.abilities = new AvatarAbilityManager(this);
    }

    public void setOwner(Player player) {
        this.owner = player;
        this.ownerId = player.getUid();
        this.guid = player.getNextGameGuid();
    }

    static public int getMinPromoteLevel(int level) {
        if (level > 80) {
            return 6;
        } else if (level > 70) {
            return 5;
        } else if (level > 60) {
            return 4;
        } else if (level > 50) {
            return 3;
        } else if (level > 40) {
            return 2;
        } else if (level > 20) {
            return 1;
        }
        return 0;
    }

    public boolean addSatiation(float value) {
        if (this.satiation >= 100) return false;
        this.satiation += (int) value;
        return true;
    }

    public GameItem getEquipBySlot(EquipType slot) {
        return this.getEquips().get(slot.getValue());
    }

    private GameItem getEquipBySlot(int slotId) {
        return this.getEquips().get(slotId);
    }

    public GameItem getWeapon() {
        return this.getEquipBySlot(EquipType.EQUIP_WEAPON);
    }

    public void setSkillDepotData(int skillDepotId) {
        this.skillDepotId = skillDepotId;
        var skillDepot = Objects.requireNonNull(this.getSkillDepot());
        // Add any missing skills
        skillDepot.getSkillsAndEnergySkill()
            .forEach(skillId -> this.skillLevelMap.putIfAbsent(skillId, 1));
    }

    /**
     * Changes the avatar's element to the target element, if the character has values for it set in the candSkillDepot
     *
     * @param elementTypeToChange element to change to
     * @return false if failed or already using that element, true if it actually changed
     */
    public boolean changeElement(@Nonnull ElementType elementTypeToChange) {
        val candSkillDepotIndex = elementTypeToChange.getDepotIndex();
        return changeSkillDepot(candSkillDepotIndex);
    }

    public boolean changeSkillDepot(int candSkillDepotIndex) {
        val candSkillDepotIdsList = data.getCandSkillDepotIds();

        // if no candidate skill to change or index out of bound
        if (candSkillDepotIdsList == null || candSkillDepotIndex >= candSkillDepotIdsList.size()) {
            return false;
        }

        int candSkillDepotId = candSkillDepotIdsList.get(candSkillDepotIndex);

        // Sanity checks for skill depots
        val skillDepot = GameData.getAvatarSkillDepotDataMap().get(candSkillDepotId);
        if (skillDepot == null || skillDepot.getId() == skillDepotId) {
            return false;
        }

        // Set skill depot
        setSkillDepotData(candSkillDepotId);

        // The depot change notify must go out before the AbilityChangeNotify sent by recalcStats,
        // so refresh the passives (proud skills) of the new depot first.
        // NOTE: switching constellations along with the element isn't supported yet, the notify still carries the
        // previous depot's constellation data. Element switching as a whole (e.g. skillLevelMap shared between
        // depots) needs a revamp later.
        stats.calculateLevelBaseStats();
        stats.refreshStats();
        owner.sendPacket(new PacketAvatarSkillDepotChangeNotify(this));
        recalcStats(true);
        return true;
    }

    public void setCurrentEnergy() {
        if (GAME_OPTIONS.energyUsage) {
            this.setCurrentEnergy(this.currentEnergy);
        }
    }

    public void setCurrentEnergy(float currentEnergy) {
        var depot = this.getSkillDepot();
        if (depot != null && depot.getEnergySkillData() != null) {
            ElementType element = depot.getElementType();
            var maxEnergy = depot.getEnergySkillData().getCostElemVal();
            this.setFightProperty(element.getMaxEnergyProp(), maxEnergy);
            this.setFightProperty(element.getCurEnergyProp(), GAME_OPTIONS.energyUsage ? currentEnergy : maxEnergy);
        }
    }

    public void setCurrentEnergy(FightProperty curEnergyProp, float currentEnergy) {
        if (GAME_OPTIONS.energyUsage) {
            this.setFightProperty(curEnergyProp, currentEnergy);
            this.currentEnergy = currentEnergy;
            if (getPlayer() != null && getPlayer().hasSentLoginPackets()) {
                getPlayer().sendPacket(new PacketAvatarFightPropUpdateNotify(this, curEnergyProp));
            }
            this.save();
        }
    }

    public void setFightProperty(FightProperty prop, float value) {
        this.getFightProperties().put(prop.getId(), value);
    }

    private void setFightProperty(int id, float value) {
        this.getFightProperties().put(id, value);
    }

    public void addFightProperty(FightProperty prop, float value) {
        this.getFightProperties().put(prop.getId(), getFightProperty(prop) + value);
    }

    public float getFightProperty(FightProperty prop) {
        return getFightProperties().getOrDefault(prop.getId(), 0f);
    }

    public Map<Integer, Integer> getSkillLevelMap() {  // Returns a copy of the skill levels for the current skillDepot.
        var map = new Int2IntOpenHashMap();
        this.getSkillDepot().getSkillsAndEnergySkill().forEach(skillId ->
            map.put(skillId, this.skillLevelMap.getOrDefault(skillId, 1).intValue()));
        return map;
    }

    // Returns a copy of the skill bonus levels for the current skillDepot, capped to avoid invalid levels.
    public Map<Integer, Integer> getProudSkillBonusMap() {
        var map = new Int2IntArrayMap();
        this.getSkillDepot().getSkillsAndEnergySkill().forEach(skillId -> {
            val skillData = GameData.getAvatarSkillDataMap().get(skillId);
            if (skillData == null) return;
            int proudSkillGroupId = skillData.getProudSkillGroupId();
            int bonus = this.proudSkillBonusMap.getOrDefault(proudSkillGroupId, 0);
            int maxLevel = GameData.getProudSkillGroupMaxLevel(proudSkillGroupId);
            int curLevel = this.skillLevelMap.getOrDefault(skillId, 0);
            if (maxLevel > 0) {
                bonus = Math.min(bonus, maxLevel - curLevel);
            }
            map.put(proudSkillGroupId, bonus);
        });
        return map;
    }

    public IntSet getTalentIdList() {  // Returns a copy of the unlocked constellations for the current skillDepot.
        var talents = new IntOpenHashSet(this.getSkillDepot().getTalents());
        talents.removeIf(id -> !this.talentIdList.contains(id));
        return talents;
    }

    public int getCoreProudSkillLevel() {
        var lockedTalents = new IntOpenHashSet(this.getSkillDepot().getTalents());
        lockedTalents.removeAll(this.getTalentIdList());
        // One below the lowest locked talent, or 6 if there are no locked talents.
        return lockedTalents.intStream().map(i -> i % 10).min().orElse(7) - 1;
    }

    // The logic is based entirely on speculations, but it works. Official may do something else.
    public void changeWeaponSkin(int skinId) {
        this.setWeaponSkin(skinId);
        // if the avatar is on the active team, update weapon entity
        if (this.getAsEntity() != null) {
            var weapon = this.getWeapon();
            weapon.setEquipCharacter(0);      // skip unequip logics
            weapon.setWeaponEntity(null);     // force change entity id
            this.equipItem(weapon, false);
        }
        this.save();
    }

    public boolean equipItem(GameItem item, boolean shouldRecalc) {
        // Sanity check equip type
        EquipType itemEquipType = item.getItemData().getEquipType();
        if (itemEquipType == EquipType.EQUIP_NONE) {
            return false;
        }

        // Check if other avatars have this item equipped
        Avatar otherAvatar = getPlayer().getAvatars().getAvatarById(item.getEquipCharacter());
        if (otherAvatar != null) {
            // Unequip other avatar's item
            if (otherAvatar.unequipItem(item.getItemData().getEquipType())) {
                getPlayer().sendPacket(new PacketAvatarEquipChangeNotify(otherAvatar, item.getItemData().getEquipType()));
            }
            // Swap with other avatar
            if (getEquips().containsKey(itemEquipType.getValue())) {
                GameItem toSwap = this.getEquipBySlot(itemEquipType);
                otherAvatar.equipItem(toSwap, false);
            }
            // Recalc
            otherAvatar.recalcStats();
        } else if (getEquips().containsKey(itemEquipType.getValue())) {
            // Unequip item in current slot if it exists
            unequipItem(itemEquipType);
        }

        // Set equip
        getEquips().put(itemEquipType.getValue(), item);

        if (itemEquipType == EquipType.EQUIP_WEAPON && getPlayer().getWorld() != null) {
            if(!(item.getWeaponEntity() != null && item.getWeaponEntity().getScene() == getPlayer().getScene())) {
                val weaponConfig = new CreateGadgetEntityConfig(item.getItemData().getGadgetId());
                item.setWeaponEntity(new EntityWeapon(this.getPlayer().getScene(), weaponConfig));
                getPlayer().getScene().getWeaponEntities().put(item.getWeaponEntity().getId(), item.getWeaponEntity());
            }
            //item.setWeaponEntityId(this.getPlayer().getWorld().getNextEntityId(EntityIdType.WEAPON));
        }

        item.setEquipCharacter(this.getAvatarId());
        item.save();

        if (this.getPlayer().hasSentLoginPackets()) {
            this.getPlayer().sendPacket(new PacketAvatarEquipChangeNotify(this, item));
        }

        if (shouldRecalc) {
            this.recalcStats();
        }

        return true;
    }

    public boolean unequipItem(EquipType slot) {
        GameItem item = getEquips().remove(slot.getValue());

        if (item != null) {
            item.setEquipCharacter(0);
            item.save();
            return true;
        }

        return false;
    }

    public void recalcStats() {
        recalcStats(false);
    }

    public void recalcStats(boolean forceSendAbilityChange) {
        // Setup
        var data = this.getAvatarData();

        // Fetters
        this.getFetters().addAll(data.getFetters());
        this.setNameCardRewardId(data.getNameCardRewardId());
        this.setNameCardId(data.getNameCardId());

        // Refresh stats. TODO: updates should be more granular, but keeping this for now for backward compatibility
        this.stats.calculateLevelBaseStats();
        this.stats.calculateWeaponStats();
        this.stats.calculateReliquaryStats();
        this.stats.calculateTalentStats();
        this.stats.refreshStats();
        this.recalcConstellations();

        this.abilities.attachOpenConfigs(AvatarAbilityManager.SOURCE_WEAPON, this.stats.getWeaponAffixes());
        this.abilities.attachOpenConfigs(AvatarAbilityManager.SOURCE_RELIQUARY, this.stats.getReliquaryAffixes());
        this.abilities.attachOpenConfigs(AvatarAbilityManager.SOURCE_PROUD_SKILL, this.stats.getProudSkills());
        this.abilities.attachOpenConfigs(AvatarAbilityManager.SOURCE_TALENT, this.stats.getTalents());
        this.abilities.updateSkillDepotAbilities();
        this.abilities.notifyChanges();

        // Packet
        if (getPlayer() != null && getPlayer().hasSentLoginPackets()) {
            // Update stats for client. TODO: maybe should sent only updated fight props
            getPlayer().sendPacket(new PacketAvatarFightPropUpdateNotify(this));
        }
    }

    public void calcConstellation(OpenConfigEntry entry, boolean notifyClient) {
        if (entry == null) return;
        if (this.getPlayer() == null)
            notifyClient = false;

        // Check if new constellation adds +3 to a skill level
        if (this.calcConstellationExtraLevels(entry) && notifyClient) {
            // Packet
            this.getPlayer().sendPacket(new PacketProudSkillExtraLevelNotify(this, entry.getExtraTalentIndex()));
        }
        // Check if new constellation adds skill charges
        if (this.calcConstellationExtraCharges(entry) && notifyClient) {
            // Packet
            Stream.of(entry.getSkillPointModifiers())
                .mapToInt(SkillPointModifier::getSkillId)
                .forEach(skillId -> this.getPlayer().sendPacket(
                    new PacketAvatarSkillMaxChargeCountNotify(this, skillId, this.getSkillExtraChargeMap().getOrDefault(skillId, 0))
                ));
        }
    }

    public void recalcConstellations() {
        // Clear first
        this.proudSkillBonusMap.clear();
        this.skillExtraChargeMap.clear();

        // Sanity checks
        if (this.data == null || this.stats == null) {
            return;
        }

        // Unlocked constellations of the current skill depot, as computed by the stats manager
        this.stats.getTalents().stream()
            .map(AvatarTalentData::getOpenConfig)
            .filter(Objects::nonNull)
            .filter(openConfig -> !openConfig.isEmpty())
            .map(GameData.getOpenConfigEntries()::get)
            .filter(Objects::nonNull)
            .forEach(e -> this.calcConstellation(e, false));
    }

    private boolean calcConstellationExtraCharges(OpenConfigEntry entry) {
        var skillPointModifiers = entry.getSkillPointModifiers();
        if (skillPointModifiers == null) return false;

        for (var mod : skillPointModifiers) {
            AvatarSkillData skillData = GameData.getAvatarSkillDataMap().get(mod.getSkillId());

            if (skillData == null) continue;

            int charges = skillData.getMaxChargeNum() + mod.getDelta();

            this.getSkillExtraChargeMap().put(mod.getSkillId(), charges);
        }
        return true;
    }

    private boolean calcConstellationExtraLevels(OpenConfigEntry entry) {
        int skillId = switch (entry.getExtraTalentIndex()) {
            case 9 -> this.getSkillDepot().getEnergySkill();  // Ult skill
            case 2 -> (this.getSkillDepot().getSkills().size() >= 2) ? this.getSkillDepot().getSkills().get(1) : 0;  // E skill
            default -> 0;
        };
        // Sanity check
        if (skillId == 0) {
            return false;
        }

        // Get proud skill group id
        AvatarSkillData skillData = GameData.getAvatarSkillDataMap().get(skillId);

        if (skillData == null) {
            return false;
        }

        // Add to bonus list
        this.addProudSkillLevelBonus(skillData.getProudSkillGroupId(), 3);
        return true;
    }

    private int addProudSkillLevelBonus(int proudSkillGroupId, int bonus) {
        return this.proudSkillBonusMap.compute(proudSkillGroupId, (k,v) -> (v==null) ? bonus : v + bonus);
    }

    public boolean upgradeSkill(int skillId) {
        AvatarSkillData skillData = GameData.getAvatarSkillDataMap().get(skillId);
        if (skillData == null) return false;

        // Get data for next skill level
        int newLevel = this.skillLevelMap.getOrDefault(skillId, 0) + 1;
        if (newLevel > 10) return false;

        // Proud skill data
        int proudSkillId = (skillData.getProudSkillGroupId() * 100) + newLevel;
        ProudSkillData proudSkill = GameData.getProudSkillDataMap().get(proudSkillId);
        if (proudSkill == null) return false;

        // Make sure break level is correct
        if (this.getPromoteLevel() < proudSkill.getBreakLevel()) return false;

        // Pay materials and mora if possible
        if (!this.getPlayer().getInventory().payItems(proudSkill.getTotalCostItems())) return false;

        // Upgrade skill
        this.setSkillLevel(skillId, newLevel);
        return true;
    }

    public boolean setSkillLevel(int skillId, int level) {
        if (level < 0 || level > 15) return false;
        var validLevels = GameData.getAvatarSkillLevels(skillId);
        if (validLevels != null && !validLevels.contains(level)) return false;
        int oldLevel = this.skillLevelMap.getOrDefault(skillId, 0);  // just taking the return value of put would have null concerns
        this.skillLevelMap.put(skillId, level);
        this.save();

        // Packet
        val player = this.getPlayer();
        if (player != null) {
            player.sendPacket(new PacketAvatarSkillChangeNotify(this, skillId, oldLevel, level));
            player.sendPacket(new PacketAvatarSkillUpgradeRsp(this, skillId, oldLevel, level));
        }
        return true;
    }

    public boolean unlockConstellation() {
        return this.unlockConstellation(false);
    }
    public boolean unlockConstellation(boolean skipPayment) {
        int currentTalentLevel = this.getCoreProudSkillLevel();
        if(currentTalentLevel == -1){
            return false;
        }
        int talentId = this.getSkillDepot().getTalents().get(currentTalentLevel);
        return this.unlockConstellation(talentId, skipPayment);
    }
    public boolean unlockConstellation(int talentId) {
        return unlockConstellation(talentId, false);
    }
    public boolean unlockConstellation(int talentId, boolean skipPayment) {
        // Get talent
        AvatarTalentData talentData = GameData.getAvatarTalentDataMap().get(talentId);
        if (talentData == null) return false;
        var player = this.getPlayer();

        // Pay constellation item if possible
        if (!skipPayment && (player != null) && !player.getInventory().payItem(talentData.getMainCostItemId(), 1)) {
            return false;
        }

        // Apply + recalc
        this.talentIdList.add(talentData.getId());

        // Packet
        if (player != null) {
            player.sendPacket(new PacketAvatarUnlockTalentNotify(this, talentId));
            player.sendPacket(new PacketUnlockAvatarTalentRsp(this, talentId));
        }

        // Proud skill bonus map (Extra skills)
        this.calcConstellation(GameData.getOpenConfigEntries().get(talentData.getOpenConfig()), true);

        // Recalc + save avatar
        this.recalcStats(true);
        this.save();
        return true;
    }

    public void forceConstellationLevel(int level) {
        if (level > 6) return;  // Sanity check

        if (level < 0) {  // Special case for resetConst to remove inactive depots too
            this.talentIdList.clear();
            this.recalcStats();
            this.save();
            return;
        }
        this.talentIdList.removeAll(this.getTalentIdList());  // Only remove constellations from active depot
        for (int i = 0; i < level; i++)
            this.unlockConstellation(true);
        this.recalcStats();
        this.save();
    }

    public boolean sendSkillExtraChargeMap() {
        val map = this.getSkillExtraChargeMap();
        if (map.isEmpty()) return false;
        this.getPlayer().sendPacket(new PacketAvatarSkillInfoNotify(this.guid, new Int2IntArrayMap(map)));  // TODO: Remove this allocation when updating interfaces to FastUtils later
        return true;
    }

    public EntityAvatar getAsEntity() {
        if (getPlayer() == null) return null;
        for (EntityAvatar entity : getPlayer().getTeamManager().getActiveTeam()) {
            if (entity.getAvatar() == this) {
                return entity;
            }
        }
        return null;
    }

    public int getEntityId() {
        EntityAvatar entity = getAsEntity();
        return entity != null ? entity.getId() : 0;
    }

    public void save() {
        if (this instanceof TrialAvatar) return;
        DatabaseHelper.saveAvatar(this);
    }

    public AvatarInfo protoBuilder() {
        int fetterLevel = this.getFetterLevel();
        val avatarFetter = new AvatarFetterInfo();
        avatarFetter.setExpLevel(fetterLevel);

        if (fetterLevel != 10) {
            avatarFetter.setExpNumber(this.getFetterExp());
        }

        avatarFetter.setFetterList(this.fetters.stream().map(fetterId ->
            new FetterData(fetterId, FetterState.FINISH.getValue())).toList());

        val avatarInfo = new AvatarInfo(this.getAvatarId(), this.getGuid());
        avatarInfo.setLifeState(1);
        avatarInfo.setTalentIdList(this.getTalentIdList().stream().toList());
        avatarInfo.setFightPropMap(this.getFightProperties());
        avatarInfo.setSkillDepotId(this.getSkillDepotId());
        avatarInfo.setCoreProudSkillLevel(this.getCoreProudSkillLevel());
        avatarInfo.setSkillLevelMap(this.getSkillLevelMap());
        avatarInfo.setInherentProudSkillList(this.getProudSkillList().stream().toList());
        avatarInfo.setProudSkillExtraLevelMap(getProudSkillBonusMap());
        avatarInfo.setAvatarType(1);
        avatarInfo.setBornTime(this.getBornTime());
        avatarInfo.setWearingFlycloakId(this.getFlyCloak());
        avatarInfo.setCostumeId(this.getCostume());
        avatarInfo.setFocus(false);
        avatarInfo.setWeaponSkinId(this.getWeaponSkin());

        avatarInfo.setFetterInfo(avatarFetter);
        if (this.getPlayer().getNameCardList().contains(this.getNameCardId())) {
            avatarFetter.setRewardedFetterLevelList(List.of(10));
        }

        avatarInfo.setSkillMap(this.getSkillExtraChargeMap().entrySet().stream().collect(
            // key == skillId
            Collectors.toMap(Map.Entry::getKey,
                entry -> {
                    val skillInfo = new AvatarSkillInfo();
                    skillInfo.setMaxChargeCount(entry.getValue());
                    return skillInfo;
                })));

        avatarInfo.setEquipGuidList(this.getEquips().values().stream().map(GameItem::getGuid).toList());

        val propMap = new Int2ObjectOpenHashMap<PropValue>();

        propMap.put(PlayerProperty.PROP_LEVEL.getId(), ProtoHelper.newPropValue(PlayerProperty.PROP_LEVEL, this.getLevel()));
        propMap.put(PlayerProperty.PROP_EXP.getId(), ProtoHelper.newPropValue(PlayerProperty.PROP_EXP, this.getExp()));
        propMap.put(PlayerProperty.PROP_BREAK_LEVEL.getId(), ProtoHelper.newPropValue(PlayerProperty.PROP_BREAK_LEVEL, this.getPromoteLevel()));
        propMap.put(PROP_SATIATION_VAL.getId(), ProtoHelper.newPropValue(PROP_SATIATION_VAL, 0));
        propMap.put(PlayerProperty.PROP_SATIATION_PENALTY_TIME.getId(), ProtoHelper.newPropValue(PlayerProperty.PROP_SATIATION_PENALTY_TIME, 0));

        avatarInfo.setPropMap(propMap);
        return avatarInfo;
    }

    public AvatarInfo toProto() {
        return this.protoBuilder();
    }

    // used only in character showcase
    public ShowAvatarInfo toShowAvatarInfoProto() {
        val avatarFetter = new AvatarFetterInfo();
        avatarFetter.setExpLevel(this.getFetterLevel());

        val showAvatarInfo = new ShowAvatarInfo(avatarId);
        showAvatarInfo.setTalentIdList(this.getTalentIdList().stream().toList());
        showAvatarInfo.setFightPropMap(this.getFightProperties());
        showAvatarInfo.setSkillDepotId(this.getSkillDepotId());
        showAvatarInfo.setCoreProudSkillLevel(this.getCoreProudSkillLevel());
        showAvatarInfo.setInherentProudSkillList(this.getProudSkillList().stream().toList());
        showAvatarInfo.setSkillLevelMap(this.getSkillLevelMap());
        showAvatarInfo.setProudSkillExtraLevelMap(this.getProudSkillBonusMap());
        showAvatarInfo.setFetterInfo(avatarFetter);
        showAvatarInfo.setCostumeId(this.getCostume());

        val propMap = new Int2ObjectOpenHashMap<PropValue>();
        propMap.put(PlayerProperty.PROP_LEVEL.getId(), ProtoHelper.newPropValue(PlayerProperty.PROP_LEVEL, this.getLevel()));
        propMap.put(PlayerProperty.PROP_EXP.getId(), ProtoHelper.newPropValue(PlayerProperty.PROP_EXP, this.getExp()));
        propMap.put(PlayerProperty.PROP_BREAK_LEVEL.getId(), ProtoHelper.newPropValue(PlayerProperty.PROP_BREAK_LEVEL, this.getPromoteLevel()));
        propMap.put(PROP_SATIATION_VAL.getId(), ProtoHelper.newPropValue(PROP_SATIATION_VAL, this.getSatiation()));
        propMap.put(PlayerProperty.PROP_SATIATION_PENALTY_TIME.getId(), ProtoHelper.newPropValue(PlayerProperty.PROP_SATIATION_PENALTY_TIME, this.getSatiationPenalty()));
        int maxStamina = this.getPlayer().getProperty(PlayerProperty.PROP_MAX_STAMINA);
        propMap.put(PlayerProperty.PROP_MAX_STAMINA.getId(), ProtoHelper.newPropValue(PlayerProperty.PROP_MAX_STAMINA, maxStamina));

        showAvatarInfo.setPropMap(propMap);

        val equips = this.getEquips().values().stream().map(item -> {
            val equip = new ShowEquip(item.getItemId());
            if (item.getItemType() == ItemType.ITEM_RELIQUARY) {
                equip.setDetail(new ShowEquip.Detail.Reliquary(item.toReliquaryProto()));
            } else if (item.getItemType() == ItemType.ITEM_WEAPON) {
                equip.setDetail(new ShowEquip.Detail.Weapon(item.toWeaponProto()));
            }
            return equip;
        }).toList();
        showAvatarInfo.setEquipList(equips);

        return showAvatarInfo;
    }

    @PostLoad
    private void onLoad() {

    }

    @PrePersist
    protected void prePersist() {
        this.currentHp = this.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
    }
}
