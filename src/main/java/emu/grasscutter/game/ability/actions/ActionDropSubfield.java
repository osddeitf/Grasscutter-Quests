package emu.grasscutter.game.ability.actions;

import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.GameEntity;
import teyvat.ability.config.actions.DropSubfield;

/**
 * Drops the target entity's subfield, e.g. Dulin's Blood from its ore, element balls from a charge point: the drop is
 * resolved by the entity's id and the subfield name (Server/SubfieldMapping.json, then DropSubfieldMapping.json)
 */
public class ActionDropSubfield extends AbilityActionHandler<DropSubfield> {
    @Override
    public Class<?> getType() {
        return DropSubfield.class;
    }

    @Override
    public boolean execute(Ability ability, DropSubfield action, byte[] abilityData, GameEntity<?> target) {
        if (target == null) {
            logger.warn("DropSubfield {} without a target, at {}", action.subfieldName, ability.getName());
            return false;
        }

        // Failures are logged by the drop itself
        return target.dropSubfield(action.subfieldName);
    }
}
