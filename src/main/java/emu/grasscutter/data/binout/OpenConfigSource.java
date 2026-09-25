package emu.grasscutter.data.binout;

public interface OpenConfigSource {
    String getOpenConfig();

    default float[] getParamList() {
        return new float[0];
    }
}
