package emu.grasscutter.data.excels;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.config.Configuration;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.GameResource;
import emu.grasscutter.data.ResourceType;
import emu.grasscutter.data.ResourceType.LoadPriority;
import emu.grasscutter.data.binout.config.CombatConfigFolder;
import emu.grasscutter.data.binout.config.ConfigEntityAvatar;
import emu.grasscutter.data.binout.config.UnsignedLongAdapter;
import emu.grasscutter.data.common.PropGrowCurve;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.props.WeaponType;
import emu.grasscutter.utils.FileUtils;
import emu.grasscutter.utils.JsonUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntList;
import lombok.Getter;

import javax.annotation.Nullable;
import com.google.gson.annotations.JsonAdapter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ResourceType(name = "AvatarExcelConfigData.json", loadPriority = LoadPriority.LOW)
public class AvatarData extends GameResource {

    private String iconName;
    @Getter private String bodyType;
    @Getter private String qualityType;
    @Getter private int chargeEfficiency;
    @Getter private int initialWeapon;
    @Getter private WeaponType weaponType;
    @Getter private String imageName;
    @Getter private int avatarPromoteId;
    @Getter private String cutsceneShow;
    @Getter private int skillDepotId;
    @Getter private int staminaRecoverSpeed;
    @Getter private List<Integer> candSkillDepotIds;
    @Getter private String avatarIdentityType;
    @Getter private List<Integer> avatarPromoteRewardLevelList;
    @Getter private List<Integer> avatarPromoteRewardIdList;

    @Getter private long nameTextMapHash;
    /** The hash of the combat config's path, see {@link #resolveConfig()} */
    @Getter
    @JsonAdapter(UnsignedLongAdapter.class)
    private long combatConfigHash;

    @Nullable @Getter private transient ConfigEntityAvatar combatConfig;

    private float hpBase;
    private float attackBase;
    private float defenseBase;
    private float critical;
    private float criticalHurt;

    private List<PropGrowCurve> propGrowCurves;
    @Getter(onMethod = @__(@Override))
    private int id;

    // Transient
    @Getter private String name;

    private Int2ObjectMap<String> growthCurveMap;
    private float[] hpGrowthCurve;
    private float[] attackGrowthCurve;
    private float[] defenseGrowthCurve;

    @Getter private List<Integer> fetters;
    @Getter private int nameCardRewardId;
    @Getter private int nameCardId;

    /** The avatar config folders, the first being the primary one, see {@link #loadConfigFolders()} */
    private static final List<CombatConfigFolder> configFolders = new ArrayList<>();
    /** The resolved configs by combatConfigHash, null when not found */
    private static final Map<Long, ConfigEntityAvatar> configsByHash = new HashMap<>();

    /** Indexes the configured avatar config folders, to be done before the avatar excel loads */
    public static void loadConfigFolders() {
        configFolders.clear();
        configsByHash.clear();
        for (String folder : Configuration.c.folderStructure.avatarConfigs) {
            var root = folder.startsWith("resources:")
                ? FileUtils.getResourcePath(folder.substring("resources:".length()))
                : Path.of(folder);
            configFolders.add(CombatConfigFolder.load(root, "Avatar"));
        }
    }

    /** Resolves this avatar's combat config by its path hash and caches the result by hash. */
    @Nullable
    private ConfigEntityAvatar resolveConfig() {
        if (configsByHash.containsKey(this.combatConfigHash)) return configsByHash.get(this.combatConfigHash);

        ConfigEntityAvatar config = null;
        search:
        for (int i = 0; i < configFolders.size(); i++) {
            var folder = configFolders.get(i);
            for (var path : folder.findCandidates(this.combatConfigHash)) {
                config = loadConfig(path);
                if (config == null) continue;
                if (i > 0) {
                    Grasscutter.getLogger().warn("Avatar {} config for combatConfigHash {} found in fallback folder: {}",
                        this.id, Long.toUnsignedString(this.combatConfigHash), path);
                }
                Grasscutter.getLogger().debug("Resolved avatar {} config for combatConfigHash {}: {}",
                    this.id, Long.toUnsignedString(this.combatConfigHash), path);
                break search;
            }
        }
        if (config == null) {
            Grasscutter.getLogger().warn("Avatar {} config not found for combatConfigHash {}",
                this.id, Long.toUnsignedString(this.combatConfigHash));
        }
        configsByHash.put(this.combatConfigHash, config);
        return config;
    }

