package emu.grasscutter.server.game;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerDebugMode;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.EntityIdType;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.BaseTypedPacket;
import emu.grasscutter.net.packet.PacketOpcodesUtils;
import emu.grasscutter.server.event.game.SendPacketEvent;
import emu.grasscutter.server.http.dispatch.RegionHandler;
import emu.grasscutter.utils.Crypto;
import emu.grasscutter.utils.FileUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.Getter;
import lombok.Setter;
import lombok.val;
import org.anime_game_servers.core.base.Version;
import org.anime_game_servers.multi_proto.core.interfaces.PacketIdProvider;
import org.anime_game_servers.multi_proto.gi.messages.player.ServerDisconnectClientNotify;
import org.anime_game_servers.multi_proto.gi.packet_id.PacketIds;
import static org.anime_game_servers.multi_proto.gi.GeneratedModelRegistry.allEnums;
import static org.anime_game_servers.multi_proto.gi.GeneratedModelRegistry.allModels;
import static org.anime_game_servers.multi_proto.gi.GeneratedModelRegistry.registryKeys;
import org.anime_game_servers.multi_proto.runtime.descriptor.DynamicProtoRuntime;
import org.anime_game_servers.multi_proto.runtime.engine.EngineLogger;
import org.anime_game_servers.multi_proto.runtime.engine.Slf4jEngineLogger;
import org.anime_game_servers.multi_proto.runtime.engine.ProtoDescriptorRuntime;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

import static emu.grasscutter.config.Configuration.*;
import static emu.grasscutter.utils.Language.translate;

public class GameSession implements GameSessionManager.KcpChannel {
    private final GameServer server;
    private GameSessionManager.KcpTunnel tunnel;

    @Getter @Setter private Account account;
    @Getter @Setter private String accountId;
    @Getter @Setter private String sessionToken;
    @Getter private Player player;

    @Setter private boolean useSecretKey;
    @Getter @Setter private SessionState state;

    @Getter private int clientTime;
    @Getter private long lastPingTime;
    private int lastClientSeq = 10;
    @Getter private Version version;
    @Getter private boolean isTemporaryVersion;
    @Getter private PacketIdProvider packageIdProvider;

    private static final EngineLogger protoEngineLogger = new Slf4jEngineLogger(Grasscutter.getLogger());

    public static ProtoDescriptorRuntime loadVersionRuntime(Version version) {
        try {
            var path = Path.of("config/" + version.getNamespace());
            if (!Files.isDirectory(path)) return null;
            var runtime = DynamicProtoRuntime.registerVersion(
                version, "config/" + version.getNamespace(),
                allModels(), allEnums(), registryKeys()
            );
            runtime.setLogger(protoEngineLogger);
            return runtime;
        }
        catch (Exception ex) {
            Grasscutter.getLogger().error("ERROR: failed to get version {} runtime", version, ex);
            return null;
        }
    }

    public GameSession(GameServer server) {
        this.server = server;
        this.state = SessionState.WAITING_FOR_TOKEN;
        this.lastPingTime = System.currentTimeMillis();
        this.updateVersion(Version.DEFAULT, true);
    }

    public GameServer getServer() {
        return server;
    }

    public InetSocketAddress getAddress() {
        try {
            return tunnel.getAddress();
        } catch (Throwable ignore) {
            return null;
        }
    }

    public boolean useSecretKey() {
        return useSecretKey;
    }

    public synchronized void setPlayer(Player player) {
        this.player = player;
        this.player.setSession(this);
        this.player.setAccount(this.getAccount());
    }

    public boolean isLoggedIn() {
        return this.getPlayer() != null;
    }

    public void updateLastPingTime(int clientTime) {
        this.clientTime = clientTime;
        this.lastPingTime = System.currentTimeMillis();
    }

    public int getNextClientSequence() {
        return ++lastClientSeq;
    }

    public void replayPacket(int opcode, String name) {
        Path filePath = FileUtils.getPluginPath(name);
        File p = filePath.toFile();

        if (!p.exists()) return;

        byte[] packet = FileUtils.read(p);

        BasePacket basePacket = new BasePacket(opcode);
        basePacket.setData(packet);

        send(basePacket);
    }

