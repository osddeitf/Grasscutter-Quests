package emu.grasscutter.game.ability.actions;

import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.GameEntity;
import lombok.val;
import teyvat.ability.config.actions.KillServerGadget;

/**
 * Kills the server gadgets of the ability's owner with the gadget id, e.g. the Geo Hypostasis's rock pillars
 * (Monster_Effigy_Rock_KillPillar). Some abilities first kill the ones the client has, by a KillSelf with otherTargets
 * of the same gadget id, sent as a KillSelf for each; this is for the server's, e.g. those the client doesn't have.
 * The invoke carries no data, and targets the owner itself.
 * Assumed: the owner's "children" (SelectTargetsByChildren) are the gadgets it owns.
 * TODO unverified: not seen killing anything yet, in the case seen (the Geo Hypostasis's pillars) KillSelf had killed
 *  them all already, and no official capture shows it
 */
public class ActionKillServerGadget extends AbilityActionHandler<KillServerGadget> {

    @Override
    public Class<?> getType() {
        return KillServerGadget.class;
    }

    @Override
    public boolean execute(Ability ability, KillServerGadget action, byte[] abilityData, GameEntity<?> target) {
        val owner = ability.getOwner();
        val scene = owner.getScene();
        val gadgetId = action.gadgetInfo.configID;
        val gadgets = scene.getEntities().values().stream()
            .filter(entity -> entity instanceof EntityGadget gadget
                && gadget.getGadgetId() == gadgetId && gadget.getOwnerEntityId() == owner.getId())
            .toList();
        gadgets.forEach(scene::killEntity);
        logger.debug("KillServerGadget: {} gadget(s) {} of {} killed, at {}", gadgets.size(), gadgetId, owner.getId(), ability.getName());
        return true;
    }

}
