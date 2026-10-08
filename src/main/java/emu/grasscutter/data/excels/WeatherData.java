package emu.grasscutter.data.excels;

import emu.grasscutter.data.GameResource;
import emu.grasscutter.data.ResourceType;
import emu.grasscutter.game.props.ClimateType;
import lombok.Getter;

@ResourceType(name = "WeatherExcelConfigData.json")
public class WeatherData extends GameResource {
    @Getter private int areaID;
    @Getter private int weatherAreaId;
    @Getter private String maxHeightStr;
    /** The area's ceiling, from maxHeightStr, e.g. a cave below the ground's area: null if none */
    private transient Float maxHeight;
    @Getter private int gadgetID;
    @Getter private boolean isDefaultValid;
    @Getter private String templateName;
    @Getter private int priority;
    @Getter private String profileName;
    @Getter private ClimateType defaultClimate;
    @Getter private boolean isUseDefault;
    @Getter private int sceneID;

    @Override
    public int getId() {
        return this.areaID;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.maxHeightStr != null && !this.maxHeightStr.isBlank()) {
            try {
                this.maxHeight = Float.parseFloat(this.maxHeightStr.trim());
            } catch (NumberFormatException ignored) {}
        }
    }

    /** Whether a height is under the area's ceiling, if any */
    public boolean isUnderMaxHeight(float y) {
        return this.maxHeight == null || y <= this.maxHeight;
    }
}
