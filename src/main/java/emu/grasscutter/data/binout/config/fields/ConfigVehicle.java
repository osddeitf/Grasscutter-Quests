package emu.grasscutter.data.binout.config.fields;

import emu.grasscutter.game.props.VehicleType;
import lombok.Data;

/**
 * The vehicle section of a gadget config, only the fields in use
 */
@Data
public class ConfigVehicle {
    VehicleType vehicleType;
    int serverBuffId;
    ConfigVehicleStamina stamina;

    @Data
    public static class ConfigVehicleStamina {
        float staminaUpperLimit;
    }
}
