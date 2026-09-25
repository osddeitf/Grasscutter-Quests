package emu.grasscutter.game.modules.widget.manager;

import emu.grasscutter.game.modules.widget.WidgetConfig;
import emu.grasscutter.game.modules.widget.WidgetCoolDownGroup;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.widget.PacketWidgetCoolDownNotify;
import emu.grasscutter.utils.DateHelper;
import lombok.NonNull;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

public class WidgetCoolDownManager {

    private final Map<Integer, Long> coolDownMap = new HashMap<>();

    public static WidgetCoolDownManager getInstance(@NonNull Player player) {
        return player.getWidgetModule().getCoolDownManager();
    }

    public static boolean isCoolingDown(@NonNull Player player, WidgetConfig config) {
        var self = getInstance(player);
        var cooldown = config.getCoolDownConfig();
        if (cooldown == null) return false;
        var nextTime = self.coolDownMap.getOrDefault(config.getCdGroup(), 0L);
        return nextTime > getReferenceTime(player, cooldown);
    }

    public static void setCoolDownSeconds(@NonNull Player player, WidgetConfig config, boolean isSuccess) {
        var cooldown = config.getCoolDownConfig();
        if (cooldown == null) return;
        var seconds = isSuccess? cooldown.getCoolDownSuccess(): cooldown.getCoolDownFail();
        if (seconds > 0) {
            var self = getInstance(player);
            var nextTime = getReferenceTime(player, cooldown) + Duration.of(seconds, ChronoUnit.SECONDS).toMillis();
            self.coolDownMap.put(config.getCdGroup(), nextTime);
            player.sendPacket(new PacketWidgetCoolDownNotify(config.getCdGroup(), nextTime, isSuccess));
        }
    }

    private static long getReferenceTime(@NonNull Player player, @NonNull WidgetCoolDownGroup cooldown) {
        // TODO: behavior when teleport to a different scene?
        return cooldown.isUnderTimeScale()? player.getPlayerTime() : DateHelper.getCurrentTime();
    }
}
