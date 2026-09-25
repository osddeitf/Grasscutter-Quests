package emu.grasscutter.server.packet.send.widget;

import emu.grasscutter.net.packet.BaseTypedPacket;
import org.anime_game_servers.multi_proto.gi.messages.item.widget.manage_gadget.WidgetGadgetAllDataNotify;

public class PacketWidgetGadgetAllDataNotify extends BaseTypedPacket<WidgetGadgetAllDataNotify> {
    public PacketWidgetGadgetAllDataNotify() {
        super(new WidgetGadgetAllDataNotify());
    }
}
