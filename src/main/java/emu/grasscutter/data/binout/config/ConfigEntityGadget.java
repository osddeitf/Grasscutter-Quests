package emu.grasscutter.data.binout.config;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.binout.config.fields.ConfigVehicle;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import javax.annotation.Nullable;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ConfigEntityGadget extends ConfigEntityBase {
    // There are more values that can be added that might be useful in the json
    @Nullable ConfigVehicle vehicle;
    /** The move component, of which only the type is read */
    @Nullable ConfigMove move;

    @Data
    public static class ConfigMove {
        @SerializedName("$type") private String type;
    }
}
