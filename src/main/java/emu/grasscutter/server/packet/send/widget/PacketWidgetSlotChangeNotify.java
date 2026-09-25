package emu.grasscutter.server.packet.send.widget;

import emu.grasscutter.net.packet.BaseTypedPacket;
import org.anime_game_servers.multi_proto.gi.messages.item.widget.manage_slot.WidgetSlotChangeNotify;
import org.anime_game_servers.multi_proto.gi.messages.item.widget.manage_slot.WidgetSlotData;
import org.anime_game_servers.multi_proto.gi.messages.item.widget.manage_slot.WidgetSlotOp;

public class PacketWidgetSlotChangeNotify extends BaseTypedPacket<WidgetSlotChangeNotify> {
    public PacketWidgetSlotChangeNotify(WidgetSlotOp op, WidgetSlotData data) {
        super(new WidgetSlotChangeNotify());
        proto.setOp(op);
        proto.setSlot(data);
    }
}
