package emu.grasscutter.game.entity.gadget.content;

import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.player.Player;
import lombok.val;
import org.anime_game_servers.multi_proto.gi.messages.gadget.GadgetInteractReq;
import org.anime_game_servers.multi_proto.gi.messages.scene.entity.AbilityGadgetInfo;
import org.anime_game_servers.multi_proto.gi.messages.scene.entity.SceneGadgetInfo;

/** A gadget created by an ability action, e.g. Albedo's elevator by CreateMovingPlatform */
public class GadgetAbility extends GadgetContent {

    public GadgetAbility(EntityGadget gadget) {
        super(gadget);
    }

    public boolean onInteract(Player player, GadgetInteractReq req) {
        return false;
    }

    @Override
    public void onBuildProto(SceneGadgetInfo gadgetInfo) {
        val gadget = getGadget();
        val abilityGadgetInfo = new AbilityGadgetInfo(gadget.getCampId(), gadget.getCampType(), gadget.getTargetEntityId());
        gadgetInfo.setContent(new SceneGadgetInfo.Content.AbilityGadget(abilityGadgetInfo));
    }

}
