package emu.grasscutter.data.excels;

import emu.grasscutter.data.GameResource;
import emu.grasscutter.data.ResourceType;
import emu.grasscutter.data.common.CurveInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/** The gadgets' grow curves at a level, for their GadgetPropData */
@ResourceType(name = "GadgetCurveExcelConfigData.json")
public class GadgetCurveData extends GameResource {
    private int level;
    private CurveInfo[] curveInfos;

    private Map<String, Float> curveInfoMap;

    @Override
    public int getId() {
        return level;
    }

    public float getMultByProp(String curve) {
        return curveInfoMap.getOrDefault(curve, 1f);
    }

    @Override
    public void onLoad() {
        this.curveInfoMap = new HashMap<>();
        Stream.of(this.curveInfos).forEach(info -> this.curveInfoMap.put(info.getType(), info.getValue()));
    }
}
