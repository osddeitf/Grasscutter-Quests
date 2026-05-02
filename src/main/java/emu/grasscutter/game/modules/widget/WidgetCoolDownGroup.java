package emu.grasscutter.game.modules.widget;

import lombok.Getter;

public class WidgetCoolDownGroup {
    @Getter int coolDownFail;
    @Getter int coolDownSuccess;
    @Getter boolean isUnderTimeScale;   // relative to game time, to prevent players exploit pauses
}
