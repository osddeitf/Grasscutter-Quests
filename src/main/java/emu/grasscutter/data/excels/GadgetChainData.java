package emu.grasscutter.data.excels;

import emu.grasscutter.data.GameResource;
import emu.grasscutter.data.ResourceType;
import lombok.Getter;

import java.util.List;

@Getter
@ResourceType(name = "GadgetChainExcelConfigData.json")
public class GadgetChainData extends GameResource {
    private List<Integer> buffList;
    private int chainId;
    private int initLevel;
    private boolean isAllowLuaRevise;
    private int maxLevel;

    @Override
    public int getId() {
        return chainId;
    }
}
