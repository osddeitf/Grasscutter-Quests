package emu.grasscutter.server.packet.recv.widget;

import emu.grasscutter.game.modules.widget.manager.WidgetSwitchManager;
import emu.grasscutter.net.packet.TypedPacketPairHandler;
import emu.grasscutter.server.game.GameSession;
import org.anime_game_servers.multi_proto.gi.messages.item.widget.manage_slot.SetWidgetQuickSwitchReq;
import org.anime_game_servers.multi_proto.gi.messages.item.widget.manage_slot.SetWidgetQuickSwitchRsp;

public class HandlerSetWidgetQuickSwitchReq extends TypedPacketPairHandler<SetWidgetQuickSwitchReq, SetWidgetQuickSwitchRsp> {
    @Override
    public boolean handle(GameSession session, byte[] header, SetWidgetQuickSwitchReq req, SetWidgetQuickSwitchRsp rsp) {
        var player = session.getPlayer();
        rsp.setActiveSwitchIndex(req.getActiveSwitchIndex());
        rsp.setQuickSwitchMaterialIdList(req.getQuickSwitchMaterialIdList());
        WidgetSwitchManager.setQuickSwitch(player, req.getActiveSwitchIndex(), req.getQuickSwitchMaterialIdList());
        return true;
    }
}
