package emu.grasscutter.game.props;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import org.anime_game_servers.core.base.Version;

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

    // Bit offset of the type within an entity id. It differs between client versions, and it's global, so it's fixed
    // by the first client whose version is known (GameSession.updateVersion). Clients needing a different offset are
    // rejected, so ongoing sessions aren't affected; co-op between different client versions isn't supported.
    private static final int DEFAULT_BIT_OFFSET = 24;
    private static volatile int bitOffset = 0;  // 0 = not fixed yet

    public static int getBitOffset() {
        return bitOffset != 0 ? bitOffset : DEFAULT_BIT_OFFSET;
    }

    // Entity id bit offset used by a client version
    public static int bitOffsetOf(@NonNull Version version) {
        if (version.getId() >= Version.GI_6_5_0.getId()) return 21;
        if (version.getId() >= Version.GI_6_1_0.getId()) return 22;
        return DEFAULT_BIT_OFFSET;
    }

    /**
     * Fixes the bit offset for the client version on the first call; later calls only check compatibility.
     * @return false if the offset is already fixed to a different value
     */
    public static synchronized boolean trySetBitOffset(@NonNull Version version) {
        int offset = bitOffsetOf(version);
        if (bitOffset == 0) {
            bitOffset = offset;
            return true;
        }
        return bitOffset == offset;
    }

    public static EntityIdType fromEntityId(int id) {
        return map.getOrDefault(id >> bitOffset, NONE);
    }
    public static int idFromEntityId(int id) {
        return id >> bitOffset;
    }

    public int toTypedEntityId(int nextEid) {
        return (getId() << bitOffset) + nextEid;
    }
}