    @Nullable
    private static ConfigEntityAvatar loadConfig(Path path) {
        try {
            var config = JsonUtils.loadToClass(path, ConfigEntityAvatar.class);
            if (config != null) return config;
            Grasscutter.getLogger().warn("Avatar config {} is empty", path);
        } catch (Exception e) {
            Grasscutter.getLogger().warn("Avatar config {} failed to parse: {}", path, e.toString());
        }
        return null;
    }

    public float getBaseHp(int level) {
        try {
            return this.hpBase * this.hpGrowthCurve[level - 1];
        } catch (Exception e) {
            return this.hpBase;
        }
    }

    public float getBaseAttack(int level) {
        try {
            return this.attackBase * this.attackGrowthCurve[level - 1];
        } catch (Exception e) {
            return this.attackBase;
        }
    }

    public float getBaseDefense(int level) {
        try {
            return this.defenseBase * this.defenseGrowthCurve[level - 1];
        } catch (Exception e) {
            return this.defenseBase;
        }
    }

    public float getBaseCritical() {
        return this.critical;
    }

    public float getBaseCriticalHurt() {
        return this.criticalHurt;
    }

    public float getGrowthCurveById(int level, FightProperty prop) {
        String growCurve = this.growthCurveMap.get(prop.getId());
        if (growCurve == null) {
            return 1f;
        }
        AvatarCurveData curveData = GameData.getAvatarCurveDataMap().get(level);
        if (curveData == null) {
            return 1f;
        }
        return curveData.getCurveInfos().getOrDefault(growCurve, 1f);
    }

    @Override
    public void onLoad() {
        this.combatConfig = resolveConfig();

        // Get fetters from GameData
        this.fetters = GameData.getFetterDataEntries().get(this.id);

        if (GameData.getFetterCharacterCardDataMap().get(this.id) != null) {
            this.nameCardRewardId = GameData.getFetterCharacterCardDataMap().get(this.id).getRewardId();
        }

        if (GameData.getRewardDataMap().get(this.nameCardRewardId) != null) {
            this.nameCardId = GameData.getRewardDataMap().get(this.nameCardRewardId).getRewardItemList().get(0).getItemId();
        }

        int size = GameData.getAvatarCurveDataMap().size();
        this.hpGrowthCurve = new float[size];
        this.attackGrowthCurve = new float[size];
        this.defenseGrowthCurve = new float[size];
        for (AvatarCurveData curveData : GameData.getAvatarCurveDataMap().values()) {
            int level = curveData.getLevel() - 1;
            for (PropGrowCurve growCurve : this.propGrowCurves) {
                FightProperty prop = FightProperty.getPropByName(growCurve.getType());
                switch (prop) {
                    case FIGHT_PROP_BASE_HP:
                        this.hpGrowthCurve[level] = curveData.getCurveInfos().get(growCurve.getGrowCurve());
                        break;
                    case FIGHT_PROP_BASE_ATTACK:
                        this.attackGrowthCurve[level] = curveData.getCurveInfos().get(growCurve.getGrowCurve());
                        break;
                    case FIGHT_PROP_BASE_DEFENSE:
                        this.defenseGrowthCurve[level] = curveData.getCurveInfos().get(growCurve.getGrowCurve());
                        break;
                    default:
                        break;
                }
            }
        }

        /*
        for (PropGrowCurve growCurve : this.PropGrowCurves) {
            FightProperty prop = FightProperty.getPropByName(growCurve.getType());
            this.growthCurveMap.put(prop.getId(), growCurve.getGrowCurve());
        }
        */

        var split = this.iconName.split("_");
        if (split.length > 0) {
            this.name = split[split.length - 1];
        }
    }

}

