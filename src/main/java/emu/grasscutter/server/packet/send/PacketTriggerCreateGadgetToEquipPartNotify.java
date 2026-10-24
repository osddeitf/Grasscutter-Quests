package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.entity.EntityWeapon;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.net.packet.BaseTypedPacket;
import org.anime_game_servers.multi_proto.gi.messages.battle.TriggerCreateGadgetToEquipPartNotify;

public class PacketTriggerCreateGadgetToEquipPartNotify extends BaseTypedPacket<TriggerCreateGadgetToEquipPartNotify> {
    public PacketTriggerCreateGadgetToEquipPartNotify(GameEntity<?> owner, EntityWeapon gadget, String equipPart) {
        super(new TriggerCreateGadgetToEquipPartNotify());
        proto.setEntityId(owner.getId());
        proto.setGadgetId(gadget.getGadgetId());
        proto.setGadgetEntityId(gadget.getId());
        proto.setEquipPart(equipPart);
    }
}
