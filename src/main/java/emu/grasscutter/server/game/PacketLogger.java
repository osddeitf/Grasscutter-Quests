package emu.grasscutter.server.game;

import com.google.gson.stream.JsonWriter;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.utils.Utils;
import io.netty.channel.DefaultEventLoop;
import io.netty.channel.EventLoop;
import lombok.val;
import org.anime_game_servers.core.base.Version;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

public class PacketLogger {

    private static JsonWriter jsonWriter = null;
    private static boolean writerAcquired = false;
    private static final EventLoop eventLoop = new DefaultEventLoop();

    private static JsonWriter getJsonWriterOnce() {
        if (writerAcquired) return jsonWriter;
        try {
            writerAcquired = true;
            val path = Path.of("session.json");
            val file = path.toFile();
            val writer = new JsonWriter(new FileWriter(file));
            writer.setIndent("  ");
            writer.beginArray();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    writer.endArray(); // close JSON structure
                    writer.close();    // flush + free file handle
                    System.out.println("Log writer closed cleanly.");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }));
            return jsonWriter = writer;
        }
        catch (IOException e) {
            Grasscutter.getLogger().error("Failed to open session.json", e);
            return null;
        }
    }

    public static void logPacket(String sendOrRecv, int opcode, BasePacket packet, Version version) {
        eventLoop.submit(() -> {
            doLogPacket(sendOrRecv, opcode, packet.getData(version), packet.getHeader().encodeToByteArray(version));
        });
    }

    public static void logPacket(String sendOrRecv, int opcode, byte[] payload, byte[] header) {
        eventLoop.submit(() -> {
            doLogPacket(sendOrRecv, opcode, payload, header);
        });
    }

    private static void doLogPacket(String sendOrRecv, int opcode, byte[] payload, byte[] header) {
        try {
            val writer = getJsonWriterOnce();
            if (writer != null) {
                var timestamp = System.currentTimeMillis() * 1000;
                writer.beginObject();
                writer.name("isClient").value(Objects.equals(sendOrRecv, "RECV"));
                writer.name("cmdId").value(opcode);
                writer.name("header").value(Utils.bytesToHex(header));
                writer.name("data").value(Utils.bytesToHex(payload));
                writer.name("timestamp").value(String.valueOf(timestamp));
                writer.endObject();
            }
        }
        catch (IOException ex) {
            Grasscutter.getLogger().error("Failed to write session.json", ex);
        }
    }

}
