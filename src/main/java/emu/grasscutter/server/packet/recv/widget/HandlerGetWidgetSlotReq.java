package emu.grasscutter.server.packet.recv.widget;

import emu.grasscutter.game.modules.widget.manager.WidgetSwitchManager;
import emu.grasscutter.net.packet.TypedPacketPairHandler;
import emu.grasscutter.server.game.GameSession;
import org.anime_game_servers.multi_proto.gi.messages.item.widget.manage_slot.GetWidgetSlotReq;
import org.anime_game_servers.multi_proto.gi.messages.item.widget.manage_slot.GetWidgetSlotRsp;

public class HandlerGetWidgetSlotReq extends TypedPacketPairHandler<GetWidgetSlotReq, GetWidgetSlotRsp> {
	@Override
    public boolean handle(GameSession session, byte[] header, GetWidgetSlotReq req, GetWidgetSlotRsp rsp) {
		var player = session.getPlayer();
        rsp.setSlotList(WidgetSwitchManager.getSlotList(player));
        return true;
	}
}
