package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BaseTypedPacket;
import org.anime_game_servers.multi_proto.gi.messages.battle.ServerBuffChangeNotify;
import org.anime_game_servers.multi_proto.gi.messages.battle.ServerBuffChangeType;
import org.anime_game_servers.multi_proto.gi.messages.general.ability.ServerBuff;

import java.util.List;

/** Server buff changes of a single holder, as the official server sends one notify per entity */
public class PacketServerBuffChangeNotify extends BaseTypedPacket<ServerBuffChangeNotify> {
    private PacketServerBuffChangeNotify(ServerBuffChangeType changeType, List<ServerBuff> buffs) {
        super(new ServerBuffChangeNotify());
        proto.setServerBuffChangeType(changeType);
        proto.setServerBuffList(buffs);
    }

    public static PacketServerBuffChangeNotify ofAvatar(long avatarGuid, ServerBuffChangeType changeType, List<ServerBuff> buffs) {
        var packet = new PacketServerBuffChangeNotify(changeType, buffs);
        packet.proto.setAvatarGuidList(List.of(avatarGuid));
        return packet;
    }

    public static PacketServerBuffChangeNotify ofEntity(int entityId, ServerBuffChangeType changeType, List<ServerBuff> buffs) {
        var packet = new PacketServerBuffChangeNotify(changeType, buffs);
        packet.proto.setEntityIdList(List.of(entityId));
        packet.proto.setCreatureBuff(true);
        return packet;
    }
}
