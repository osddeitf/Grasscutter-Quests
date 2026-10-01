package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.ability.AbilityScalars;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BaseTypedPacket;
import lombok.val;
import org.anime_game_servers.multi_proto.gi.messages.general.ability.AbilityControlBlock;
import org.anime_game_servers.multi_proto.gi.messages.general.ability.AbilitySyncStateInfo;
import org.anime_game_servers.multi_proto.gi.messages.scene.AvatarEnterSceneInfo;
import org.anime_game_servers.multi_proto.gi.messages.scene.PlayerEnterSceneInfoNotify;
import org.anime_game_servers.multi_proto.gi.messages.scene.entity.MPLevelEntityInfo;

import java.util.Map;

public class PacketPlayerEnterSceneInfoNotify extends BaseTypedPacket<PlayerEnterSceneInfoNotify> {

	public PacketPlayerEnterSceneInfoNotify(Player player) {
		super(new PlayerEnterSceneInfoNotify());

		val empty = new AbilitySyncStateInfo();

		val teamAbilityInfo = new AbilitySyncStateInfo();
		Map<String, Float> teamGlobalValues = player.getTeamManager().getEntity().getGlobalAbilityValues();
		teamAbilityInfo.setSgvDynamicValueMap(teamGlobalValues.entrySet().stream()
			.map(entry -> AbilityScalars.from(entry.getKey(), entry.getValue()))
			.toList());

        proto.setCurAvatarEntityId(player.getTeamManager().getCurrentAvatarEntity().getId());
        proto.setEnterSceneToken(player.getEnterSceneToken());

		proto.setTeamEnterInfo(
				new org.anime_game_servers.multi_proto.gi.messages.scene.TeamEnterSceneInfo(
                    player.getTeamManager().getEntity().getId(),
                    teamAbilityInfo,
                    player.getTeamManager().getEntity().getAbilities().toProto()
                )
		);
		proto.setMpLevelEntityInfo(
				new MPLevelEntityInfo(
                    player.getWorld().getLevelEntityId(),
                    player.getWorld().getHostPeerId(),
                    empty
                )
		);

        proto.setAvatarEnterInfo(player.getTeamManager().getActiveTeam().stream().map(avatarEntity -> {
            val weapon = avatarEntity.getAvatar().getWeapon();
            long weaponGuid = weapon != null ? weapon.getGuid() : 0;

            val avatarInfo = new AvatarEnterSceneInfo(avatarEntity.getAvatar().getGuid(), avatarEntity.getId(), empty);
            avatarInfo.setWeaponGuid(weaponGuid);
            avatarInfo.setWeaponEntityId(avatarEntity.getWeaponEntityId());
            avatarInfo.setWeaponAbilityInfo(empty);
            return avatarInfo;
        }).toList());
	}
}
