package emu.grasscutter.data.common;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.Getter;

public class StackedValue {

    private final StackingType stackingType;
    @Getter private float value;    // cache
    private float boundMin = Float.MIN_VALUE;
    private float boundMax = Float.MAX_VALUE;
    private final Int2ObjectMap<StackingItem> stack = new Int2ObjectOpenHashMap<>();

    public StackedValue() {
        this(0f, StackingType.Sum);
    }

    public StackedValue(float defaultValue) {
        this(defaultValue, StackingType.Sum);
    }

    public StackedValue(float defaultValue, StackingType stackingType) {
        this.stackingType = stackingType;
        this.value = defaultValue;
    }

    public void setLimit(float min, float max) {
        boundMin = min;
        boundMax = max;
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

    public enum StackingType {
        /** multiply of (1 - x) */
        OneMinusMultiply,
        Multiply,
        Sum,
        Top,
    }

    private void calculateFloatValue() {
        switch (stackingType) {
            case OneMinusMultiply -> {
               this.value = 0;
            }
            case Multiply -> {
                var value = 1f;
                for (var item: stack.values()) {
                    if (!item.isBounded()) {
                        value = (1f - item.val) * value;
                    }
                }
                this.value = value;
            }
            case Sum -> {
                var value = 1f;
                var boundedValue = 0f;
                for (var item: stack.values()) {
                    if (!item.isBounded()) {
                        value += item.val;
                    }
                    else {
                        boundedValue += item.val;
                    }
                }
                this.value = value + clampValue(boundedValue);
            }
            case Top -> {
                var value = 1f;
                var boundedValue = 0f;
                for (var item: stack.values()) {
                    if (!item.isBounded()) {
                        value = item.val;
                    }
                    else {
                        boundedValue += item.val;
                    }
                }
                this.value = value + clampValue(boundedValue);
            }
        }
    }

    private float clampValue(float value) {
        return Math.max(boundMin, Math.min(value, boundMax));
    }
}
