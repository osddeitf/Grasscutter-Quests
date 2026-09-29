package emu.grasscutter.data.excels;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.GameResource;
import emu.grasscutter.data.ResourceType;
import emu.grasscutter.data.ResourceType.LoadPriority;
import emu.grasscutter.game.props.ElementType;
import lombok.Getter;

@ResourceType(name = "AvatarSkillExcelConfigData.json", loadPriority = LoadPriority.HIGHEST)
@Getter
public class AvatarSkillData extends GameResource {
    @Getter(onMethod = @__(@Override))
    private int id;
    @Getter private float cdTime;
    @Getter private float costElemVal; // fight prop update uses float
    @Getter private int maxChargeNum;
    @Getter private int triggerID;
    @Getter private boolean isAttackCameraLock;
    @Getter private int proudSkillGroupId;
    @Getter private ElementType costElemType;
    @Getter private long nameTextMapHash;
    @Getter private long descTextMapHash;
    @Getter private String abilityName;
    // Special (non elemental) energy, e.g. Mavuika, Skirk. Key names differ between resource dumps.
    @SerializedName(value = "specialEnergyCostType", alternate = {"specialEnergyType"})
    @Getter private String specialEnergyCostType;
    @SerializedName(value = "specialEnergyCostMax", alternate = {"specialEnergyMax"})
    @Getter private int specialEnergyCostMax;
    @SerializedName(value = "specialEnergyCostStart", alternate = {"specialEnergyMin"})
    @Getter private int specialEnergyCostStart;

    public boolean hasSpecialEnergy() {
        return specialEnergyCostType != null && !specialEnergyCostType.isEmpty()
            && !specialEnergyCostType.equals("SPECIAL_ENERGY_NONE");
    }
}
