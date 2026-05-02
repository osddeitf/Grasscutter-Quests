package emu.grasscutter.server.packet.send.widget;

import emu.grasscutter.game.modules.widget.manager.WidgetBackgroundActiveManager;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BaseTypedPacket;
import lombok.NonNull;
import org.anime_game_servers.multi_proto.gi.messages.item.widget.type_background.AllWidgetBackgroundActiveStateNotify;

import java.util.List;

public class PacketAllWidgetBackgroundActiveStateNotify extends BaseTypedPacket<AllWidgetBackgroundActiveStateNotify> {
    public PacketAllWidgetBackgroundActiveStateNotify(@NonNull Player player) {
        super(new AllWidgetBackgroundActiveStateNotify());
        proto.setBackgroundActiveWidgetList(List.copyOf(
            WidgetBackgroundActiveManager.getInstance(player).getActiveMaterials()
        ));
    }
}
