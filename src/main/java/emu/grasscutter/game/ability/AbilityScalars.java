package emu.grasscutter.game.ability;

import lombok.NonNull;
import org.anime_game_servers.multi_proto.gi.messages.general.ability.AbilityScalarType;
import org.anime_game_servers.multi_proto.gi.messages.general.ability.AbilityScalarValueEntry;

/**
 * Builds ability scalar entries (e.g. SGV dynamic values). A float is set in both wire shapes:
 * the value oneof (with valueType) before 4.8, and the plain float_value after; each version encodes its own.
 */
public final class AbilityScalars {
    private AbilityScalars() {}

    public static AbilityScalarValueEntry from(@NonNull String key, float value) {
        return new AbilityScalarValueEntry(
            AbilityStrings.of(key),
            AbilityScalarType.ABILITY_SCALAR_TYPE_FLOAT,
            new AbilityScalarValueEntry.Value.FloatValue(value),
            value
        );
    }
}
