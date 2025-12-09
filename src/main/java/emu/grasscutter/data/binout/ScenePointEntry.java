package emu.grasscutter.data.binout;

import emu.grasscutter.data.common.PointData;
import lombok.Getter;

public class ScenePointEntry {
    @Getter final private int sceneId;
    @Getter final private PointData pointData;

    public ScenePointEntry(int sceneId, PointData pointData) {
        this.sceneId = sceneId;
        this.pointData = pointData;
    }
}
