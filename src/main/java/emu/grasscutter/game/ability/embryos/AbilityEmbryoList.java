package emu.grasscutter.game.ability.embryos;

import emu.grasscutter.GameConstants;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.anime_game_servers.multi_proto.gi.messages.general.ability.AbilityEmbryo;

import java.util.List;

public class AbilityEmbryoList {
    private int counter;
    private final Int2ObjectMap<AbilityEmbryo> embryoList = new Int2ObjectLinkedOpenHashMap<>();

    public List<AbilityEmbryo> toList() {
        return List.copyOf(embryoList.values());
    }

    public boolean updateAbilitySet(IntSet prev, IntSet list) {
        var modified = false;

        // Remove abilities no longer in the new set
        for (int hash : prev) {
            if (!list.contains(hash)) {
                remove(hash);
                modified = true;
            }
        }

        // Add abilities not in the previous set
        for (int hash : list) {
            if (!prev.contains(hash)) {
                add(hash);
                modified = true;
            }
        }

        return modified;
    }

    private void add(int abilityHash) {
        embryoList.computeIfAbsent(abilityHash, k -> new AbilityEmbryo(++counter, k, GameConstants.DEFAULT_ABILITY_NAME));
    }

    private void remove(int abilityHash) {
        embryoList.remove(abilityHash);
    }
}
