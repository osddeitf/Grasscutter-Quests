package emu.grasscutter.data.binout.config;

import lombok.Data;
import teyvat.ability.config.enums.ClimateType;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** BinOutput/Climate/ConfigClimate.json, the server buffs and the meter options of each climate */
@Data
public class ConfigClimate {
    public static final float DEFAULT_THRESHOLD_RATIO = 1.0f;
    public static final float DEFAULT_FADE_SPEED = 5.0f;
    public static final float DEFAULT_SPEED = 1.0f;

    private Common common = new Common();
    /** By template name, as in the file */
    private Map<String, ConfigClimateInfo> templates = Map.of();
    /** The templates by climate type, see onLoad */
    private transient Map<ClimateType, ConfigClimateInfo> climateInfos = Map.of();

    /** Indexes the templates by climate type */
    public void onLoad() {
        this.climateInfos = this.templates.values().stream()
            .filter(info -> info.getClimateType() != null)
            .collect(Collectors.toMap(ConfigClimateInfo::getClimateType, info -> info, (first, second) -> first));
    }

    @Nullable
    public ConfigClimateInfo getClimateInfo(ClimateType type) {
        return this.climateInfos.get(type);
    }

    @Data
    public static class Common {
        /** Added while in the climate's area */
        private Map<ClimateType, List<Integer>> areaBuffs = Map.of();
        /** By level, the first one is added while in the climate's area, the second one from the threshold */
        private Map<ClimateType, List<Integer>> debuffs = Map.of();
        /** The meter's ratio of its max from which the high level debuff is added */
        private float thresholdRatio = DEFAULT_THRESHOLD_RATIO;
        private Miscs miscs = new Miscs();
    }

    @Data
    public static class ConfigClimateInfo {
        private ClimateType climateType;
        private TemperatureOptions temperatureOptions = new TemperatureOptions();
    }

    @Data
    public static class TemperatureOptions {
        /** The meter's increase in the climate's area, in units per second */
        private float speed = DEFAULT_SPEED;
    }

    @Data
    public static class Miscs {
        /** The meter's decrease out of a climate area, in units per second */
        private float fadeSpeed = DEFAULT_FADE_SPEED;
        private float dampingTime;
    }
}
