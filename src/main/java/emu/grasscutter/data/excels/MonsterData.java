package emu.grasscutter.data.excels;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.config.Configuration;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.GameResource;
import emu.grasscutter.data.ResourceType;
import emu.grasscutter.data.ResourceType.LoadPriority;
import emu.grasscutter.data.binout.config.CombatConfigFolder;
import emu.grasscutter.data.binout.config.ConfigEntityMonster;
import emu.grasscutter.data.binout.config.UnsignedLongAdapter;
import emu.grasscutter.data.common.PropGrowCurve;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.props.MonsterType;
import emu.grasscutter.utils.FileUtils;
import emu.grasscutter.utils.JsonUtils;
import lombok.Getter;
import lombok.val;

@ResourceType(name = "MonsterExcelConfigData.json", loadPriority = LoadPriority.LOW)
@Getter
public class MonsterData extends GameResource {
    static public Set<FightProperty> definedFightProperties = Set.of(FightProperty.FIGHT_PROP_BASE_HP, FightProperty.FIGHT_PROP_BASE_ATTACK, FightProperty.FIGHT_PROP_BASE_DEFENSE, FightProperty.FIGHT_PROP_PHYSICAL_SUB_HURT, FightProperty.FIGHT_PROP_FIRE_SUB_HURT, FightProperty.FIGHT_PROP_ELEC_SUB_HURT, FightProperty.FIGHT_PROP_WATER_SUB_HURT, FightProperty.FIGHT_PROP_GRASS_SUB_HURT, FightProperty.FIGHT_PROP_WIND_SUB_HURT, FightProperty.FIGHT_PROP_ROCK_SUB_HURT, FightProperty.FIGHT_PROP_ICE_SUB_HURT);

    @Getter(onMethod = @__(@Override))
    private int id;

    private String monsterName;
    private MonsterType type;
    private String serverScript;
    private List<Integer> affix;
    private String ai;
    private int[] equips;
    private List<HpDrops> hpDrops;
    private int killDropId;
    private String excludeWeathers;
    private int featureTagGroupID;
    private int mpPropID;
    private String skin;
    private int describeId;
    private int combatBGMLevel;
    private int entityBudgetLevel;

    @SerializedName("hpBase")
    private float baseHp;
    @SerializedName("attackBase")
    private float baseAttack;
    @SerializedName("defenseBase")
    private float baseDefense;

    private float fireSubHurt;
    private float elecSubHurt;
    private float grassSubHurt;
    private float waterSubHurt;
    private float windSubHurt;
    private float rockSubHurt;
    private float iceSubHurt;
    private float physicalSubHurt;
    private List<PropGrowCurve> propGrowCurves;
    private long nameTextMapHash;
    private int campID;
    /** The hash of the combat config's path, see {@link #resolveConfig()} */
    @JsonAdapter(UnsignedLongAdapter.class)
    private long combatConfigHash;

    // Transient
    private int weaponId;
    private MonsterDescribeData describeData;
    @Nullable private transient ConfigEntityMonster config;

	private int specialNameId; // will only be set if describe data is available

	@Override
	public void onLoad() {
		this.config = resolveConfig();

		for (int id : this.equips) {
			if (id == 0) {
				continue;
			}
			GadgetData gadget = GameData.getGadgetDataMap().get(id);
			if (gadget == null) {
				continue;
			}
			if (gadget.getItemJsonName().equals("Default_MonsterWeapon")) {
				this.weaponId = id;
			}
		}

		this.describeData = GameData.getMonsterDescribeDataMap().get(this.getDescribeId());

		if (this.describeData == null){
			return;
		}
		for(val entry: GameData.getMonsterSpecialNameDataMap().int2ObjectEntrySet()) {
			if (entry.getValue().getSpecialNameLabId() == this.getDescribeData().getSpecialNameLabId()){
				this.specialNameId = entry.getIntKey();
				break;
			}
		}
	}

    public float getFightProperty(FightProperty prop) {
        return switch (prop) {
            case FIGHT_PROP_BASE_HP -> this.baseHp;
            case FIGHT_PROP_BASE_ATTACK -> this.baseAttack;
            case FIGHT_PROP_BASE_DEFENSE -> this.baseDefense;
            case FIGHT_PROP_PHYSICAL_SUB_HURT -> this.physicalSubHurt;
            case FIGHT_PROP_FIRE_SUB_HURT -> this.fireSubHurt;
            case FIGHT_PROP_ELEC_SUB_HURT -> this.elecSubHurt;
            case FIGHT_PROP_WATER_SUB_HURT -> this.waterSubHurt;
            case FIGHT_PROP_GRASS_SUB_HURT -> this.grassSubHurt;
            case FIGHT_PROP_WIND_SUB_HURT -> this.windSubHurt;
            case FIGHT_PROP_ROCK_SUB_HURT -> this.rockSubHurt;
            case FIGHT_PROP_ICE_SUB_HURT -> this.iceSubHurt;
            default -> 0f;
        };
    }

    /** The monster config folders, the first being the primary one, see {@link #loadConfigFolders()} */
    private static final List<CombatConfigFolder> configFolders = new ArrayList<>();
    /** The resolved configs by combatConfigHash, null when not found */
    private static final Map<Long, ConfigEntityMonster> configsByHash = new HashMap<>();

    /** Indexes the configured monster config folders, to be done before the monster excel loads */
    public static void loadConfigFolders() {
        configFolders.clear();
        configsByHash.clear();
        for (String folder : Configuration.c.folderStructure.monsterConfigs) {
            val root = folder.startsWith("resources:")
                ? FileUtils.getResourcePath(folder.substring("resources:".length()))
                : Path.of(folder);
            configFolders.add(CombatConfigFolder.load(root, "Monster"));
        }
    }

    /** Each folder is searched in order by unsigned decimal name, path hash, then 8-character short hash. */
    @Nullable
    private ConfigEntityMonster resolveConfig() {
        if (configsByHash.containsKey(this.combatConfigHash)) return configsByHash.get(this.combatConfigHash);

        ConfigEntityMonster config = null;
        search:
        for (int i = 0; i < configFolders.size(); i++) {
            val folder = configFolders.get(i);
            for (val path : folder.findCandidates(this.combatConfigHash)) {
                config = loadConfig(path);
                if (config == null) continue;
                if (i > 0) {
                    Grasscutter.getLogger().warn("Monster {} config for combatConfigHash {} found in fallback folder: {}",
                        this.id, Long.toUnsignedString(this.combatConfigHash), path);
                }
                Grasscutter.getLogger().debug("Resolved monster {} config for combatConfigHash {}: {}",
                    this.id, Long.toUnsignedString(this.combatConfigHash), path);
                break search;
            }
        }
        if (config == null) {
            Grasscutter.getLogger().warn("Monster {} config not found for combatConfigHash {}",
                this.id, Long.toUnsignedString(this.combatConfigHash));
        }
        configsByHash.put(this.combatConfigHash, config);
        return config;
    }

    @Nullable
    private static ConfigEntityMonster loadConfig(Path path) {
        try {
            val config = JsonUtils.loadToClass(path, ConfigEntityMonster.class);
            if (config != null) return config;
            Grasscutter.getLogger().warn("Monster config {} is empty", path);
        } catch (Exception e) {
            Grasscutter.getLogger().warn("Monster config {} failed to parse: {}", path, e.toString());
        }
        return null;
    }

    @Getter
    public class HpDrops {
        private int DropId;
        private int HpPercent;
    }
}
