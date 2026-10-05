package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BaseTypedPacket;
import org.anime_game_servers.multi_proto.gi.messages.gadget.CreateVehicleRsp;

public class PacketCreateVehicleRsp extends BaseTypedPacket<CreateVehicleRsp> {

    public PacketCreateVehicleRsp(int vehicleId, int entityId) {
        super(new CreateVehicleRsp());
        proto.setVehicleId(vehicleId);
        proto.setEntityId(entityId);
    }
}
