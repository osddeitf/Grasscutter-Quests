package emu.grasscutter.game.ability;

import java.util.HashMap;
import java.util.Map;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.common.DynamicFloat;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.utils.Utils;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.Getter;
import org.anime_game_servers.multi_proto.gi.messages.general.ability.AbilityString;

import javax.annotation.Nullable;

public class Ability {
    @Getter private AbilityData data;
    @Getter private GameEntity<?> owner;
    @Getter private Player playerOwner;
    @Getter private final Int2ObjectOpenHashMap<DynamicFloat> overrideMap = new Int2ObjectOpenHashMap<>();
    @Getter private final Int2ObjectOpenHashMap<DynamicFloat> serverOverrideMap = new Int2ObjectOpenHashMap<>();

    public Ability(AbilityData data, GameEntity<?> owner, Player playerOwner) {
        this.data = data;
        this.owner = owner;
        this.playerOwner = playerOwner;
        data.initialize();
        refreshServerOverrideMap();
    }

    public void refreshServerOverrideMap() {
        if(this.data.abilitySpecials != null)
            for(var entry : this.data.abilitySpecials.entrySet())
                serverOverrideMap.put(
                    Utils.abilityHash(entry.getKey()),
                    new DynamicFloat(entry.getValue())
                );
        // TODO: investigate and implement overrides from AbilityOverrideExcelConfigData
    }

    public float evaluateValue(String key) {
        var hash = Utils.abilityHash(key);
        // TODO: implement a config, when set to true, get from server override map, then return early (ignore other map)
        var value = overrideMap.get(hash);
        if (value == null) return data.abilitySpecials == null ? 0f : data.abilitySpecials.getOrDefault(key, 0f);
        // continue evaluating the value
        return value.get(this);
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
