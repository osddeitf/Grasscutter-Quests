package emu.grasscutter.game.props;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.Getter;
import org.anime_game_servers.core.base.interfaces.IntValueEnum;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public enum ElementType implements IntValueEnum {
    None		(0, FightProperty.FIGHT_PROP_CUR_FIRE_ENERGY, FightProperty.FIGHT_PROP_MAX_FIRE_ENERGY),
    Fire		(1, FightProperty.FIGHT_PROP_CUR_FIRE_ENERGY, FightProperty.FIGHT_PROP_MAX_FIRE_ENERGY, 1),
    Water		(2, FightProperty.FIGHT_PROP_CUR_WATER_ENERGY, FightProperty.FIGHT_PROP_MAX_WATER_ENERGY, 2),
    Grass		(3, FightProperty.FIGHT_PROP_CUR_GRASS_ENERGY, FightProperty.FIGHT_PROP_MAX_GRASS_ENERGY, 7),
    Electric	(4, FightProperty.FIGHT_PROP_CUR_ELEC_ENERGY, FightProperty.FIGHT_PROP_MAX_ELEC_ENERGY, 6),
    Ice			(5, FightProperty.FIGHT_PROP_CUR_ICE_ENERGY, FightProperty.FIGHT_PROP_MAX_ICE_ENERGY, 4),
    Frozen		(6, FightProperty.FIGHT_PROP_CUR_ICE_ENERGY, FightProperty.FIGHT_PROP_MAX_ICE_ENERGY),
    Wind		(7, FightProperty.FIGHT_PROP_CUR_WIND_ENERGY, FightProperty.FIGHT_PROP_MAX_WIND_ENERGY, 3),
    Rock		(8, FightProperty.FIGHT_PROP_CUR_ROCK_ENERGY, FightProperty.FIGHT_PROP_MAX_ROCK_ENERGY, 5),
    AntiFire	(9, FightProperty.FIGHT_PROP_CUR_FIRE_ENERGY, FightProperty.FIGHT_PROP_MAX_FIRE_ENERGY),
    Default		(255, FightProperty.FIGHT_PROP_CUR_FIRE_ENERGY, FightProperty.FIGHT_PROP_MAX_FIRE_ENERGY);

    @Getter private final int value;
    @Getter private final FightProperty curEnergyProp;
    @Getter private final FightProperty maxEnergyProp;
    @Getter private final int depotIndex;
    private static final Int2ObjectMap<ElementType> map = new Int2ObjectOpenHashMap<>();
    private static final Map<String, ElementType> stringMap = new HashMap<>();

    static {
        Stream.of(values()).forEach(e -> {
            map.put(e.getValue(), e);
            stringMap.put(e.name(), e);
        });
    }

    ElementType(int value, FightProperty curEnergyProp, FightProperty maxEnergyProp) {
        this(value, curEnergyProp, maxEnergyProp, 1);
    }

    ElementType(int value, FightProperty curEnergyProp, FightProperty maxEnergyProp, int depotIndex) {
        this.value = value;
        this.curEnergyProp = curEnergyProp;
        this.maxEnergyProp = maxEnergyProp;
        this.depotIndex = depotIndex;
    }

    public static ElementType getTypeByValue(int value) {
        return map.getOrDefault(value, None);
    }

    public static ElementType getTypeByName(String name) {
        return stringMap.getOrDefault(name, None);
    }
}
