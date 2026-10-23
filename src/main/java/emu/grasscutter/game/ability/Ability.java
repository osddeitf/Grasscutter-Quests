package emu.grasscutter.game.ability;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.entity.create_config.CreateGadgetEntityConfig;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.CampTargetType;
import emu.grasscutter.utils.Utils;
import it.unimi.dsi.fastutil.ints.Int2FloatArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.Getter;
import lombok.val;
import org.anime_game_servers.multi_proto.gi.messages.general.ability.AbilityString;
import teyvat.ability.config.ConfigAbility;
import teyvat.ability.config.ConfigAbilityMixin;
import teyvat.ability.config.mixins.ServerCreateGadgetOnKillMixin;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nullable;

public class Ability {
    @Getter private ConfigAbility data;
    @Getter private GameEntity<?> owner;
    @Getter private Player playerOwner;
    @Getter private final Int2FloatArrayMap overrideMap = new Int2FloatArrayMap();
    @Getter private final Int2FloatArrayMap serverOverrideMap = new Int2FloatArrayMap();

    public Ability(ConfigAbility data, GameEntity<?> owner, Player playerOwner) {
        this.data = data;
        this.owner = owner;
        this.playerOwner = playerOwner;
//        data.initialize();
        refreshServerOverrideMap();
    }

    public void refreshServerOverrideMap() {
            for(var entry : this.data.getAbilitySpecials().entrySet())
                serverOverrideMap.put(
                    Utils.abilityHash(entry.getKey()),
                    (float)entry.getValue()
                );
        // TODO: investigate and implement overrides from AbilityOverrideExcelConfigData
    }

    public float evaluateValue(String key) {
        var hash = Utils.abilityHash(key);
        // TODO: implement a config, when set to true, get from server override map, then return early (ignore other map)
        if (!overrideMap.containsKey(hash)) {
            return data.getAbilitySpecials().get(key);
        }
        // continue evaluating the value
//        return overrideMap.get(hash).get(this);
        return 0;
    }

    public String getName() {
        return data.getAbilityName();
    }

    /** Once instanced on its owner: applies the ability's own mixins, see {@link AbilityModifierController#attach} for a modifier's */
    public void attach() {
        addOnKillMixins(this.owner, this, this.data.getMixins(), this);
    }

    /** Once removed from its owner */
    public void detach() {
        this.owner.removeOnKillEvent(this);
    }

    /** Adds the entity an on kill event, under the key, for the ServerCreateGadgetOnKillMixins among the mixins */
    static void addOnKillMixins(GameEntity<?> entity, Object key, List<ConfigAbilityMixin> mixins, Ability ability) {
        var createGadgetsOnKill = mixins.stream()
            .filter(ServerCreateGadgetOnKillMixin.class::isInstance).map(ServerCreateGadgetOnKillMixin.class::cast).toList();
        if (!createGadgetsOnKill.isEmpty()) {
            entity.addOnKillEvent(key, () -> createGadgetsOnKill.forEach(mixin -> createGadgetsOnKill(entity, mixin, ability)));
        }
    }

    /**
     * Creates the mixin's gadgets (a random one of them with randomCreate) where the killed entity is, the killed entity
     * being their prop owner.
     * TODO: lifeByOwnerIsAlive isn't handled, unclear whose life it is as the creator is the killed entity. It's never
     *  true in the 4.8 or 7.0 data
     */
    private static void createGadgetsOnKill(GameEntity<?> entity, ServerCreateGadgetOnKillMixin mixin, Ability ability) {
        if (mixin.gadgetIDList.isEmpty()) return;
        val scene = entity.getScene();
        val gadgetIds = mixin.randomCreate
            ? List.of(mixin.gadgetIDList.get(ThreadLocalRandom.current().nextInt(mixin.gadgetIDList.size())))
            : mixin.gadgetIDList;
        for (int gadgetId : gadgetIds) {
            val config = new CreateGadgetEntityConfig(gadgetId)
                .setBornPos(entity.getPosition().clone())
                .setBornRot(entity.getRotation().clone())
                .setCampId(mixin.campID)
                .setCampType(CampTargetType.getTypeByName(mixin.campTargetType).getValue())
                .setPropOwnerEntityId(entity.getId())
                .setCreator(entity);
            if (config.getGadgetData() == null) {
                AbilityManager.getLogger().warn("[MISSING] ServerCreateGadgetOnKillMixin: gadget {} not found, at {}", gadgetId, ability.getName());
                continue;
            }
            scene.addEntity(new EntityGadget(scene, config));
        }
    }

    @Nullable
    public static String getAbilityName(AbilityString abString) {
        if (abString == null){
            Grasscutter.getLogger().error("AbilityString is null");
            return null;
        }
        if(abString.getType() instanceof AbilityString.Type.Str abStr){
            return abStr.getValue();
        }if(abString.getType() instanceof AbilityString.Type.Hash abHash){
            return GameData.getAbilityHashes().get((int)abHash.getValue());
        }
        return null;
    }

    public static int getAbilityHash(AbilityString abString) {
        if (abString == null) {
            return 0;
        }
        if (abString.getType() instanceof AbilityString.Type.Str abStr){
            return Utils.abilityHash(abStr.getValue());
        }
        if (abString.getType() instanceof AbilityString.Type.Hash abHash){
            return abHash.getValue();
        }
        return 0;
    }
}
