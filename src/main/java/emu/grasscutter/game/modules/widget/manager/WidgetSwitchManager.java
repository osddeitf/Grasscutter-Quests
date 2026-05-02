package emu.grasscutter.game.modules.widget.manager;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Property;
import dev.morphia.query.filters.Filters;
import emu.grasscutter.database.DatabaseManager;
import emu.grasscutter.game.modules.GameModuleData;
import emu.grasscutter.game.modules.widget.WidgetModule;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.GrasscutterException;
import emu.grasscutter.server.packet.send.widget.PacketWidgetSlotChangeNotify;
import lombok.Getter;
import lombok.NonNull;
import org.anime_game_servers.multi_proto.gi.messages.general.Retcode;
import org.anime_game_servers.multi_proto.gi.messages.item.widget.manage_slot.WidgetSlotData;
import org.anime_game_servers.multi_proto.gi.messages.item.widget.manage_slot.WidgetSlotOp;
import org.anime_game_servers.multi_proto.gi.messages.item.widget.manage_slot.WidgetSlotTag;

import java.util.*;
import java.util.stream.Collectors;

@Entity(value = GameModuleData.COLLECTION, discriminator = "grasscutter.widget.slots")
public class WidgetSwitchManager extends GameModuleData {
    static private final int OPEN_STATE_WIDGET_QUICK_SWITCH_UNLOCK = 3702;
    static private final int QUICK_SWITCH_SLOT_NUM = 4;

    // configured materials
    @Getter private int activeIndex;
    @Getter private List<Integer> materialList = List.of();
    @Property("experimental_slots_v1")
    private final Map<WidgetSlotTag, Integer> slots = new HashMap<>();

    // transient states
    private transient Player player;

    // morphia-only
    private WidgetSwitchManager() {
        super(0);
    }

    public WidgetSwitchManager(@NonNull Player player) {
        super(player.getUid());
        this.player = player;
    }

    public static WidgetSwitchManager getInstance(@NonNull Player player) {
        return player.getWidgetModule().getSwitchManager();
    }

    public static int getMaterialId(@NonNull Player player) {
        return getInstance(player).slots.getOrDefault(WidgetSlotTag.WIDGET_SLOT_QUICK_USE, 0);
    }

    public static void attachMaterial(@NonNull Player player, int materialId) throws GrasscutterException {
        var self = getInstance(player);
        var config = WidgetModule.getWidgetConfigMap().get(materialId);
        if (config == null) {
            throw new GrasscutterException("missing config for material " + materialId, Retcode.RET_WIDGET_ANCHOR_POINT_NOT_FOUND);
        }

        // get current attached
        var curMaterialIds = config.getTags().stream()
            .<Integer>mapMulti((tag, c) -> {
                var curMaterialId = self.slots.get(tag.asSlotTag());
                if (curMaterialId != null) c.accept(curMaterialId);
            })
            .collect(Collectors.toSet());

        if (curMaterialIds.contains(materialId)) {
            throw new GrasscutterException("already attached", Retcode.RET_ALREADY_ATTACH_WIDGET);
        }

        // detach current material ids
        for (var it: curMaterialIds) self.detachMaterial(it);

        // update slots and notify change
        config.getTags().stream()
            .map(tag -> tag.asSlotTag())
            .forEach(tag -> {
                self.slots.put(tag, materialId);
                player.sendPacket(new PacketWidgetSlotChangeNotify(WidgetSlotOp.WIDGET_SLOT_OP_ATTACH, self.getSlotData(tag)));
            });

        // update the quick switch material list (for compatibility with 3.7+)
        self.compat_UpdateQuickSwitchSlot(materialId);

        // persist
        self.saveToDatabase();
    }

    public static void detachMaterial(@NonNull Player player, int materialId) throws GrasscutterException {
        var self = getInstance(player);
        if (!self.detachMaterial(materialId)) {
            throw new GrasscutterException("material not attached", Retcode.RET_ALREADY_DETACH_WIDGET);
        }
        self.saveToDatabase();
    }

    public static void unlockQuickSwitch(@NonNull Player player) {
        player.getProgressManager().setOpenStateFromServer(OPEN_STATE_WIDGET_QUICK_SWITCH_UNLOCK, 1);
    }

    public static void setQuickSwitch(@NonNull Player player, int slotIndex, List<Integer> materialList) {
        var self = getInstance(player);
        self.materialList = materialList;
        self.activeIndex = slotIndex;
        self.saveToDatabase();
    }

    public static List<WidgetSlotData> getSlotList(@NonNull Player player) {
        var self = getInstance(player);
        return List.of(
            self.getSlotData(WidgetSlotTag.WIDGET_SLOT_QUICK_USE),
            self.getSlotData(WidgetSlotTag.WIDGET_SLOT_ATTACH_AVATAR)
        );
    }

    private boolean detachMaterial(int materialId) {
        var slotTags = slots.keySet().stream()
            .filter(tag -> slots.get(tag) == materialId)
            .toList();
        if (slotTags.isEmpty()) return false;

        // detach and notify
        for (var tag: slotTags) {
            var notify = new PacketWidgetSlotChangeNotify(WidgetSlotOp.WIDGET_SLOT_OP_DETACH, getSlotData(tag));
            slots.remove(tag);
            player.sendPacket(notify);
        }

        compat_UpdateQuickSwitchSlot(materialId);
        return true;
    }

    // For compatibility, this effectively updates the current quick swap state.
    // For version 3.7+, when do quick swap, both SetWidgetSlotReq (first) and SetWidgetQuickSwitchReq (second)
    // will be sent by the client, so the slot data would be eventually consistent
    // (given that the necessary proto are correct!)
    private void compat_UpdateQuickSwitchSlot(int materialId) {
        var materials = new ArrayList<>(materialList);
        while (materials.size() < QUICK_SWITCH_SLOT_NUM) materials.add(0);
        activeIndex = Math.max(0, Math.min(activeIndex, QUICK_SWITCH_SLOT_NUM)); // just-in-case
        materials.set(activeIndex, materialId);
        materialList = List.copyOf(materials);
    }

    private WidgetSlotData getSlotData(WidgetSlotTag tag) {
        var proto = new WidgetSlotData();
        proto.setMaterialId(slots.getOrDefault(tag, 0));
        proto.setTag(tag);
        proto.setActive(proto.getMaterialId() != 0);
        proto.setCdOverTime(0);     // TODO: which widget uses countdown?
        return proto;
    }

    public static WidgetSwitchManager loadFromDatabase(@NonNull Player player) {
        var saved = DatabaseManager.getGameDatastore()
            .find(WidgetSwitchManager.class)
            .filter(Filters.eq("ownerId", player.getUid()))
            .first();

        if (saved == null) {
            return new WidgetSwitchManager(player);
        }

        // init if previously persisted
        saved.player = player;
        return saved;
    }

    private void saveToDatabase() {
        DatabaseManager.getGameDatastore().save(this);
    }
}
