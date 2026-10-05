package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.entity.EntityVehicle;
import emu.grasscutter.net.packet.TypedPacketHandler;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketVehicleInteractRsp;
import lombok.val;
import org.anime_game_servers.multi_proto.gi.messages.gadget.VehicleInteractReq;
import org.anime_game_servers.multi_proto.gi.messages.general.Retcode;
import org.anime_game_servers.multi_proto.gi.messages.general.vehicle.VehicleMember;

public class HandlerVehicleInteractReq extends TypedPacketHandler<VehicleInteractReq> {
    @Override
    public void handle(GameSession session, byte[] header, VehicleInteractReq req) throws Exception {
        val player = session.getPlayer();
        if (!(player.getScene().getEntityById(req.getEntityId()) instanceof EntityVehicle vehicle)) {
            session.send(new PacketVehicleInteractRsp(Retcode.RET_GADGET_NOT_VEHICLE, req.getEntityId(), req.getInteractType()));
            return;
        }

        switch (req.getInteractType()) {
            case VEHICLE_INTERACT_IN -> vehicle.enter(player, player.getTeamManager().getCurrentCharacterGuid());
            case VEHICLE_INTERACT_OUT -> vehicle.exit(player);
            default -> {
                val member = new VehicleMember(player.getUid(), player.getTeamManager().getCurrentCharacterGuid());
                session.send(new PacketVehicleInteractRsp(vehicle, member, req.getInteractType()));
            }
        }
    }
}
