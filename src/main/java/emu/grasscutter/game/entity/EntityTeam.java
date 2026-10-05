package emu.grasscutter.game.entity;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.ability.AbilityManager;
import emu.grasscutter.game.ability.embryos.TeamAbilityManager;
import emu.grasscutter.game.entity.interfaces.StringAbilityEntity;
import emu.grasscutter.game.managers.stamina.StaminaManager;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.game.props.EntityIdType;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.World;
import lombok.Getter;

import java.util.Collection;

public class EntityTeam extends MetaGameEntity implements StringAbilityEntity {

    @Getter private final Player player;
    @Getter private final TeamAbilityManager abilities;

    public EntityTeam(Player player) {
        super(player.getScene());
        this.player = player;
        initAbilities();
        this.id = player.getWorld().getNextEntityId(EntityIdType.TEAM);
        this.abilities = new TeamAbilityManager(this);

        /*
         * Full list as of v7.0:
         *  2558787905 SGV_PlayerTeam_Phlogiston
         *  1031130579 SGV_MoonPhaseLevel
         *  3698231495 SGV_MoonPhaseType
         *  1245369362 SGV_HexenzirkelLevel
         *  583619525 SGV_HexenzirkelLevel_Temp
         *  3430280279 SGV_SnezhnayaFrigid
         *  2318692774 SGV_SnezhnayaEnergy
         *  4148391354 SGV_SnezhnayaBuildResource
         **/
        // Server global values, sent with the team's ability info
        getGlobalAbilityValues().put(StaminaManager.PHLOGISTON_SGV, player.getProperty(PlayerProperty.PROP_CUR_PHLOGISTON) / 100f);
    }

    @Override
    public AbilityManager getAbilityTargetManager() {
        return getWorld().getHost().getAbilityManager();
    }

    @Override
    public Collection<String> getAbilityData() {
        // TODO: is this even needed anymore
        return GameData.getConfigGlobalCombat().getDefaultAbilities().getDefaultTeamAbilities();
    }

    @Override
    public World getWorld() {
        return player.getWorld();
    }

    /** The player's current scene: the team is created before the player enters one, and follows them across scenes */
    @Override
    public Scene getScene() {
        return player.getScene();
    }

    @Override
    public int getEntityTypeId() {
        return EntityIdType.TEAM.getId();
    }

}
