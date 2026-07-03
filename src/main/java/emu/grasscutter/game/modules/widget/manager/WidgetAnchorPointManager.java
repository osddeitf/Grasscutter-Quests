package emu.grasscutter.game.modules.widget.manager;

import dev.morphia.annotations.Entity;
import dev.morphia.query.filters.Filters;
import emu.grasscutter.database.DatabaseManager;
import emu.grasscutter.game.modules.GameModuleData;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.create_config.CreateGadgetEntityConfig;
import emu.grasscutter.game.modules.widget.WidgetModule;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.packet.GrasscutterException;
import emu.grasscutter.server.packet.send.widget.PacketAnchorPointDataNotify;
import emu.grasscutter.utils.DateHelper;
import emu.grasscutter.utils.Position;
import lombok.Getter;
import lombok.NonNull;
import org.anime_game_servers.multi_proto.gi.messages.general.Retcode;
import org.anime_game_servers.multi_proto.gi.messages.item.widget.anchor_point.AnchorPointData;
import org.anime_game_servers.multi_proto.gi.messages.scene.VisionType;

import java.util.*;

@Entity(value = GameModuleData.COLLECTION, discriminator = "grasscutter.widget.anchor_point")
public class WidgetAnchorPointManager extends GameModuleData {
    private static final int ANCHOR_POINT_COOL_DOWN = 60;
    private static final int ANCHOR_POINT_LASTING_TIME = 604800;
    private static final Set<Integer> ANCHOR_POINT_INVALID_SCENE_IDS = Set.of(4, 10, 100);  // exclude event limited maps

    @Getter
    @Entity
    public static class AnchorPoint {
        int id;
        int sceneId;
        int materialId;
        int gadgetId;
        int createdTime;
        Position pos;
        Position rot;

        public int getExpireTime() {
            return DateHelper.getFutureTimeSecond(ANCHOR_POINT_LASTING_TIME);
        }

        public AnchorPointData toProto() {
            var proto = new AnchorPointData();
            proto.setPos(pos.toProto());
            proto.setRot(rot.toProto());
            proto.setAnchorPointId(id);
            proto.setSceneId(sceneId);
            proto.setMaterialId(materialId);
            proto.setEndTime(getExpireTime());
            return proto;
        }
    }

    // morphia-only
    private WidgetAnchorPointManager() {
        super(0);
    }

    private WidgetAnchorPointManager(@NonNull Player player) {
        super(player.getUid());
    }

    @Getter private final LinkedList<AnchorPoint> pointList = new LinkedList<>();
    @Getter private final Map<Integer, Integer> nextUsableTime = new HashMap<>();
    private int counter;

    public static WidgetAnchorPointManager getInstance(@NonNull Player player) {
        return player.getWorld().getAnchorPointManager();
    }

    public static void teleportToAnchorPoint(@NonNull Player player, int id) throws GrasscutterException {
        var self = getInstance(player);
        var point = self.getPointList().stream()
            .filter(p -> p.getId() == id)
            .findFirst()
            .orElse(null);

        if (point == null) {
            throw new GrasscutterException("anchor point not found", Retcode.RET_WIDGET_ANCHOR_POINT_NOT_FOUND);
        }

        var world = player.getWorld();
        world.transferPlayerToScene(player, point.getSceneId(), point.getPos(), point.getRot());
    }

    public static void spawnAnchorPoint(@NonNull Player player, int materialId) throws GrasscutterException {
        if (player.getWorld().getHost() != player) {
            // another Retcode maybe a better fit
            throw new GrasscutterException("player is not the host", Retcode.RET_MP_NOT_IN_MY_WORLD);
        }

        var config = WidgetModule.getWidgetConfigMap().get(materialId);
        if (config == null || config.getGadgetId() == 0) {
            throw new GrasscutterException("missing config for material " + materialId, Retcode.RET_WIDGET_ANCHOR_POINT_NOT_FOUND);
        }

        // TODO: verify valid scenes
        var self = getInstance(player);
        var scene = player.getScene();

        // TODO: reduce item count for Portable Waypoint
        var point = new AnchorPoint();
        point.id = ++self.counter;
        point.sceneId = scene.getId();
        point.materialId = materialId;
        point.gadgetId = config.getGadgetId();
        point.createdTime = DateHelper.getCurrentTimeSecond();
        point.pos = new Position(player.getPosition());
        point.rot = new Position(player.getRotation());
        self.pointList.add(point);
        self.nextUsableTime.put(materialId, point.createdTime + ANCHOR_POINT_COOL_DOWN);

        // TODO: implement limits and remove old entities
        saveToDatabase(self);
        scene.addEntity(createAnchorPointEntity(scene, point));
        player.getWorld().broadcastPacket(new PacketAnchorPointDataNotify(player));
    }

    public static void spawnSavedEntities(@NonNull Player player) {
        var self = getInstance(player);
        var anyRemoved = self.pointList.removeIf(point -> point.getExpireTime() > DateHelper.getCurrentTimeSecond());
        if (anyRemoved) {
            // save upon any points are expired
            saveToDatabase(self);
        }

        // TODO: remove entities when they expire
        var scene = player.getScene();
        var entities = self.pointList.stream()
            .filter(point -> point.sceneId == scene.getId())
            .map(point -> createAnchorPointEntity(scene, point))
            .toList();
        scene.addEntities(entities, VisionType.VISION_REFRESH);
    }

    private static EntityGadget createAnchorPointEntity(Scene scene, AnchorPoint point) {
        var config = new CreateGadgetEntityConfig(point.gadgetId)
            .setBornPos(point.getPos())
            .setBornRot(point.getRot())
            .setEnableInteract(true);
        return new EntityGadget(scene, config);
    }

    public static WidgetAnchorPointManager loadFromDatabase(@NonNull Player player) {
        // Experimental module system persists only on the Morphia backend for now (Phase 6).
        if (DatabaseManager.getAccountDatastore() == null) return new WidgetAnchorPointManager(player);
        var saved = DatabaseManager.getAccountDatastore()
            .find(WidgetAnchorPointManager.class)
            .filter(Filters.eq("ownerId", player.getUid()))
            .first();
        return Objects.requireNonNullElseGet(saved, () -> new WidgetAnchorPointManager(player));
    }

    private static void saveToDatabase(WidgetAnchorPointManager instance) {
        if (DatabaseManager.getGameDatastore() == null) return;
        DatabaseManager.getGameDatastore().save(instance);
    }
}
