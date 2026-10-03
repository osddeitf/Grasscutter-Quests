package emu.grasscutter.data.common;

import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityManager;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.utils.Utils;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import teyvat.ability.config.DynamicFloat;
import teyvat.ability.config.Term;

import java.util.NoSuchElementException;
import java.util.function.Function;

public final class AbilityMath {
    public static float evaluate(DynamicFloat value, Ability ability) {
        return evaluate(value, ability, 0f);
    }

    public static float evaluate(DynamicFloat value, GameEntity<?> entity) {
        return evaluate(value, entity, 0f);
    }

    public static float evaluate(DynamicFloat value, GameEntity<?> entity, float defaultValue) {
        return evaluate(value, entity.getGlobalAbilityValues()::get, defaultValue);
    }

    public static float evaluate(DynamicFloat value, Ability ability, float defaultValue) {
        return evaluate(value, (key) -> {
            var hash = Utils.abilityHash(key);
            // TODO: implement a config, when set to true, get from server override map, then return early (ignore other map)
            var overrideMap = ability.getOverrideMap();
            if (!overrideMap.containsKey(hash)) {
                // Assumption: skip abilitySpecials, since always init by client
                // Assumption: the global value in question is from ability entity owner
                return ability.getOwner().getGlobalAbilityValues().get(key);
            }
            return overrideMap.get(hash);
        }, defaultValue);
    }

    public static float evaluate(DynamicFloat value, Function<String, Float> resolver, float defaultValue) {
        if (value instanceof DynamicFloat.Key k) {
            var key = k.getKey();
            var resolvedValue = resolver.apply(key);
            if (resolvedValue == null) {
                AbilityManager.getLogger().warn("Evaluate DynamicFloat missing key {}", key);
                return 0;
            }
            return resolvedValue * (k.getNegative()? -1: 1);
        }
        if (value instanceof DynamicFloat.Constant c) {
            return c.getValue();
        }
        if (value instanceof DynamicFloat.Formula f) {
            var fl = new FloatArrayList();
            for (var item: f.getTerms()) {
                if (item instanceof Term.Operand operand) {
                    fl.push(evaluate(operand.getValue(), resolver, defaultValue));
                }
                if (item instanceof Term.Operator op) {
                    switch (op.getOp()) {
                        case Add -> fl.push(fl.popFloat() + fl.popFloat());
                        case Sub -> fl.push(-fl.popFloat() + fl.popFloat());  // [f0, f1, f2] -> [f0, f1-f2]  (opposite of RPN order)
                        case Mul -> fl.push(fl.popFloat() * fl.popFloat());
                        case Div -> fl.push((1f/fl.popFloat()) * fl.popFloat());  // [f0, f1, f2] -> [f0, f1/f2]
                    }
                }
            }
            try {
                return fl.popFloat();  // well-formed data will always have only one value left at this point
            } catch(NoSuchElementException e) {
                // TODO: report mal-formed data
            }
        }
        return defaultValue;
    }
}
