package emu.grasscutter.server.packet.recv.widget;

import emu.grasscutter.game.entity.create_config.CreateGadgetEntityConfig;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.modules.widget.WidgetModule;
import emu.grasscutter.game.modules.widget.manager.WidgetCoolDownManager;
import emu.grasscutter.game.modules.widget.manager.WidgetSpawnManager;
import emu.grasscutter.net.packet.GrasscutterException;
import emu.grasscutter.net.packet.TypedPacketPairHandler;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.utils.Position;
import org.anime_game_servers.multi_proto.gi.messages.general.Retcode;
import org.anime_game_servers.multi_proto.gi.messages.item.widget.WidgetCreateLocationInfo;
import org.anime_game_servers.multi_proto.gi.messages.item.widget.WidgetCreatorOpType;
import org.anime_game_servers.multi_proto.gi.messages.item.widget.use.WidgetDoBagReq;
import org.anime_game_servers.multi_proto.gi.messages.item.widget.use.WidgetDoBagRsp;

import java.util.Objects;

public class HandlerWidgetDoBagReq extends TypedPacketPairHandler<WidgetDoBagReq, WidgetDoBagRsp> {

    @Override
    public boolean handle(GameSession session, byte[] header, WidgetDoBagReq req, WidgetDoBagRsp rsp) {
        try {
            this.run(session, header, req, rsp);
        }
        catch (GrasscutterException ex) {
            rsp.setRetcode(ex.logAndGetRetcode(req));
        }
        return true;
    }

    public void run(GameSession session, byte[] header, WidgetDoBagReq req, WidgetDoBagRsp rsp) throws GrasscutterException {
        var player = session.getPlayer();
        var materialId = req.getMaterialId();
        rsp.setMaterialId(materialId);
        WidgetCreateLocationInfo location = null;
        var op = WidgetCreatorOpType.WIDGET_CREATOR_TYPE_NONE;

        if (req.getOpInfo() instanceof WidgetDoBagReq.OpInfo.WidgetCreatorInfo widgetCreatorInfo) {
            location = widgetCreatorInfo.getValue().getLocationInfo();
            op = widgetCreatorInfo.getValue().getOpType();
        }
        else if (req.getOpInfo() instanceof WidgetDoBagReq.OpInfo.LocationInfo locationInfo) {
            location = locationInfo.getValue();
        }

        var config = WidgetModule.getWidgetConfigMap().get(materialId);
        if (config == null) {
            throw new GrasscutterException("widget not found in config", Retcode.RET_INVALID_WIDGET_MATERIAL_ID);
        }
        if (WidgetCoolDownManager.isCoolingDown(player, config)) {
            throw new GrasscutterException("widget is on cooldown", Retcode.RET_INVALID_QUICK_USE_WIDGET);
        }
        if (config.isConsumeMaterial() && !player.getInventory().hasItem(materialId, 1)) {
            throw new GrasscutterException("widget is depleted", Retcode.RET_ITEM_COUNT_NOT_ENOUGH);
        }

        try {
            // To-be-created gadget
            var gadget = new CreateGadgetEntityConfig(config.getGadgetId())
                .setPlayerOwner(player);

            var isUnhandled = false;
            switch (config.getType()) {
                case ConfigWidgetToyAdeptiAbode, ConfigWidgetFireworksLauncher -> {
                    Objects.requireNonNull(location);
                    WidgetSpawnManager.spawnGadgetEntity(player, materialId, op, gadget
                        .setBornPos(new Position(location.getPos()))
                        .setBornRot(new Position(location.getRot()))
                    );
                }
                default -> isUnhandled = true;
            }

            if (isUnhandled) {
                var gadgetId = config.getGadgetId();
                if (location != null && gadgetId != 0 && (config.getDoBagType() != null || config.isConsumeMaterial())) {
                    // Fallback to spawn gadget at position, this may be incorrect for some gadgets
                    WidgetSpawnManager.spawnGadgetEntity(player, materialId, op, gadget
                        .setBornPos(new Position(location.getPos()))
                        .setBornRot(new Position(location.getRot()))
                    );
                    isUnhandled = false;
                }
            }

            if (isUnhandled) {
                throw new GrasscutterException("widget is not supported: " + materialId);
            }

            // Consume material on success
            if (config.isConsumeMaterial()) {
                player.getInventory().removeItem(new GameItem(materialId), 1);
            }

            WidgetCoolDownManager.setCoolDownSeconds(player, config, true);
        }
        catch (Exception ex) {
            WidgetCoolDownManager.setCoolDownSeconds(player, config, false);
            throw ex;
        }
    }
}
