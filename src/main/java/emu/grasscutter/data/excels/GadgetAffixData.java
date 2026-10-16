package emu.grasscutter.data.excels;

import emu.grasscutter.data.GameResource;
import emu.grasscutter.data.ResourceType;
import lombok.Getter;

/**
 * An affix adding an ability to a gadget, like the monster affixes. Gadgets don't name their affixes in the excels,
 * they likely come from the scripts.
 * TODO: the obfuscated field MEAEFFLPJKB (as of v7.1, the token changes between versions), always true, is likely the monster affix's preAdd: on the wire, a gadget
 *  affix's ability is prepended to the gadget's abilities
 */
@ResourceType(name = "GadgetAffixExcelConfigData.json")
@Getter
public class GadgetAffixData extends GameResource {
    @Getter(onMethod = @__(@Override))
    private int id;
    private String abilityName;
}
