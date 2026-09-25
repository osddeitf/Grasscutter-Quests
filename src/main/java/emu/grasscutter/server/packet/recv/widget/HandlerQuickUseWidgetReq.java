package emu.grasscutter.server.packet.recv.widget;

import emu.grasscutter.game.modules.widget.WidgetConfig;
import emu.grasscutter.game.modules.widget.WidgetModule;
import emu.grasscutter.game.modules.widget.manager.WidgetCoolDownManager;
import emu.grasscutter.game.modules.widget.manager.WidgetSwitchManager;
import emu.grasscutter.game.modules.widget.manager.WidgetUseAttachAbilityGroupManager;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.GrasscutterException;
import emu.grasscutter.net.packet.TypedPacketPairHandler;
import emu.grasscutter.server.game.GameSession;
import lombok.NonNull;
import org.anime_game_servers.multi_proto.gi.messages.general.Retcode;
import org.anime_game_servers.multi_proto.gi.messages.item.widget.use.QuickUseWidgetReq;
import org.anime_game_servers.multi_proto.gi.messages.item.widget.use.QuickUseWidgetRsp;

public class HandlerQuickUseWidgetReq extends TypedPacketPairHandler<QuickUseWidgetReq, QuickUseWidgetRsp> {
    @Override
    public boolean handle(GameSession session, byte[] header, QuickUseWidgetReq req, QuickUseWidgetRsp rsp) {
        try {
            this.run(session, header, req, rsp);
        }
        catch (GrasscutterException ex) {
            rsp.setRetcode(ex.logAndGetRetcode(req));
        }
        return true;
    }

    private void run(GameSession session, byte[] header, QuickUseWidgetReq req, QuickUseWidgetRsp rsp) throws GrasscutterException {
        var player = session.getPlayer();
        int materialId = WidgetSwitchManager.getMaterialId(player);
        rsp.setMaterialId(materialId);

        var config = WidgetModule.getWidgetConfigMap().get(materialId);
        if (config == null) {
            throw new GrasscutterException("widget not found in config", Retcode.RET_INVALID_WIDGET_MATERIAL_ID);
        }
        if (WidgetCoolDownManager.isCoolingDown(player, config)) {
            throw new GrasscutterException("widget is on cooldown", Retcode.RET_INVALID_QUICK_USE_WIDGET);
        }

        try {
            handleUseWidget(player, materialId, config);
            WidgetCoolDownManager.setCoolDownSeconds(player, config, true);
        }
        catch (GrasscutterException ex) {
            WidgetCoolDownManager.setCoolDownSeconds(player, config, false);
            throw ex;   // rethrow
        }
    }

    private void handleUseWidget(@NonNull Player player, int materialId, @NonNull WidgetConfig config) throws GrasscutterException {
        // attach widgets (no consuming)
        if (WidgetUseAttachAbilityGroupManager.toggleEffect(player, materialId)) return;

        if (config.isConsumeMaterial() && !player.getInventory().hasItem(materialId, 1)) {
            throw new GrasscutterException("widget is depleted", Retcode.RET_ITEM_COUNT_NOT_ENOUGH);
            // TODO: actually consume the material
        }

        // default to success. TODO: some other widgets may need handling?
    }
}
