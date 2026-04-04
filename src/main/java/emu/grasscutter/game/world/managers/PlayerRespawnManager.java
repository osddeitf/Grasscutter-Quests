package emu.grasscutter.game.world.managers;

import emu.grasscutter.data.excels.DungeonData;
import emu.grasscutter.game.dungeons.DungeonManager;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.EnterReason;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.data.TeleportProperties;
import emu.grasscutter.server.event.player.PlayerTeleportEvent;
import emu.grasscutter.utils.Position;
import lombok.val;
import org.anime_game_servers.multi_proto.gi.messages.scene.EnterType;

import java.util.Optional;

public class PlayerRespawnManager {
    private final Scene scene;

    public PlayerRespawnManager(Scene scene) {
        this.scene = scene;
    }

    public void tick() {
        if (scene.getScriptManager().getConfig() == null) return;

        val diePos = scene.getScriptManager().getConfig().getDieY();
        // Collect before mutating to avoid modifying collections during iteration
        val toRespawn = scene.getPlayers().stream().filter(p -> diePos >= p.getPosition().getY()).toList();
        toRespawn.forEach(this::respawnPlayer);
        val toKill = scene.getEntities().values().stream().filter(e -> diePos >= e.getPosition().getY()).toList();
        toKill.forEach(scene::killEntity);
    }

    public boolean respawnPlayer(Player player) {
        player.getTeamManager().onAvatarDieDamage();

        return scene.getWorld().transferPlayerToScene(player, TeleportProperties.builder()
            .sceneId(scene.getId())
            .prevSceneId(scene.getId())
            .prevPos(player.getPosition())
            .teleportTo(getRespawnLocation(player))
            .teleportRot(getRespawnRotation(player))
            .teleportType(PlayerTeleportEvent.TeleportType.INTERNAL)
            .worldType(Optional.ofNullable(scene.getDungeonManager()).map(data -> 13).orElse(14))
            .enterType(EnterType.ENTER_GOTO)
            .dungeonId(Optional.ofNullable(scene.getDungeonManager()).map(DungeonManager::getDungeonData).map(DungeonData::getId).orElse(0))
            .enterReason(scene.getDungeonManager() != null ? EnterReason.DungeonReviveOnWaypoint : EnterReason.Revival)
            .build());
    }

    private Position getDefaultLocation(Player player) {
        val bornPos = scene.getScriptManager().getConfig().getBornPos();
        return bornPos != null ? new Position(bornPos) : player.getPosition();
    }

    private Position getDefaultRot(Player player) {
        val bornRot = scene.getScriptManager().getConfig().getBornRot();
        return bornRot != null ? new Position(bornRot) : player.getRotation();
    }

    private Position getRespawnLocation(Player player) {
        return Optional.ofNullable(scene.getDungeonManager()).map(DungeonManager::getRespawnLocation).orElse(getDefaultLocation(player));
    }

    private Position getRespawnRotation(Player player) {
        return Optional.ofNullable(scene.getDungeonManager()).map(DungeonManager::getRespawnRotation).orElse(getDefaultRot(player));
    }
}
