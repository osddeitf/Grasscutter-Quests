package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BaseTypedPacket;
import org.anime_game_servers.multi_proto.gi.messages.gadget.VehiclePhlogistonNotify;

public class PacketVehiclePhlogistonNotify extends BaseTypedPacket<VehiclePhlogistonNotify> {
    public PacketVehiclePhlogistonNotify(int vehicleId, float curPhlogiston) {
        super(new VehiclePhlogistonNotify());
        proto.setEntityId(vehicleId);
        proto.setCurPhlogiston(curPhlogiston);
    }
}
