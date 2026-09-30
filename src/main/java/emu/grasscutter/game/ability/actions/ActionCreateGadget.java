package emu.grasscutter.game.ability.actions;

import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.entity.create_config.CreateGadgetEntityConfig;
import emu.grasscutter.game.props.CampTargetType;
import org.anime_game_servers.multi_proto.gi.messages.ability.action.AbilityActionCreateGadget;
import lombok.val;
import teyvat.ability.config.actions.CreateGadget;

public class ActionCreateGadget extends AbilityActionHandler<CreateGadget> {

    @Override
    public Class<?> getType() {
        return CreateGadget.class;
    }

    @Override
    public boolean execute(Ability ability, CreateGadget action, byte[] abilityData, GameEntity<?> target) {
        if(!action.byServer) {
            logger.debug("Action not executed by server");

            return true;
        }

        var entity = ability.getOwner();
        AbilityActionCreateGadget createGadget;
        try {
            createGadget = AbilityActionCreateGadget.parseBy(abilityData, ability.getPlayerOwner().getSession().getVersion());
        } catch (Exception e) {
            return false;
        }

        val config = new CreateGadgetEntityConfig(action, createGadget)
            .setOwner(action.ownerIsTarget ? target : entity)
            .setGadgetId(action.gadgetID)
            .setCampId(action.campID)
            .setCampType(CampTargetType.getTypeByName(action.campTargetType).getValue());

        var entityCreated = new EntityGadget(entity.getScene(), config);

        entity.getScene().addEntity(entityCreated);

        logger.info("Gadget {} created at pos {} rot {}", action.gadgetID, entityCreated.getPosition(), entityCreated.getRotation());

        return true;
    }

}
