package emu.grasscutter.server.packet.send.widget;

import emu.grasscutter.net.packet.BaseTypedPacket;
import org.anime_game_servers.multi_proto.gi.messages.item.widget.use.WidgetDoBagRsp;

public class PacketWidgetDoBagRsp extends BaseTypedPacket<WidgetDoBagRsp> {
    public PacketWidgetDoBagRsp(int materialId) {
        super(new WidgetDoBagRsp());
        proto.setMaterialId(materialId);
    }

    public PacketWidgetDoBagRsp() {
        super(new WidgetDoBagRsp());
    }
}
