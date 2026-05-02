package emu.grasscutter.game.modules.widget.manager;

import dev.morphia.annotations.Entity;
import dev.morphia.query.filters.Filters;
import emu.grasscutter.database.DatabaseManager;
import emu.grasscutter.game.modules.GameModuleData;
import emu.grasscutter.game.modules.widget.effect.WidgetEffectManager;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.widget.PacketAllWidgetBackgroundActiveStateNotify;
import lombok.Getter;
import lombok.NonNull;

import java.util.Set;

@Entity(value = GameModuleData.COLLECTION, discriminator = "grasscutter.widget.background_active")
public class WidgetBackgroundActiveManager extends GameModuleData {
    @Getter private Set<Integer> activeMaterials;
    private transient Player player;

    public WidgetBackgroundActiveManager(@NonNull Player player) {
        super(player.getUid());
        this.player = player;
    }

    public static WidgetBackgroundActiveManager getInstance(@NonNull Player player) {
        return player.getWidgetModule().getBackgroundActiveManager();
    }

    public static void setActive(@NonNull Player player, int materialId, boolean active) {
        var self = getInstance(player);
        if (active) {
            self.activeMaterials.add(materialId);
            WidgetEffectManager.registerEffect(player, materialId);
        }
        else {
            self.activeMaterials.remove(materialId);
            WidgetEffectManager.deregisterEffect(player, materialId);
        }

        self.saveToDatabase();
        player.sendPacket(new PacketAllWidgetBackgroundActiveStateNotify(player));
    }

    public static WidgetBackgroundActiveManager loadFromDatabase(@NonNull Player player) {
        var saved = DatabaseManager.getGameDatastore()
            .find(WidgetBackgroundActiveManager.class)
            .filter(Filters.eq("ownerId", player.getUid()))
            .first();

        if (saved == null) {
            return new WidgetBackgroundActiveManager(player);
        }

        // init if previously persisted
        saved.player = player;
        Set.copyOf(saved.activeMaterials).forEach(materialId ->
            WidgetEffectManager.registerEffect(player, materialId)
        );
        return saved;
    }

    private void saveToDatabase() {
        DatabaseManager.getGameDatastore().save(this);
    }
}