    private void logPacketSummary(String method, int opcode) {
        Grasscutter.getLogger().info(method + ": " + PacketOpcodesUtils.getOpcodeName(opcode, this) + " (" + opcode + ")");
    }

    public void logPacket(String method, int opcode, BasePacket packet) {
        logPacketSummary(method, opcode);
        if (GAME_INFO.isShowPacketPayload) {
            var header = Optional.ofNullable(packet.getHeader())
                .map(p -> p.encodeToByteArray(version))
                .orElse(new byte[0]);
            PacketLogger.logPacket(method, opcode, packet.getData(this.version), header);
        }
    }

    public void logPacket(String method, int opcode, byte[] payload, byte[] header) {
        logPacketSummary(method, opcode);
        if (GAME_INFO.isShowPacketPayload) {
            PacketLogger.logPacket(method, opcode, payload, header);
        }
    }

    public void send(BasePacket packet) {
        // Test
        val opcode = packet.getOpcode(this);
        if (opcode <= 0 || opcode == 999999) {
            Grasscutter.getLogger().warn("Tried to send packet with missing cmd id! {}", packet.getClass().getSimpleName());
            return;
        }

        // DO NOT REMOVE (unless we find a way to validate code before sending to client which I don't think we can)
        // Stop WindSeedClientNotify from being sent for security purposes.
        val paketName = PacketOpcodesUtils.getOpcodeName(opcode, this);
        if (PacketOpcodesUtils.BANNED_PACKETS.contains(paketName)) {
            return;
        }

        // Header
        if (packet.shouldBuildHeader()) {
            packet.buildHeader(this.getNextClientSequence());
        }

        // Log
        switch (DEBUG_MODE_INFO.logPackets) {
            case ALL -> {
                if (!PacketOpcodesUtils.LOOP_PACKETS.contains(paketName) || GAME_INFO.isShowLoopPackets) {
                    logPacket("SEND", opcode, packet);
                }
            }
            case WHITELIST -> {
                if (SERVER.debugWhitelist.contains(paketName)) {
                    logPacket("SEND", opcode, packet);
                }
            }
            case BLACKLIST -> {
                if (!SERVER.debugBlacklist.contains(paketName)) {
                    logPacket("SEND", opcode, packet);
                }
            }
            default -> {
            }
        }

        // Invoke event.
        SendPacketEvent event = new SendPacketEvent(this, packet);
        event.call();
        if (!event.isCanceled()) { // If event is not cancelled, continue.
            tunnel.writeData(event.getPacket().build(this));
        }
    }

    @Override
    public void onConnected(GameSessionManager.KcpTunnel tunnel) {
        this.tunnel = tunnel;
        Grasscutter.getLogger().info(translate("messages.game.connect", this.getAddress().toString()));
    }

    /**
     * @return false if the session was rejected (and closed) for this version
     */
    public boolean updateVersion(Version version, boolean isTemporaryVersion) {
        this.version = version;
        var runtime = loadVersionRuntime(version);
        if (runtime == null) {
            Grasscutter.getLogger().error("Session invalid, version {} is not supported", version);
            if (!isTemporaryVersion) this.close();
            return isTemporaryVersion;
        }

        this.isTemporaryVersion = isTemporaryVersion;
        this.packageIdProvider = PacketIds.getMapper(version);

        // Entity id layout of this client version. It's global, so the first client fixes it (see EntityIdType)
        if (!isTemporaryVersion) {
            if (!EntityIdType.trySetBitOffset(version)) {
                Grasscutter.getLogger().error("Session rejected: client version {} needs entity id bit offset {}, " +
                    "but the server is fixed to {} by an earlier client",
                    version, EntityIdType.bitOffsetOf(version), EntityIdType.getBitOffset());
                this.close();
                return false;
            }
            Grasscutter.getLogger().info("Client version {}, entity id bit offset {}", version, EntityIdType.getBitOffset());
        }
        return true;
    }

