package emu.grasscutter.game.modules.widget.manager;

import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.create_config.CreateGadgetEntityConfig;
import emu.grasscutter.game.player.Player;
import lombok.NonNull;
import org.anime_game_servers.multi_proto.gi.messages.item.widget.WidgetCreatorOpType;

import java.util.HashMap;
import java.util.Map;

public class WidgetSpawnManager {
    private final Map<Integer, Integer> entityMap = new HashMap<>();

    public static WidgetSpawnManager getInstance(@NonNull Player player) {
        return player.getWidgetModule().getSpawnManager();
    }

    public static void spawnGadgetEntity(@NonNull Player player, int materialId, WidgetCreatorOpType op, CreateGadgetEntityConfig specs) {
        var self = getInstance(player);
        var shouldRetract = op != WidgetCreatorOpType.WIDGET_CREATOR_TYPE_NONE;
        var shouldCreate = op != WidgetCreatorOpType.WIDGET_CREATOR_TYPE_RETRACT;
        var scene = player.getScene();

        if (shouldRetract) {
            var id = self.entityMap.getOrDefault(materialId, 0);
            var entity = scene.getEntityById(id);
            if (entity != null) {
                player.getScene().killEntity(entity);
            }
        }

        // TODO: send WidgetGadgetDestroyNotify and WidgetGadgetDataNotify
        if (shouldCreate) {
            var entity = new EntityGadget(scene, specs);
            scene.addEntity(entity);
            self.entityMap.put(materialId, entity.getId());
        }
    }
}
