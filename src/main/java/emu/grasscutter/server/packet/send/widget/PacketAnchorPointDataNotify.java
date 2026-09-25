package emu.grasscutter.server.packet.send.widget;

import emu.grasscutter.game.modules.widget.manager.WidgetAnchorPointManager;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BaseTypedPacket;
import org.anime_game_servers.multi_proto.gi.messages.general.Uint32Pair;
import org.anime_game_servers.multi_proto.gi.messages.item.widget.anchor_point.AnchorPointDataNotify;

import javax.annotation.Nonnull;

public class PacketAnchorPointDataNotify extends BaseTypedPacket<AnchorPointDataNotify> {
    public PacketAnchorPointDataNotify(@Nonnull Player player) {
        super(new AnchorPointDataNotify());
        var nextUsableTime = WidgetAnchorPointManager
            .getInstance(player).getNextUsableTime().entrySet().stream()
            .map(point -> new Uint32Pair(point.getKey(), point.getValue()))
            .toList();;

        if (!nextUsableTime.isEmpty()) {
            // for versions prior to v6.3
            proto.setNextUsableTime(
                nextUsableTime.stream().mapToInt(Uint32Pair::getValue).max().getAsInt()
            );
            // v6.3+
            proto.setAnchorPointMaterialNextUsableTimeList(nextUsableTime);
        }

        proto.setAnchorPointList(
            WidgetAnchorPointManager.getInstance(player).getPointList().stream()
                .map(WidgetAnchorPointManager.AnchorPoint::toProto)
                .toList()
        );
    }
}
