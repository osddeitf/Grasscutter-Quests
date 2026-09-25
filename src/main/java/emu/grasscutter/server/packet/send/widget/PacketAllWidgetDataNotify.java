package emu.grasscutter.server.packet.send.widget;

import emu.grasscutter.game.modules.widget.manager.WidgetSwitchManager;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BaseTypedPacket;
import org.anime_game_servers.multi_proto.gi.messages.item.widget.AllWidgetDataNotify;
import org.anime_game_servers.multi_proto.gi.messages.item.widget.lunch_box.LunchBoxData;

import java.util.List;

public class PacketAllWidgetDataNotify extends BaseTypedPacket<AllWidgetDataNotify> {
    public PacketAllWidgetDataNotify(Player player) {
        super(new AllWidgetDataNotify());
        // TODO: Implement this

            // If you want to implement this, feel free to do so. :)
        proto.setLunchBoxData(new LunchBoxData());
            // Maybe it's a little difficult, or it makes you upset :(
        proto.setOneOfGatherPointDetectorDataList(List.of());
            // So, goodbye, and hopefully sometime in the future o(*￣▽￣*)ブ
        proto.setCoolDownGroupDataList(List.of());
            // I'll see your PR with a title that says (・∀・(・∀・(・∀・*)
        proto.setAnchorPointList(List.of());
            // "Complete implementation of widget functionality" b（￣▽￣）d　
        proto.setClientCollectorDataList(List.of());
            // Good luck, my boy.
        proto.setNormalCoolDownDataList(List.of());

        proto.setSlotList(WidgetSwitchManager.getSlotList(player));
    }
}

