package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.modules.shop.MoonCardManager;
import emu.grasscutter.net.packet.BaseTypedPacket;
import org.anime_game_servers.multi_proto.gi.messages.shop.micro_transaction.CardProductRewardNotify;

public class PacketCardProductRewardNotify extends BaseTypedPacket<CardProductRewardNotify> {
    public PacketCardProductRewardNotify(MoonCardManager moonCard) {
        super(new CardProductRewardNotify());
        proto.setProductId(MoonCardManager.MOON_CARD_PRODUCT_ID);
        proto.setHcoin(MoonCardManager.ITEM_PRIMO_GEM_AMOUNT);
        proto.setRemainDays(moonCard.getMoonCardRemainDays());
    }
}
