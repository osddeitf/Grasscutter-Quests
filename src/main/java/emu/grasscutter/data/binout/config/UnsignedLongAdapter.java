package emu.grasscutter.data.binout.config;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

/** Reads unsigned 64-bit values that exceed the range accepted by Gson's default long adapter. */
public class UnsignedLongAdapter extends TypeAdapter<Long> {
    @Override
    public void write(JsonWriter out, Long value) throws IOException {
        out.value(Long.toUnsignedString(value));
    }

    @Override
    public Long read(JsonReader in) throws IOException {
        return Long.parseUnsignedLong(in.nextString());
    }
}
