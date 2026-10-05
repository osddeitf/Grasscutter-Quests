package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.entity.EntityVehicle;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.packet.BaseTypedPacket;
import org.anime_game_servers.multi_proto.gi.messages.scene.ScenePlayerLocationNotify;

public class PacketScenePlayerLocationNotify extends BaseTypedPacket<ScenePlayerLocationNotify> {

    public PacketScenePlayerLocationNotify(Scene scene) {
        super(new ScenePlayerLocationNotify(scene.getId()));

        proto.setPlayerLocList(scene.getPlayers().stream().map(Player::getPlayerLocationInfo).toList());
        // Players' vehicles only, not the ones placed by groups
        proto.setVehicleLocList(scene.getEntities().values().stream()
            .filter(EntityVehicle.class::isInstance).map(EntityVehicle.class::cast)
            .filter(vehicle -> vehicle.getOwner() != null)
            .map(EntityVehicle::toLocationInfo).toList());
    }
}
