package emu.grasscutter.data.excels;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.GameResource;
import emu.grasscutter.data.ResourceType;
import emu.grasscutter.game.props.ElementType;
import lombok.Getter;

import java.util.List;

@ResourceType(name = "WorldAreaConfigData.json")
public class WorldAreaData extends GameResource {
    private int ID;
    @Getter @SerializedName(value = "AreaID1", alternate = {"areaID1"})
    private int areaID1;
    @Getter @SerializedName(value = "AreaID2", alternate = {"areaID2"})
    private int areaID2;
    @Getter @SerializedName(value = "SceneID", alternate = {"sceneID"})
    private int sceneID;
    @Getter
    private ElementType elementType;
    @Getter
    private List<String> avatarAbilityGroupList;
    @Getter
    private List<String> teamAbilityGroupList;

    @Override
    public int getId() {
        return (this.areaID2 << 16) + this.areaID1;
    }
}
