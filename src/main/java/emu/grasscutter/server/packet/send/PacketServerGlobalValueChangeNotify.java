package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.net.packet.BaseTypedPacket;
import emu.grasscutter.utils.Utils;
import org.anime_game_servers.multi_proto.gi.messages.ability.ServerGlobalValueChangeNotify;

public class PacketServerGlobalValueChangeNotify extends BaseTypedPacket<ServerGlobalValueChangeNotify> {
    public PacketServerGlobalValueChangeNotify(GameEntity<?> entity, String key, float value) {
        super(new ServerGlobalValueChangeNotify());
        proto.setEntityId(entity.getId());
        proto.setKeyHash(Utils.abilityHash(key));
        proto.setValue(value);
    }
}
