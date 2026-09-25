package emu.grasscutter.server.packet.recv.widget;

import emu.grasscutter.game.modules.widget.manager.WidgetAnchorPointManager;
import emu.grasscutter.net.packet.BaseTypedPacket;
import emu.grasscutter.net.packet.TypedPacketHandler;
import emu.grasscutter.server.game.GameSession;
import org.anime_game_servers.multi_proto.gi.messages.item.widget.anchor_point.AnchorPointOpReq;
import org.anime_game_servers.multi_proto.gi.messages.item.widget.anchor_point.AnchorPointOpRsp;

public class HandlerAnchorPointOpReq extends TypedPacketHandler<AnchorPointOpReq> {
    @Override
    public void handle(GameSession session, byte[] header, AnchorPointOpReq req) throws Exception {
        var player = session.getPlayer();
        // ANCHOR_POINT_OP_TELEPORT
        if (req.getAnchorPointOpType() == 1) {
            WidgetAnchorPointManager.teleportToAnchorPoint(player, req.getAnchorPointId());
            var proto = new AnchorPointOpRsp();
            proto.setAnchorPointId(req.getAnchorPointId());
            proto.setAnchorPointOpType(req.getAnchorPointOpType());
            session.send(new BaseTypedPacket<>(proto) {});
        }
    }
}
