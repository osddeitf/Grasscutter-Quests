package emu.grasscutter.game.ability.actions;

import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.entity.EntityWeapon;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.entity.create_config.CreateGadgetEntityConfig;
import emu.grasscutter.server.packet.send.PacketTriggerCreateGadgetToEquipPartNotify;
import lombok.val;
import teyvat.ability.config.actions.TriggerCreateGadgetToEquipPart;

/**
 * A gadget held at an equip part of the ability's owner, e.g. the slime a hilichurl digs up to throw (Hili_Fire_BombCreate,
 * gadget 42101012 at equip03). As the official server: a weapon entity, not appearing in the scene, sent in
 * TriggerCreateGadgetToEquipPartNotify, from which the clients create it. The invoke carries no data.
 * The throw is the client's, creating the thrown gadget by EvtCreateGadgetNotify, see ActionThrowEquipPart.
 * TODO the held gadget isn't removed on its owner's death: not seen in the captures
 */
public class ActionCreateGadgetForEquip extends AbilityActionHandler<TriggerCreateGadgetToEquipPart> {

    @Override
    public Class<?> getType() {
        return TriggerCreateGadgetToEquipPart.class;
    }

    @Override
    public boolean execute(Ability ability, TriggerCreateGadgetToEquipPart action, byte[] abilityData, GameEntity<?> target) {
        val owner = ability.getOwner();
        val scene = owner.getScene();
        // the creator, as for a monster's weapon, for a gadget with useCreatorProperty
        val config = new CreateGadgetEntityConfig(action.gadgetID).setCreator(owner);
        val gadget = new EntityWeapon(scene, config);
        scene.getWeaponEntities().put(gadget.getId(), gadget);
        if (owner instanceof EntityMonster monster) monster.setEquipPartGadget(action.equipPart, gadget);
        scene.broadcastPacket(new PacketTriggerCreateGadgetToEquipPartNotify(owner, gadget, action.equipPart));
        return true;
    }

}
