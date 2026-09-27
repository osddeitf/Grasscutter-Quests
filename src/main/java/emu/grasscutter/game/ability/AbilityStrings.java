package emu.grasscutter.game.ability;

import emu.grasscutter.utils.Utils;
import lombok.NonNull;
import org.anime_game_servers.multi_proto.gi.messages.general.ability.AbilityString;

/**
 * Whether ability strings (ability names, override/SGV keys) are hashed on the wire.
 * The client is told in PlayerLoginRsp (is_use_ability_hash), and outgoing ability strings are built here,
 * so both follow the same switch. Incoming ability strings are handled in both forms
 * (see Ability.getAbilityName / Ability.getAbilityHash).
 * Proto fields that are hash-only by definition (e.g. AbilityEmbryo name hashes,
 * ServerGlobalValueChangeNotify.key_hash) aren't affected.
 */
public final class AbilityStrings {
    // Hardcoded for now: ability strings are sent as plain strings
    public static final boolean USE_ABILITY_HASH = false;

    private AbilityStrings() {}

    public static AbilityString of(@NonNull String value) {
        return USE_ABILITY_HASH
            ? new AbilityString(new AbilityString.Type.Hash(Utils.abilityHash(value)))
            : new AbilityString(new AbilityString.Type.Str(value));
    }
}
