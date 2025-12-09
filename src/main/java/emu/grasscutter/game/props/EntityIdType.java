package emu.grasscutter.game.props;

import emu.grasscutter.Grasscutter;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * This comes from [ProtEntityType].
 * Either the client or GC seems to expect ids to have this type in them, with these specific numbers.
 */
@AllArgsConstructor
public enum EntityIdType {
	NONE	(0x00, EntityType.None),
	AVATAR	(0x01, EntityType.Avatar),
	MONSTER	(0x02, EntityType.Monster),
	NPC		(0x03, EntityType.NPC),
	GADGET	(0x04, EntityType.Gadget),
    REGION	(0x05, EntityType.None), //TODO check maybe level or EnviroArea?
    WEAPON	(0x06, EntityType.Equip),
    WEATHER	(0x07, EntityType.None), //TODO check maybe EnviroArea or AOE?
    SCENE	(0x08, EntityType.None), //TODO check maybe level?
	TEAM 	(0x09, EntityType.Team),
    MASSIVE_ENTITY    (0x0A, EntityType.None), //TODO check
	MPLEVEL	(0x0B, EntityType.MPLevel),
    PLAY_TEAM_ENTITY  (0x0C, EntityType.PlayTeam),
    EYE_POINT  (0x0D, EntityType.EyePoint)
    ;

    @Getter
	private final int id;
    @Getter
    private final EntityType type;


    private static final Int2ObjectMap<EntityIdType> map = new Int2ObjectOpenHashMap<>();
    private static final Map<String, EntityIdType> stringMap = new HashMap<>();

    static {
        Stream.of(values()).forEach(e -> {
            map.put(e.getId(), e);
            stringMap.put(e.name(), e);
        });
    }

    private static final int BIT_OFFSET = Grasscutter.getConfig().server.game.gameOptions.useShortenedEntityId? 22: 24;

    public static EntityIdType fromEntityId(int id) {
        return map.getOrDefault(id >> BIT_OFFSET, NONE);
    }
    public static int idFromEntityId(int id) {
        return id >> BIT_OFFSET;
    }

    public int toTypedEntityId(int nextEid) {
        return (getId() << BIT_OFFSET) + nextEid;
    }
}
