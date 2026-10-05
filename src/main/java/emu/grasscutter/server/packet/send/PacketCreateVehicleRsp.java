package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.entity.create_config.CreateGadgetEntityConfig;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.entity.EntityVehicle;
import emu.grasscutter.game.entity.GameEntity;

import emu.grasscutter.net.packet.BaseTypedPacket;

import emu.grasscutter.utils.Position;
import org.anime_game_servers.multi_proto.gi.messages.gadget.CreateVehicleRsp;
import lombok.val;

import java.util.List;

public class PacketCreateVehicleRsp extends BaseTypedPacket<CreateVehicleRsp> {

    public PacketCreateVehicleRsp(Player player, int vehicleId, int pointId, Position pos, Position rot) {
        super(new CreateVehicleRsp());

        // Kill previous vehicles if there are any, which ejects their members
        List<EntityVehicle> previousVehicles = player.getScene().getEntities().values().stream()
                .filter(entity -> entity instanceof EntityVehicle entityVehicle
                        && entityVehicle.getGadgetId() == vehicleId
                        && entityVehicle.getOwner().equals(player))
                .map(EntityVehicle.class::cast)
                .toList();

        previousVehicles.forEach(entity -> player.getScene().killEntity(entity, 0));

        val config = new CreateGadgetEntityConfig(vehicleId)
            .setBornPos(pos)
            .setBornRot(rot)
            .setPlayerOwner(player);

        EntityVehicle vehicle = new EntityVehicle(player.getScene(), config);
        player.getScene().addEntity(vehicle);

        proto.setVehicleId(vehicleId);
        proto.setEntityId(vehicle.getId());
    }
}
