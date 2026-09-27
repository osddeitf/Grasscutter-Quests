package emu.grasscutter.game.avatar;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.FightPropData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.game.inventory.EquipType;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.props.FightProperty;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import lombok.Getter;
import lombok.NonNull;
import lombok.val;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static emu.grasscutter.game.inventory.EquipType.*;

// TODO: need to verify, perhaps by writing some unit tests
public class AvatarStatsManager {

    private final Avatar avatar;
    private final Int2FloatOpenHashMap baseStats = new Int2FloatOpenHashMap();
    private final Int2FloatOpenHashMap weaponExtraStats = new Int2FloatOpenHashMap();
    private final Int2FloatOpenHashMap reliquaryExtraStats = new Int2FloatOpenHashMap();
    private final Int2FloatOpenHashMap talentExtraStats = new Int2FloatOpenHashMap();
    @Getter private final Set<EquipAffixData> weaponAffixes = new HashSet<>();
    @Getter private final Set<EquipAffixData> reliquaryAffixes = new HashSet<>();
    @Getter private final Set<ProudSkillData> proudSkills = new HashSet<>();
    @Getter private final Set<AvatarTalentData> talents = new HashSet<>();

    public AvatarStatsManager(@NonNull Avatar avatar) {
        this.avatar = avatar;
        this.calculateLevelBaseStats();
        this.calculateWeaponStats();
        this.calculateReliquaryStats();
        this.calculateTalentStats();
    }

    private static void addFightProperty(Int2FloatOpenHashMap stats, FightProperty prop, float value) {
        var key = prop.getId();
        stats.put(key, stats.get(key) + value);
    }

    // this will modify the first argument
    private static void sumStats(Int2FloatOpenHashMap a, Int2FloatOpenHashMap b) {
        b.forEach((key, value) -> a.mergeFloat(key, value, Float::sum));
    }

