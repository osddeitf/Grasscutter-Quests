package emu.grasscutter.net.packet;

import emu.grasscutter.server.game.GameSession;

import java.util.Set;

public class PacketOpcodesUtils {

    public static final Set<String> BANNED_PACKETS = Set.of(
        "WindSeedClientNotify",
        "PlayerLuaShellNotify"
    );

    public static final Set<String> LOOP_PACKETS = Set.of(
        "PingReq",
        "PingRsp",
        "WorldPlayerRTTNotify",
        "UnionCmdNotify",
        "QueryPathReq",
        "QueryPathRsp",

        // Satiation sends these every tick
        "PlayerTimeNotify",
        "PlayerGameTimeNotify",
        "AvatarPropNotify",
        "AvatarSatiationDataNotify"
    );

    public static String getOpcodeName(int opcode, GameSession session) {
        if (opcode <= 0) return "UNKNOWN";
        var name = session.getPackageIdProvider().getPacketName(opcode);
        if (name == null) {
            return "UNKNOWN";
        }
        return name;
    }
}
