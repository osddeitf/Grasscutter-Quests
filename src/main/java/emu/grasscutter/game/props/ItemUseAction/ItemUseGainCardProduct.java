package emu.grasscutter.game.props.ItemUseAction;

import emu.grasscutter.game.modules.shop.MoonCardManager;
import emu.grasscutter.game.props.ItemUseOp;

public class ItemUseGainCardProduct extends ItemUseAction {
    @Override
    public ItemUseOp getItemUseOp() {
        return ItemUseOp.ITEM_USE_GAIN_CARD_PRODUCT;
    }

    public ItemUseGainCardProduct(String[] useParam) {
    }

    @Override
    public boolean useItem(UseItemParams params) {
        return MoonCardManager.getInstance(params.player).rechargeMoonCard();
    }
}
