package emu.grasscutter.game.modules.widget;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.utils.Utils;
import lombok.Getter;
import lombok.NonNull;
import org.anime_game_servers.multi_proto.gi.messages.item.widget.manage_slot.WidgetSlotTag;

import java.util.List;
import java.util.Set;

public class WidgetConfig {

    @SerializedName("$type")
    @Getter private WidgetConfigType type;
    @Getter private String abilityGroupName;        // abilities added on equip
    @Getter private String abilityGroupNameSecond;  // abilities added on trigger
    @Getter private int gadgetId;
    @Getter private Set<WidgetConfigTag> tags;
    @Getter private boolean isConsumeMaterial;
    @Getter private int cdGroup;
    @Getter private String doBagType;

    // custom config for abilities
    @Getter private List<String> avatarAbilities;
    @Getter private List<String> teamAbilities;
    @Getter private List<Integer> avatarHashAbilities;
    @Getter private List<Integer> teamHashAbilities;

    public boolean hasInitialAbilities() {
        return !Utils.isNullOrEmpty(abilityGroupName)
            || !Utils.isNullOrEmpty(avatarAbilities)
            || !Utils.isNullOrEmpty(teamAbilities)
            || !Utils.isNullOrEmpty(avatarHashAbilities)
            || !Utils.isNullOrEmpty(teamHashAbilities);
    }

    public WidgetCoolDownGroup getCoolDownConfig() {
        if (cdGroup == 0) return null;
        return WidgetModule.getWidgetCoolDownGroupMap().get(cdGroup);
    }

    public enum WidgetConfigTag {
        ActionPanel,
        FlyAttach;

        public WidgetSlotTag asSlotTag() {
            return switch (this) {
                case ActionPanel -> WidgetSlotTag.WIDGET_SLOT_QUICK_USE;
                case FlyAttach -> WidgetSlotTag.WIDGET_SLOT_ATTACH_AVATAR;
            };
        }
    }

    // TODO: this is incomplete
    public enum WidgetConfigType {
        // spawn a gadget, the client will send EvtCreateGadgetNotify
        ConfigWidgetToyGadgetBuilder,

        // flying objects
        ConfigWidgetToyAbilityGroupGenerator,

        // spawn gadgets (Retract/RetractAndCreate)
        ConfigWidgetToyAdeptiAbode,
        ConfigWidgetFireworksLauncher,
        ConfigWidgetToyBonfire,

        // attach ability gadgets
        ConfigWidgetUseAttachAbilityGroup,
        ConfigWidgetCaptureAnimal,

        // special gadgets (with their own data)
        ConfigWidgetToyAnchorPoint,
        ConfigWidgetToyWeather,
        ConfigWidgetToySorush,
        ConfigWidgetWispCage,
    }
}
