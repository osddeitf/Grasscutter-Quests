package emu.grasscutter.game.ability;

import teyvat.ability.config.ConfigAbility;
import teyvat.ability.config.ConfigAbilityAction;
import teyvat.ability.config.index.IndexAbilityEntry;
import teyvat.ability.config.index.LoadedAbilities;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Decodes ability localIds: type in the low 3 bits, then 6-bit indices.
 * Modifier actions: type + (modifierIndex << 3) + (configIndex << 9) + (actionIndex << 15).
 */
public final class AbilityLocalId {
    public static final int TYPE_MODIFIER_ACTION = 3;
    /** configIndex of the modifier triggers, in declaration order */
    public static final int MODIFIER_ON_ADDED = 0;

    private static LoadedAbilities cachedFor;
    private static Map<String, IndexAbilityEntry> entries;

    private AbilityLocalId() {}

    public static int type(int localId) {
        return localId & 0x7;
    }

    public static int modifierIndex(int localId) {
        return (localId >> 3) & 0x3F;
    }

    public static int configIndex(int localId) {
        return (localId >> 9) & 0x3F;
    }

    /**
     * Top-level actions of a modifier trigger (e.g. onAdded), in config order.
     * Nested actions (e.g. Predicated branches) are left out: sites are sorted by offset,
     * and a nested site lies within the byte range of the top-level site holding it.
     */
    public static List<ConfigAbilityAction> modifierActions(LoadedAbilities configs, ConfigAbility ability, int modifierIndex, int configIndex) {
        var entry = entry(configs, ability.getAbilityName());
        if (entry == null) return List.of();

        var result = new ArrayList<ConfigAbilityAction>();
        int end = -1;
        for (int i = 0; i < entry.getSiteCount(); i++) {
            int id = entry.localIdAt(i);
            if (type(id) != TYPE_MODIFIER_ACTION || modifierIndex(id) != modifierIndex || configIndex(id) != configIndex) continue;

            int offset = entry.offsetAt(i);
            if (offset < end) continue;  // nested in the previous top-level action
            end = offset + entry.lengthAt(i);

            var action = configs.action(ability, id);
            if (action != null) result.add(action);
        }
        return result;
    }

    private static synchronized IndexAbilityEntry entry(LoadedAbilities configs, String abilityName) {
        if (cachedFor != configs) {
            entries = configs.getIndex().getAbilities().stream()
                .collect(Collectors.toMap(IndexAbilityEntry::getName, Function.identity(), (a, b) -> a));
            cachedFor = configs;
        }
        return entries.get(abilityName);
    }
}
