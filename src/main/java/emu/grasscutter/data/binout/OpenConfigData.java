package emu.grasscutter.data.binout;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.common.DynamicFloat;

public class OpenConfigData {

    @SerializedName("$type")
    public Type type;

    // AddAbility, UnlockTalentParam
    public String abilityName;

    // AddTalentExtraLevel
    public int talentIndex;
    public int extraLevel;

    // ModifySkillPoint
    public int skillID;
    public int pointDelta;

    // ModifyAbility
    public String paramSpecial;
    public DynamicFloat paramDelta;
    public DynamicFloat paramRatio;

    // UnlockTalentParam
    public String talentParam;

    public enum Type {
        AddAbility,
        ModifyAbility,
        ModifySkillPoint,
        AddTalentExtraLevel,
        UnlockTalentParam,  // TODO: is it handled server-side?
        ModifySkillCD,  // TODO: not handled
        UnlockControllerConditions,  // TODO: not handled
    }
}
