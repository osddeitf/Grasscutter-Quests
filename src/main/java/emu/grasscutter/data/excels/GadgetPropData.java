package emu.grasscutter.data.excels;

import emu.grasscutter.data.GameResource;
import emu.grasscutter.data.ResourceType;
import lombok.Getter;

/** A gadget's HP, attack and defense at level 1, grown by their GadgetCurveData curves */
@ResourceType(name = "GadgetPropExcelConfigData.json")
@Getter
public class GadgetPropData extends GameResource {
    @Getter(onMethod = @__(@Override))
    private int id;
    private float hp;
    private String hpCurve;
    private float attack;
    private String attackCurve;
    private float defense;
    private String defenseCurve;
}