    @Override
    public void handleReceive(byte[] bytes) {
        // Decrypt and turn back into a packet
        // for 4.7.50 should be disabled
        if (GAME_INFO.compatOptions.usePacketXorEncryption) {
        Crypto.xor(bytes, useSecretKey() ? Crypto.ENCRYPT_KEY : Crypto.DISPATCH_KEY);
        }
        ByteBuf packet = Unpooled.wrappedBuffer(bytes);

        // Handle
        try {
            boolean allDebug = DEBUG_MODE_INFO.logPackets == ServerDebugMode.ALL;
            while (packet.readableBytes() > 0) {
                // Length
                if (packet.readableBytes() < 12) {
                    return;
                }
                // Packet sanity check
                int const1 = packet.readShort();
                if (const1 != 17767) {
                    if (allDebug) {
                        Grasscutter.getLogger().error("Bad Data Package Received: got {} ,expect 17767", const1);
                    }
                    return; // Bad packet
                }
                // Data
                int opcode = packet.readShort();
                int headerLength = packet.readShort();
                int payloadLength = packet.readInt();
                byte[] header = new byte[headerLength];
                byte[] payload = new byte[payloadLength];

                packet.readBytes(header);
                packet.readBytes(payload);
                // Sanity check #2
                int const2 = packet.readShort();
                if (const2 != -30293) {
                    if (allDebug) {
                        Grasscutter.getLogger().error("Bad Data Package Received: got {} ,expect -30293", const2);
                    }
                    return; // Bad packet
                }

                if(version == Version.DEFAULT) {
                    val versions =
                        Optional.<List<Version>>ofNullable(null)
                        //Optional.ofNullable(VersionIdentify.getClientVersionsFromFirstPacket(opcode, payload))
                        .orElseGet(() -> RegionHandler.versionIdentifies.get(opcode));

                    if(versions == null || versions.isEmpty()) {
                        Grasscutter.getLogger().error("Failed to identify client version for opcode: {}", opcode);
                        return;
                    }
                    val targetVersion = versions.get(versions.size()-1); // get the latest version for initial parsing
                    val updateIsTemporaryVersion = versions.size() > 1; // if the result is only one version, it's the final version

                    if (!updateVersion(targetVersion, updateIsTemporaryVersion)) {
                        return; // session rejected
                    }
                }

                val paketName = PacketOpcodesUtils.getOpcodeName(opcode, this);

                // Log packet
                switch (DEBUG_MODE_INFO.logPackets) {
                    case ALL -> {
                        if (!PacketOpcodesUtils.LOOP_PACKETS.contains(paketName) || GAME_INFO.isShowLoopPackets) {
                            logPacket("RECV", opcode, payload, header);
                        }
                    }
                    case WHITELIST -> {
                        if (SERVER.debugWhitelist.contains(paketName)) {
                            logPacket("RECV", opcode, payload, header);
                        }
                    }
                    case BLACKLIST -> {
                        if (!(SERVER.debugBlacklist.contains(paketName))) {
                            logPacket("RECV", opcode, payload, header);
                        }
                    }
                    default -> { /* nothing to log */}
                }

                // Handle
                getServer().getPacketHandler().handle(this, opcode, header, payload);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            //byteBuf.release(); //Needn't
            packet.release();
        }
    }

    @Override
    public void handleClose() {
        setState(SessionState.INACTIVE);
        //send disconnection pack in case of reconnection
        Grasscutter.getLogger().info(translate("messages.game.disconnect", this.getAddress().toString()));
        // Save after disconnecting
        if (this.isLoggedIn()) {
            Player player = getPlayer();
            // Call logout event.
            player.onLogout();
        }
        try {
            send(new BaseTypedPacket<>(new ServerDisconnectClientNotify()) {});
        } catch (Throwable ignore) {
            Grasscutter.getLogger().warn("closing {} error", getAddress().getAddress().getHostAddress());
        }
        tunnel = null;
    }

    public void close() {
        tunnel.close();
    }

    public boolean isActive() {
        return getState() == SessionState.ACTIVE;
    }

    public enum SessionState {
        INACTIVE,
        WAITING_FOR_TOKEN,
        WAITING_FOR_LOGIN,
        PICKING_CHARACTER,
        ACTIVE,
        ACCOUNT_BANNED
    }
}
