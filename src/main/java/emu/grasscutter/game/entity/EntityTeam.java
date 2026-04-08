package emu.grasscutter.game.entity;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.ability.AbilityManager;
import emu.grasscutter.game.ability.embryos.TeamAbilityManager;
import emu.grasscutter.game.entity.interfaces.StringAbilityEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.EntityIdType;
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

    @Override
    public int getEntityTypeId() {
        return EntityIdType.TEAM.getId();
    }

}
