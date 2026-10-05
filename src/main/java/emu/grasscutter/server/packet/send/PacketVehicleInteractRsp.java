package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.entity.EntityVehicle;
import emu.grasscutter.net.packet.BaseTypedPacket;
import org.anime_game_servers.multi_proto.gi.messages.gadget.VehicleInteractRsp;
import org.anime_game_servers.multi_proto.gi.messages.gadget.VehicleInteractType;
import org.anime_game_servers.multi_proto.gi.messages.general.Retcode;
import org.anime_game_servers.multi_proto.gi.messages.general.vehicle.VehicleMember;

public class PacketVehicleInteractRsp extends BaseTypedPacket<VehicleInteractRsp> {

    /** A failed interaction, e.g. the entity isn't a vehicle, or it's full */
    public PacketVehicleInteractRsp(Retcode retcode, int entityId, VehicleInteractType interactType) {
        super(new VehicleInteractRsp());
        proto.setRetCode(retcode);
        proto.setEntityId(entityId);
        proto.setInteractType(interactType);
    }

    /** A member got in or off, the vehicle's membership already changed */
    public PacketVehicleInteractRsp(EntityVehicle vehicle, VehicleMember vehicleMember, VehicleInteractType interactType) {
        super(new VehicleInteractRsp());
        proto.setRetCode(Retcode.RET_SUCC);
        proto.setEntityId(vehicle.getId());
        proto.setInteractType(interactType);
        proto.setMember(vehicleMember);
        proto.setVehicleType(vehicle.getVehicleType().getValue());
        proto.setGadgetId(vehicle.getGadgetId());
        proto.setVehiclePos(vehicle.getPosition().toProto());
        proto.setVehicleRot(vehicle.getRotation().toProto());
    }
}
