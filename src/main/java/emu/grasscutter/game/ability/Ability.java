package emu.grasscutter.game.ability;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.DynamicFloat;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.utils.Utils;
import it.unimi.dsi.fastutil.ints.Int2FloatArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.Getter;
import org.anime_game_servers.multi_proto.gi.messages.general.ability.AbilityString;
import teyvat.ability.config.ConfigAbility;

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
