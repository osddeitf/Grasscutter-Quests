package emu.grasscutter.data.excels;

import emu.grasscutter.data.GameResource;
import emu.grasscutter.data.ResourceType;
import lombok.Getter;

@ResourceType(name = "DungeonLevelEntityConfigData.json")
public class DungeonLevelEntityData extends GameResource {
    @Getter private int id;
    /** Name of the ConfigLevelEntity the dungeon layers on top of its scene's */
    @Getter private String levelConfigName;
}