    public void refreshStats() {
        // Save current state before clearing
        float hpPercent = avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP) <= 0 ? 1f : avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP) / avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        var skillDepot = avatar.getSkillDepot();
        float currentEnergy = (skillDepot != null) ? avatar.getFightProperty(skillDepot.getElementType().getCurEnergyProp()) : 0f;

        // Aggregate stats
        var stats = baseStats.clone();
        sumStats(stats, weaponExtraStats);
        sumStats(stats, reliquaryExtraStats);
        // NOTE: the only constellation addProps in the resources is FIGHT_PROP_CHARGE_EFFICIENCY
        // (Anemo Traveler C2, talent 72), so a plain sum is safe for the time being
        sumStats(stats, talentExtraStats);

        // Set % stats
        FightProperty.forEachCompoundProperty(c -> {
            var base = stats.get(c.getBase().getId());
            var flat = stats.get(c.getFlat().getId());
            var percent = stats.get(c.getPercent().getId());
            stats.put(c.getResult().getId(), flat + (base * (1f + percent)));
        });

        avatar.getFightProperties().clear();
        avatar.getFightProperties().putAll(stats);

        // Reapply overrides, energy, hp
        avatar.setCurrentEnergy(currentEnergy);
        avatar.getFightProperties().putAll(avatar.getFightPropOverrides());
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP) * hpPercent);
    }

    public void calculateLevelBaseStats() {
        var data = avatar.getData();
        var level = avatar.getLevel();
        baseStats.clear();
        proudSkills.clear();

        // base stats by levels
        addFightProperty(this.baseStats, FightProperty.FIGHT_PROP_BASE_HP, data.getBaseHp(level));
        addFightProperty(this.baseStats, FightProperty.FIGHT_PROP_BASE_ATTACK, data.getBaseAttack(level));
        addFightProperty(this.baseStats, FightProperty.FIGHT_PROP_BASE_DEFENSE, data.getBaseDefense(level));
        addFightProperty(this.baseStats, FightProperty.FIGHT_PROP_CRITICAL, data.getBaseCritical());
        addFightProperty(this.baseStats, FightProperty.FIGHT_PROP_CRITICAL_HURT, data.getBaseCriticalHurt());
        addFightProperty(this.baseStats, FightProperty.FIGHT_PROP_CHARGE_EFFICIENCY, 1f);

        // stats by promote level
        var promoteData = GameData.getAvatarPromoteData(data.getAvatarPromoteId(), avatar.getPromoteLevel());
        if (promoteData != null) {
            // TODO: otherwise, logging to identify issues
            for (FightPropData fightPropData : promoteData.getAddProps()) {
                addFightProperty(baseStats, fightPropData.getProp(), fightPropData.getValue());
            }
        }

        // Skill depot: add proud skills and unlock them if needed
        for (var openData : avatar.getSkillDepot().getInherentProudSkillOpens()) {
            if (openData.getProudSkillGroupId() == 0) {
                continue;
            }
            if (openData.getNeedAvatarPromoteLevel() <= avatar.getPromoteLevel()) {
                int proudSkillId = (openData.getProudSkillGroupId() * 100) + 1;
                var proudSkill = GameData.getProudSkillDataMap().get(proudSkillId);
                if (proudSkill != null) {
                    proudSkills.add(proudSkill);
                }
            }
        }

        // Proud skills
        for (var proudSkill: proudSkills) {
            // Add properties from this proud skill to our avatar
            for (FightPropData prop : proudSkill.getAddProps()) {
                addFightProperty(baseStats, prop.getProp(), prop.getValue());
            }
        }
    }

    public void calculateWeaponStats() {
        var weapon = avatar.getWeapon();
        weaponExtraStats.clear();
        weaponAffixes.clear();

        // TODO: weapon should always be present
        if (weapon == null) return;

        // Add stats
        var curveData = GameData.getWeaponCurveDataMap().get(weapon.getLevel());
        if (curveData != null) {
            for (ItemData.WeaponProperty weaponProperty : weapon.getItemData().getWeaponProperties()) {
                addFightProperty(
                    weaponExtraStats,
                    weaponProperty.getPropType(),
                    weaponProperty.getInitValue() * curveData.getMultByProp(weaponProperty.getType())
                );
            }
        }
        // Weapon promotion stats
        var promoteData = GameData.getWeaponPromoteData(weapon.getItemData().getWeaponPromoteId(), weapon.getPromoteLevel());
        if (promoteData != null) {
            for (FightPropData prop : promoteData.getAddProps()) {
                if (prop.getValue() == 0f || prop.getProp() == null) {
                    continue;
                }
                addFightProperty(weaponExtraStats, prop.getProp(), prop.getValue());
            }
        }
        // Add weapon skill from affixes
        if (weapon.getAffixes() != null && !weapon.getAffixes().isEmpty()) {
            // Weapons usually don't have more than one affix but just in case...
            for (int af : weapon.getAffixes()) {
                if (af == 0) {
                    continue;
                }
                // Calculate affix id
                int affixId = (af * 10) + weapon.getRefinement();
                var affix = GameData.getEquipAffixDataMap().get(affixId);
                if (affix == null) {
                    continue;
                }

                // Add properties from this affix to our avatar
                for (FightPropData prop : affix.getAddProps()) {
                    addFightProperty(weaponExtraStats, prop.getProp(), prop.getValue());
                }

                weaponAffixes.add(affix);
            }
        }
    }

    // Artifacts
    public void calculateReliquaryStats() {
        var pieces = new EquipType[] { EQUIP_BRACER, EQUIP_NECKLACE, EQUIP_SHOES, EQUIP_RING, EQUIP_DRESS };
        var setMap = new Int2IntOpenHashMap();
        reliquaryExtraStats.clear();
        reliquaryAffixes.clear();

        for (var slot: pieces) {
            // Get artifact
            GameItem equip = avatar.getEquipBySlot(slot);
            if (equip == null) {
                continue;
            }
            // Artifact main stat
            ReliquaryMainPropData mainPropData = GameData.getReliquaryMainPropDataMap().get(equip.getMainPropId());
            if (mainPropData != null) {
                ReliquaryLevelData levelData = GameData.getRelicLevelData(equip.getItemData().getRankLevel(), equip.getLevel());
                if (levelData != null) {
                    addFightProperty(reliquaryExtraStats, mainPropData.getFightProp(), levelData.getPropValue(mainPropData.getFightProp()));
                }
            }
            // Artifact sub stats
            for (int appendPropId : equip.getAppendPropIdList()) {
                ReliquaryAffixData affixData = GameData.getReliquaryAffixDataMap().get(appendPropId);
                if (affixData != null) {
                    addFightProperty(reliquaryExtraStats, affixData.getFightProp(), affixData.getPropValue());
                }
            }
            // Set bonus
            if (equip.getItemData().getSetId() > 0) {
                setMap.addTo(equip.getItemData().getSetId(), 1);
            }
        }

        // Set stuff
        setMap.forEach((setId, amount) -> {
            ReliquarySetData setData = GameData.getReliquarySetDataMap().get((int) setId);
            if (setData == null) return;

            // Calculate how many items are from the set
            // Add affix data from set bonus
            val setNeedNum = setData.getSetNeedNum();
            for (int setIndex = 0; setIndex < setNeedNum.length; setIndex++) {
                if (amount < setNeedNum[setIndex]) break;

                int affixId = (setData.getEquipAffixId() * 10) + setIndex;
                EquipAffixData affix = GameData.getEquipAffixDataMap().get(affixId);
                if (affix == null) {
                    continue;
                }

                // Add properties from this affix to our avatar
                for (FightPropData prop : affix.getAddProps()) {
                    addFightProperty(reliquaryExtraStats, prop.getProp(), prop.getValue());
                }

                reliquaryAffixes.add(affix);
            }
        });
    }

    // Constellations
    public void calculateTalentStats() {
        talents.clear();
        talentExtraStats.clear();

        // TODO: handle more logics here
        avatar.getTalentIdList().intStream()
            .mapToObj(GameData.getAvatarTalentDataMap()::get)
            .filter(Objects::nonNull)
            .forEach(talents::add);

        for (var talent : talents) {
            if (talent.getAddProps() == null) continue;
            for (FightPropData prop : talent.getAddProps()) {
                // empty entries in the excel load as FIGHT_PROP_NONE with value 0
                if (prop.getProp() == null || prop.getProp() == FightProperty.FIGHT_PROP_NONE || prop.getValue() == 0f) {
                    continue;
                }
                addFightProperty(talentExtraStats, prop.getProp(), prop.getValue());
            }
        }
    }
}
