package emu.grasscutter.server.packet.ability;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BaseTypedPacket;
import lombok.NonNull;
import org.anime_game_servers.multi_proto.gi.messages.team.skill.AvatarTeamResonanceInfo;
import org.anime_game_servers.multi_proto.gi.messages.team.skill.TeamResonanceChangeNotify;

import java.util.Collection;
import java.util.List;

public class PacketTeamResonanceChangeNotify extends BaseTypedPacket<TeamResonanceChangeNotify> {
    public PacketTeamResonanceChangeNotify(@NonNull Player player, Collection<Integer> addResonance, Collection<Integer> delResonance) {
        super(new TeamResonanceChangeNotify());
        proto.setInfoList(player.getTeamManager().getActiveTeam().stream()
            .map(entity -> new AvatarTeamResonanceInfo(
                List.copyOf(addResonance),
                entity.getAvatar().getGuid(),
                List.copyOf(delResonance),
                entity.getId()
            ))
            .toList()
        );
    }
}
