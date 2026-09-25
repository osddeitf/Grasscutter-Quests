package emu.grasscutter.server.packet.gadget;

import emu.grasscutter.net.packet.BaseTypedPacket;
import org.anime_game_servers.multi_proto.gi.messages.gadget.GadgetChainLevelUpdateNotify;

import java.util.Map;

public class PacketGadgetChainLevelUpdateNotify extends BaseTypedPacket<GadgetChainLevelUpdateNotify> {
    public PacketGadgetChainLevelUpdateNotify() {
        super(new GadgetChainLevelUpdateNotify());
        proto.setGadgetChainLevelMap(Map.of(100002, 1));
    }
}
