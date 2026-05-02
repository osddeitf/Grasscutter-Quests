package emu.grasscutter.server.packet.recv.widget;

import emu.grasscutter.game.modules.widget.manager.WidgetSwitchManager;
import emu.grasscutter.net.packet.GrasscutterException;
import emu.grasscutter.net.packet.TypedPacketPairHandler;
import emu.grasscutter.server.game.GameSession;
import org.anime_game_servers.multi_proto.gi.messages.item.widget.manage_slot.GetWidgetQuickSwitchReq;
import org.anime_game_servers.multi_proto.gi.messages.item.widget.manage_slot.GetWidgetQuickSwitchRsp;

public class HandlerGetWidgetQuickSwitchReq extends TypedPacketPairHandler<GetWidgetQuickSwitchReq, GetWidgetQuickSwitchRsp> {
    @Override
    public boolean handle(GameSession session, byte[] header, GetWidgetQuickSwitchReq req, GetWidgetQuickSwitchRsp rsp) {
        var player = session.getPlayer();
        var manager = WidgetSwitchManager.getInstance(player);
        rsp.setActiveSwitchIndex(manager.getActiveIndex());
        rsp.setQuickSwitchMaterialIdList(manager.getMaterialList());
        return true;
    }
}
