package emu.grasscutter.game.modules.shop;

import lombok.Getter;

public enum CardProductType {
    CARD_PRODUCT_TYPE_INVALID(0),
    CARD_PRODUCT_TYPE_HCOIN(1),
    CARD_PRODUCT_TYPE_RESIN(2);

    @Getter private final int value;
    CardProductType(int value) {
        this.value = value;
    }
}
