package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.entity.EntityVehicle;
import emu.grasscutter.game.entity.create_config.CreateGadgetEntityConfig;
import emu.grasscutter.net.packet.TypedPacketHandler;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketCreateVehicleRsp;
import emu.grasscutter.utils.Position;
import lombok.val;
import org.anime_game_servers.multi_proto.gi.messages.gadget.CreateVehicleReq;

public class HandlerCreateVehicleReq extends TypedPacketHandler<CreateVehicleReq> {

    @Override
    public void handle(GameSession session, byte[] header, CreateVehicleReq req) throws Exception {
        val player = session.getPlayer();
        val scene = player.getScene();

        val config = new CreateGadgetEntityConfig(req.getVehicleId())
            .setBornPos(new Position(req.getPos()))
            .setBornRot(new Position(req.getRot()))
            .setPlayerOwner(player);
        val vehicle = new EntityVehicle(scene, config);

        // The kept types are replaced by the world (e.g. the player's previous skiff).
        // TODO: other types, e.g. Sorush (likely one per player), ParamMove (not understood yet)
        scene.addVehicle(vehicle);
        session.send(new PacketCreateVehicleRsp(req.getVehicleId(), vehicle.getId()));
    }
}
