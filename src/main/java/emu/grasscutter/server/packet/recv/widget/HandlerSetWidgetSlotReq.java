package emu.grasscutter.server.packet.recv.widget;

import emu.grasscutter.game.modules.widget.manager.WidgetSwitchManager;
import emu.grasscutter.net.packet.GrasscutterException;
import emu.grasscutter.net.packet.TypedPacketPairHandler;
import emu.grasscutter.server.game.GameSession;
import org.anime_game_servers.multi_proto.gi.messages.item.widget.manage_slot.SetWidgetSlotReq;
import org.anime_game_servers.multi_proto.gi.messages.item.widget.manage_slot.SetWidgetSlotRsp;
import org.anime_game_servers.multi_proto.gi.messages.item.widget.manage_slot.WidgetSlotOp;

public class HandlerSetWidgetSlotReq extends TypedPacketPairHandler<SetWidgetSlotReq, SetWidgetSlotRsp> {
    @Override
    public boolean handle(GameSession session, byte[] header, SetWidgetSlotReq req, SetWidgetSlotRsp rsp) {
        try {
            this.run(session, header, req, rsp);
        }
        catch (GrasscutterException ex) {
            rsp.setRetcode(ex.logAndGetRetcode(req));
        }
        return true;
    }

    public void run(GameSession session, byte[] header, SetWidgetSlotReq req, SetWidgetSlotRsp rsp) throws GrasscutterException {
        var player = session.getPlayer();
        rsp.setOp(req.getOp());
        rsp.setMaterialId(req.getMaterialId());
        rsp.setTagList(req.getTagList());

        var materialId = req.getMaterialId();
        var isAttach = req.getOp() == WidgetSlotOp.WIDGET_SLOT_OP_ATTACH;
        if (isAttach) {
            WidgetSwitchManager.attachMaterial(player, materialId);
        }
        else {
            WidgetSwitchManager.detachMaterial(player, materialId);
        }
    }
}
