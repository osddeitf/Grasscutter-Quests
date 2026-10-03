package emu.grasscutter.game.props;

import lombok.Getter;
import org.anime_game_servers.core.base.interfaces.IntValueEnum;

/**
 * The vehicle type, as named in the gadget configs' vehicle.vehicleType, and sent as VehicleInteractRsp.vehicle_type
 */
public enum VehicleType implements IntValueEnum {
    None(0),
    Ship(1),
    Skiff(2),
    Sorush(3),
    Natsaurus(4),
    ParamMove(5);

    @Getter private final int value;

    VehicleType(int value) {
        this.value = value;
    }
}
