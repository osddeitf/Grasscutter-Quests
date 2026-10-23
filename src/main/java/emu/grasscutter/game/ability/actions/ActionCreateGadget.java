package emu.grasscutter.game.ability.actions;

import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.entity.create_config.CreateGadgetEntityConfig;
import emu.grasscutter.game.entity.gadget.platform.AbilityRoute;
import emu.grasscutter.game.props.CampTargetType;
import org.anime_game_servers.multi_proto.gi.messages.ability.action.AbilityActionCreateGadget;
import lombok.val;
import teyvat.ability.config.actions.CreateGadget;
import teyvat.ability.config.actions.CreateMovingPlatform;

/** Also runs CreateMovingPlatform, a CreateGadget whose gadget is a platform started and stopped by the client */
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

        val owner = action.ownerIsTarget ? target : entity;
        if (owner == null) {
            logger.warn("Gadget {} not created, its owner is missing, owner is target {}", action.gadgetID, action.ownerIsTarget);
            return false;
        }
        // it would never be killed with an owner already gone
        if (action.lifeByOwnerIsAlive && !owner.isInScene()) {
            logger.debug("Gadget {} not created, its owner {} has already left the scene", action.gadgetID, owner.getId());
            return true;
        }

        val config = new CreateGadgetEntityConfig(action, action.gadgetID, createGadget)
            .setOwner(owner)
            .setCreator(entity)
            .setTargetEntityId(target != null ? target.getId() : entity.getId())
            .setAbilityGadget(true)
            .setCampId(action.campID)
            .setCampType(CampTargetType.getTypeByName(action.campTargetType).getValue());
        if (action instanceof CreateMovingPlatform) {
            config.setRouteConfig(new AbilityRoute(config, false, false));
        }

        var entityCreated = new EntityGadget(entity.getScene(), config);
        if (action.lifeByOwnerIsAlive) {
            owner.addLifeBoundEntity(entityCreated);
        }

        entity.getScene().addEntity(entityCreated);

        logger.info("Gadget {} created at pos {} rot {}", action.gadgetID, entityCreated.getPosition(), entityCreated.getRotation());

        return true;
    }

}
