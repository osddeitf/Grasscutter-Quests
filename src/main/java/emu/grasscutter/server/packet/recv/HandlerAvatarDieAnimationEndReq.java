package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.TypedPacketHandler;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketAvatarDieAnimationEndRsp;
import lombok.val;
import org.anime_game_servers.multi_proto.gi.messages.team.avatar.AvatarDieAnimationEndReq;

public class HandlerAvatarDieAnimationEndReq extends TypedPacketHandler<AvatarDieAnimationEndReq> {
	@Override
    public void handle(GameSession session, byte[] header, AvatarDieAnimationEndReq req) throws Exception {
        // Where the replacing avatar appears: the dead avatar's own moves aren't applied once it's out of the scene
        val player = session.getPlayer();
        val deadAvatar = player.getTeamManager().getCurrentAvatarEntity();
        if (req.getRebornPos() != null && deadAvatar.getId() == req.getDieGuid() && !deadAvatar.isAlive()) {
            player.getPosition().set(req.getRebornPos());
        }
		player.getTeamManager().onAvatarDie(req.getDieGuid());
        // Response packet
        player.sendPacket(new PacketAvatarDieAnimationEndRsp(req.getDieGuid(), req.getSkillId()));
	}

}
