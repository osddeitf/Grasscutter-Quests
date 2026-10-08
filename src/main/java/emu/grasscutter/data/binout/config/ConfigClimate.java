package emu.grasscutter.data.binout.config;

import lombok.Data;
import teyvat.ability.config.enums.ClimateType;

import java.util.List;
import java.util.Map;

/** BinOutput/Climate/ConfigClimate.json, the server buffs of each climate */
@Data
public class ConfigClimate {
    public static final float DEFAULT_THRESHOLD_RATIO = 1.0f;
    public static final float DEFAULT_FADE_SPEED = 5.0f;

    private Common common = new Common();

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
    public static class Miscs {
        /** The meter's decrease out of a climate area, in units per second */
        private float fadeSpeed = DEFAULT_FADE_SPEED;
        private float dampingTime;
    }
}
