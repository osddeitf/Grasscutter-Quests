package emu.grasscutter.game.ability.embryos;

import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import lombok.Getter;
import org.anime_game_servers.multi_proto.gi.messages.general.ability.AbilityControlBlock;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

public abstract class BaseAbilityManager {

    protected final AbilityEmbryoList abilityEmbryos = new AbilityEmbryoList();
    protected final Map<AbilityType, IntSet> abilities = new HashMap<>();
    @Getter protected boolean abilityChanged;

    public AbilityControlBlock toProto() {
        var proto = new AbilityControlBlock();
        proto.setAbilityEmbryoList(abilityEmbryos.toList());
        return proto;
    }

    protected void updateAbilityEmbryos(AbilityType type, IntStream hashes) {
        updateAbilityEmbryos(type, IntLinkedOpenHashSet.toSet(hashes));
    }

    protected void updateAbilityEmbryos(AbilityType type, IntSet hashes) {
        var prev = abilities.getOrDefault(type, IntSet.of());
        if (abilityEmbryos.updateAbilitySet(prev, hashes)) {
            abilities.put(type, hashes);
            abilityChanged = true;
        }
    }

    // internal enum
    protected enum AbilityType {
        DEFAULT,
        SKILL_DEPOT,
        OPEN_CONFIG,
        RESONANCE_MOON_SIGN,
        WORLD_AREA_LEVEL_1,
        WORLD_AREA_LEVEL_2,
        LEVEL_ENTITY,
        GALLERY,
        SCRIPT_LUA,
        DYNAMIC,
    }
}
