package emu.grasscutter.server.packet.recv;

import emu.grasscutter.data.GameData;
import emu.grasscutter.net.packet.TypedPacketHandler;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketEnterWorldAreaRsp;
import lombok.val;
import org.anime_game_servers.multi_proto.gi.messages.packet_head.PacketHead;
import org.anime_game_servers.multi_proto.gi.messages.scene.EnterWorldAreaReq;

public class HandlerEnterWorldAreaReq extends TypedPacketHandler<EnterWorldAreaReq> {
    @Override
    public void handle(GameSession session, byte[] header, EnterWorldAreaReq req) throws Exception {
		val head = PacketHead.parseBy(header, session.getVersion());
        var areaId1 = req.getAreaType() == 1? req.getAreaId(): req.getAreaId() / 100;
        var areaId2 = req.getAreaType() == 2? req.getAreaId(): 0;
        var areaKey = (areaId2 << 16) + areaId1;
        var area = GameData.getWorldAreaDataMap().get(areaKey);
        var player = session.getPlayer();

        if (area != null) {
            player.getTeamManager().enterWorldArea(area);
        }

        session.send(new PacketEnterWorldAreaRsp(head.getClientSequenceId(), req));
		//session.send(new PacketScenePlayerLocationNotify(session.getPlayer()));
	}
}
