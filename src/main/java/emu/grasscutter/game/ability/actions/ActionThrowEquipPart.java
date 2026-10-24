package emu.grasscutter.game.ability.actions;

import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.entity.GameEntity;
import teyvat.ability.config.actions.TriggerThrowEquipPart;

/**
 * Throws the gadget held at an equip part, e.g. a hilichurl's slime (Hili_Fire_BombThrow). The client creates the thrown
 * gadget itself, by EvtCreateGadgetNotify, and the official server sends nothing for it: the held gadget is only
 * released. That it's released on the throw is assumed: the captures have no throw of a dug up slime
 */
public class ActionThrowEquipPart extends AbilityActionHandler<TriggerThrowEquipPart> {

    @Override
    public Class<?> getType() {
        return TriggerThrowEquipPart.class;
    }

    @Override
    public boolean execute(Ability ability, TriggerThrowEquipPart action, byte[] abilityData, GameEntity<?> target) {
        if (ability.getOwner() instanceof EntityMonster monster && monster.removeEquipPartGadget(action.equipPart) == null) {
            logger.debug("Equip part {} of {} holds nothing to throw", action.equipPart, monster.getId());
        }
        return true;
    }

}
