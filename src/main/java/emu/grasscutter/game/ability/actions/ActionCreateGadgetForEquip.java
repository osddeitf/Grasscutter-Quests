package emu.grasscutter.game.ability.actions;

import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.entity.EntityWeapon;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.entity.create_config.CreateGadgetEntityConfig;
import lombok.val;
import org.anime_game_servers.multi_proto.gi.messages.ability.action.AbilityActionCreateGadget;
import teyvat.ability.config.ConfigAbilityAction;
import teyvat.ability.config.actions.TriggerCreateGadgetToEquipPart;

public class ActionCreateGadgetForEquip extends AbilityActionHandler<TriggerCreateGadgetToEquipPart> {

    @Override
    public Class<?> getType() {
        return TriggerCreateGadgetToEquipPart.class;
    }

    @Override
    public boolean execute(Ability ability, TriggerCreateGadgetToEquipPart action, byte[] abilityData, GameEntity<?> target) {
       // if(!action.byServer) {
        //    logger.debug("Action not executed by server");

          //  return true;
        //}

        // TODO not working yet
        var entity = ability.getOwner();
        AbilityActionCreateGadget createGadget;
        try {
            createGadget = AbilityActionCreateGadget.parseBy(abilityData, ability.getPlayerOwner().getSession().getVersion());
        } catch (Exception e) {
            return false;
        }

        val config = new CreateGadgetEntityConfig(action, createGadget);
//            .setOwner(action.ownerIsTarget ? target : entity);    // TriggerCreateGadgetToEquipPart doesn't have the field

        var entityCreated = new EntityWeapon(entity.getScene(), config);

        /*if(action.ownerIsTarget)
            entityCreated.par(target);
        else
        entityCreated.setOwner(entity);*/


        entity.getScene().addEntity(entityCreated);
        if(target instanceof EntityMonster monster){
            //monster.setWeaponEntity(entityCreated);
        }

        logger.info("Gadget {} created at pos {} rot {}", action.gadgetID, entityCreated.getPosition(), entityCreated.getRotation());

        return true;
    }

}
