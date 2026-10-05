package emu.grasscutter.scripts.scriptlib_handlers.gadget;

import emu.grasscutter.Loggers;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.entity.EntityVehicle;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.entity.create_config.CreateGadgetEntityConfig;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.VehicleType;
import emu.grasscutter.scripts.lua_engine.GroupEventLuaContext;
import emu.grasscutter.scripts.scriptlib_handlers.BaseHandler;
import emu.grasscutter.server.packet.send.PacketScenePlayerLocationNotify;
import emu.grasscutter.utils.Position;
import lombok.Getter;
import lombok.val;
import org.anime_game_servers.core.gi.models.Vector;
import org.anime_game_servers.gi_lua.script_lib.handler.gadget.VehicleScriptHandler;
import org.anime_game_servers.multi_proto.gi.messages.scene.VisionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;

public class VehicleHandler extends BaseHandler implements VehicleScriptHandler<GroupEventLuaContext> {
    @Getter
    private static final Logger logger = Loggers.getScriptSystem();

    @Override
    public int createVehicle(@NotNull GroupEventLuaContext context, int uid, int gadgetId, @NotNull Vector position, @NotNull Vector rot) {
        val scene = context.getSceneScriptManager().getScene();
        val player = scene.getPlayers().stream().filter(p -> p.getUid() == uid).findFirst().orElse(null);
        if (player == null) {
            logger.warn("[LUA] CreateVehicle player {} not in scene {}", uid, scene.getId());
            return 1;
        }

        val createConfig = new CreateGadgetEntityConfig(gadgetId)
            .setBornPos(new Position(position))
            .setBornRot(new Position(rot))
            .setPlayerOwner(player);
        val vehicle = new EntityVehicle(scene, createConfig);

        // Saurian indwelling: called from the saurian (the source), for the indwelling avatar (the target)
        val vehicleConfig = vehicle.getVehicleConfig();
        if (vehicleConfig != null && vehicleConfig.getVehicleType() == VehicleType.Natsaurus
            && scene.getEntityById(context.getArgs().getSourceEntityId()) instanceof EntityMonster monster) {
            return transferToVehicle(player, vehicle, monster, scene.getEntityById(context.getArgs().getTargetEntityId()));
        }

        scene.addEntity(vehicle);
        return 0;
    }

    /** The avatar takes over the monster, which the vehicle replaces */
    private int transferToVehicle(Player player, EntityVehicle vehicle, EntityMonster monster, @Nullable GameEntity<?> target) {
        // For now, only an avatar of the player's own team
        if (!(target instanceof EntityAvatar avatar) || !player.getTeamManager().getActiveTeam().contains(avatar)) {
            logger.warn("[LUA] CreateVehicle transfer target {} isn't an avatar of player {}'s team", target, player.getUid());
            return 1;
        }

        val scene = player.getScene();
        vehicle.setTransformEntityId(monster.getId());
        vehicle.setCurPhlogiston(EntityVehicle.MAX_PHLOGISTON); // starts full
        scene.addEntity(vehicle);

        // The saurian is considered killed, with VISION_MISS as the official does. It may reappear on changing scene,
        // or through the scene's usual entity persistence
        scene.removeEntities(List.of(monster), VisionType.VISION_MISS);

        // Switching from another vehicle: entering gets fully off it first, as getting off does
        vehicle.enter(player, avatar.getAvatar().getGuid());
        player.sendPacket(new PacketScenePlayerLocationNotify(scene));
        return 0;
    }
}
