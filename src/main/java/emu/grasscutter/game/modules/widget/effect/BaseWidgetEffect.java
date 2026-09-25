package emu.grasscutter.game.modules.widget.effect;

import lombok.Getter;

public abstract class BaseWidgetEffect {
    @Getter
    protected int materialId;

    public BaseWidgetEffect(int materialId) {
        this.materialId = materialId;
    }

    public abstract void attach();
    public abstract void detach();
}
