package emu.grasscutter.data.common;

import emu.grasscutter.game.entity.EntityAbilityProperty;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMap;
import lombok.Getter;

// Mirrors the client's PileValue::updateValue
public class StackedValue {

    private final StackingType stackingType;
    @Getter private float value;    // cache
    private final float boundMin;
    private final float boundMax;
    private final Int2ObjectSortedMap<StackingItem> stack = new Int2ObjectRBTreeMap<>();

    private StackedValue(float defaultValue, StackingType stackingType, float min, float max) {
        this.stackingType = stackingType;
        this.value = defaultValue;
        this.boundMin = min;
        this.boundMax = max;
    }

    public static StackedValue from(EntityAbilityProperty prop) {
        return new StackedValue(prop.getDefaultValue(), prop.getStackingType(), prop.getMin(), prop.getMax());
    }

    public int add(float value, boolean isBounded) {
        int idx = 0;
        while (stack.containsKey(idx)) idx++;
        stack.put(idx, new StackingItem(value, isBounded));
        calculateFloatValue();
        return idx;
    }

    public void del(int idx) {
        stack.remove(idx);
        calculateFloatValue();
    }


    private record StackingItem(float val, boolean isBounded) {}

    /** Ordered as the client's data::StackMethod */
    public enum StackingType {
        /** the item with the highest index */
        Top,
        /** sum of x */
        Sum,
        /** multiply of (1 + x) */
        Multiply,
        /** multiply of (1 - x) */
        OneMinusMultiply,
    }

    private void calculateFloatValue() {
        var boundedValue = 0f;
        switch (stackingType) {
            case Top -> {
                // as in client, value is left unchanged when empty, or when the top item is bounded
                if (!stack.isEmpty()) {
                    var item = stack.get(stack.lastIntKey());
                    if (item.isBounded()) {
                        boundedValue = item.val;
                    }
                    else {
                        this.value = item.val;
                    }
                }
            }
            case Sum -> {
                this.value = 0f;
                for (var item: stack.values()) {
                    if (item.isBounded()) {
                        boundedValue += item.val;
                    }
                    else {
                        this.value += item.val;
                    }
                }
            }
            // as in client, boundedValue starts at 0 for the multiplied types, so it stays 0
            case Multiply -> {
                this.value = 1f;
                for (var item: stack.values()) {
                    if (item.isBounded()) {
                        boundedValue *= 1f + item.val;
                    }
                    else {
                        this.value *= 1f + item.val;
                    }
                }
            }
            case OneMinusMultiply -> {
                this.value = 1f;
                for (var item: stack.values()) {
                    if (item.isBounded()) {
                        boundedValue *= 1f - item.val;
                    }
                    else {
                        this.value *= 1f - item.val;
                    }
                }
            }
        }

        boundedValue = clampValue(boundedValue);
        switch (stackingType) {
            case Top, Sum -> this.value += boundedValue;
            case Multiply, OneMinusMultiply -> this.value *= boundedValue;
        }
    }

    private float clampValue(float value) {
        if (boundMin <= value) {
            return value > boundMax ? boundMax : value;
        }
        return boundMin;
    }
}
