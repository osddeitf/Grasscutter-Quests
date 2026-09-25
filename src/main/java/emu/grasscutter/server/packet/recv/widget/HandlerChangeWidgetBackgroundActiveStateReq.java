package emu.grasscutter.server.packet.recv.widget;

import emu.grasscutter.game.modules.widget.manager.WidgetBackgroundActiveManager;
import emu.grasscutter.net.packet.TypedPacketPairHandler;
import emu.grasscutter.server.game.GameSession;
import org.anime_game_servers.multi_proto.gi.messages.item.widget.type_background.ChangeWidgetBackgroundActiveStateReq;
import org.anime_game_servers.multi_proto.gi.messages.item.widget.type_background.ChangeWidgetBackgroundActiveStateRsp;

public class HandlerChangeWidgetBackgroundActiveStateReq extends TypedPacketPairHandler<ChangeWidgetBackgroundActiveStateReq, ChangeWidgetBackgroundActiveStateRsp> {
    @Override
    public boolean handle(GameSession session, byte[] header, ChangeWidgetBackgroundActiveStateReq req, ChangeWidgetBackgroundActiveStateRsp rsp) {
        WidgetBackgroundActiveManager.setActive(session.getPlayer(), req.getMaterialId(), req.isActive());
        return true;
    }
}
