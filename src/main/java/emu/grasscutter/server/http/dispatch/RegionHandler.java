package emu.grasscutter.server.http.dispatch;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.server.event.dispatch.QueryAllRegionsEvent;
import emu.grasscutter.server.event.dispatch.QueryCurrentRegionEvent;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.http.Router;
import emu.grasscutter.server.http.objects.QueryCurRegionRspJson;
import emu.grasscutter.utils.Crypto;
import emu.grasscutter.utils.JsonUtils;
import emu.grasscutter.utils.Utils;
import io.javalin.Javalin;
import io.javalin.http.Context;
import lombok.val;
import org.anime_game_servers.core.base.Version;
import org.anime_game_servers.multi_proto.gi.messages.general.server.RegionInfo;
import org.anime_game_servers.multi_proto.gi.messages.login.QueryCurrRegionHttpRsp;
import org.anime_game_servers.multi_proto.gi.messages.login.QueryRegionListHttpRsp;
import org.anime_game_servers.multi_proto.gi.messages.login.RegionSimpleInfo;
import org.anime_game_servers.multi_proto.gi.packet_id.PacketIds;
import org.anime_game_servers.multi_proto.gi.utils.VersionIdentify;

import javax.crypto.Cipher;
import java.io.ByteArrayOutputStream;
import java.security.Signature;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static emu.grasscutter.config.Configuration.*;

/**
 * Handles requests related to region queries.
 */
public final class RegionHandler implements Router {
    private static final Map<String, RegionData> regions = new ConcurrentHashMap<>();
    private static final Map<RegionType, String> regionListResponses = new EnumMap<>(RegionType.class);
    public static final Map<Integer, List<Version>> versionIdentifies = new ConcurrentHashMap<>();

    public RegionHandler() {
        try { // Read & initialize region data.
            this.initialize();
        } catch (Exception exception) {
            Grasscutter.getLogger().error("Failed to initialize region data.", exception);
        }
    }

    enum RegionType {
        OS,
        CN
    }

    /**
     * Configures region data according to configuration.
     */
    private void initialize() {
        String dispatchDomain = "http" + (HTTP_ENCRYPTION.useInRouting ? "s" : "") + "://"
                + lr(HTTP_INFO.accessAddress, HTTP_INFO.bindAddress) + ":"
                + lr(HTTP_INFO.accessPort, HTTP_INFO.bindPort);

        // Create regions.
        List<RegionSimpleInfo> servers = new ArrayList<>();
        List<String> usedNames = new ArrayList<>(); // List to check for potential naming conflicts.

        var configuredRegions = new ArrayList<>(List.of(DISPATCH_INFO.regions));
        if (SERVER.runMode != ServerRunMode.HYBRID && configuredRegions.size() == 0) {
            Grasscutter.getLogger().error("[Dispatch] There are no game servers available. Exiting due to unplayable state.");
            System.exit(1);
        } else if (configuredRegions.size() == 0)
            configuredRegions.add(new Region("os_usa", DISPATCH_INFO.defaultName,
                    lr(GAME_INFO.accessAddress, GAME_INFO.bindAddress),
                    lr(GAME_INFO.accessPort, GAME_INFO.bindPort)));

        configuredRegions.forEach(region -> {
            if (usedNames.contains(region.Name)) {
                Grasscutter.getLogger().error("Region name already in use.");
                return;
            }

            // Create a region identifier.
            val identifier = new RegionSimpleInfo();
            identifier.setName(region.Name);
            identifier.setTitle(region.Title);
            identifier.setType("DEV_PUBLIC");
            identifier.setDispatchUrl(dispatchDomain + "/query_cur_region/" + region.Name);
            usedNames.add(region.Name); servers.add(identifier);

            // Create a region info object.
            var regionInfo = new RegionInfo();
            regionInfo.setGateserverIp(region.Ip);
            regionInfo.setGateserverPort(region.Port);
            regionInfo.setSecretKey(Crypto.DISPATCH_SEED);

            // Create an updated region query.
            val updatedQuery = new QueryCurrRegionHttpRsp();
            updatedQuery.setRegionInfo(regionInfo);
            updatedQuery.setClientSecretKey(Crypto.DISPATCH_SEED);
            regions.put(region.Name, new RegionData(updatedQuery));
        });

        var hiddenIcons = new JsonArray();
        hiddenIcons.add(40);
        var codeSwitch = new JsonArray();
        // codeSwitch.add(3628);
        codeSwitch.add(4334);

        // Create a config object.
        var customConfig = new JsonObject();
        customConfig.addProperty("sdkenv", "2");
        customConfig.addProperty("checkdevice", "false");
        customConfig.addProperty("loadPatch", "false");
        customConfig.addProperty("showexception", "false"); // todo make it a setting
        customConfig.addProperty("regionConfig", "pm|fk|add");
        customConfig.addProperty("downloadMode", "0");
        customConfig.addProperty("debugmenu", "true");
        customConfig.addProperty("debuglog", "true");
        customConfig.add("codeSwitch", codeSwitch);
        customConfig.add("coverSwitch", hiddenIcons);
        var encodedConfig = JsonUtils.encode(customConfig).getBytes();
        Crypto.xor(encodedConfig, Crypto.DISPATCH_KEY); // XOR the config with the key.

        // CN config
        customConfig.addProperty("sdkenv", "0");
        var encodedConfigCn = JsonUtils.encode(customConfig).getBytes();
        Crypto.xor(encodedConfigCn, Crypto.DISPATCH_KEY); // XOR the config with the key.

        // Create an updated region list.
        val updatedRegionList = new QueryRegionListHttpRsp();
        updatedRegionList.setRegionList(servers);
        updatedRegionList.setClientSecretKey(Crypto.DISPATCH_SEED);
        updatedRegionList.setEnableLoginPc(true);

        // Set the region list response.
        setRegionListResponses(RegionType.OS, encodedConfig, updatedRegionList);
        setRegionListResponses(RegionType.CN, encodedConfigCn, updatedRegionList);
    }

    /**
     * Pre generates the region list responses for each version
     * @param regionType The region type
     * @param customConfig The  custom configuration, XOR'd with the dispatch key
     * @param baseRegionList The filled out region list response model
     */
    private void setRegionListResponses(RegionType regionType, byte[] customConfig, QueryRegionListHttpRsp baseRegionList) {
        versionIdentifies.clear();

        baseRegionList.setClientCustomConfigEncrypted(customConfig);
        Arrays.stream(Version.values()).forEach(version -> {
            var runtime = GameSession.loadVersionRuntime(version);
            if (runtime == null) return;
            val encoded = baseRegionList.encodeToByteArray(version);
            if (encoded != null) {
                Grasscutter.getLogger().info("Added version {}, length {}, hex {}", version.getNamespace(), encoded.length, Utils.bytesToHex(encoded));
                // todo actually save region specific responses, and not just the newest success
                regionListResponses.put(regionType, Utils.base64Encode(encoded));

                var mapper = PacketIds.getMapper(version);
                if (mapper != null) {
                    var opcode = mapper.getPacketId("GetPlayerTokenReq");
                    if (opcode <= 0 || opcode == 999999) return;

                    var list = versionIdentifies.computeIfAbsent(opcode, k -> new ArrayList<>());
                    Grasscutter.getLogger().info("Added version identify (GetPlayerTokenReq) for {}, cmdid {}", version.getNamespace(), opcode);
                    list.add(version);
                }
            }
        });
    }

    @Override public void applyRoutes(Javalin javalin) {
        javalin.get("/query_region_list", RegionHandler::queryRegionList);
        javalin.get("/query_cur_region/{region}", RegionHandler::queryCurrentRegion);
    }

    private static final String VERSION_KEY = "version";
    private static final String PLATFORM_KEY = "platform";

    /**
     * @route /query_region_list
     */
    private static void queryRegionList(Context ctx) {

        // use OS as default fallback
        RegionType targetRegion = RegionType.OS;

        // Respond with event result.
        if (ctx.queryParamMap().containsKey(VERSION_KEY) && ctx.queryParamMap().containsKey(PLATFORM_KEY)) {
            String versionName = ctx.queryParam(VERSION_KEY);
            String versionCode = versionName!=null ?  versionName.replaceAll("[/.0-9]*", "") : "";
            String platformName = ctx.queryParam(PLATFORM_KEY);

            // Determine the region list to use based on the version and platform.
            if ("CNCBiOS".equals(versionCode) || "CNCBWin".equals(versionCode) || "CNCBAnd".equals(versionCode)) {
                targetRegion = RegionType.CN;
            }
            else if ("CNRELiOS".equals(versionCode) || "CNRELWin".equals(versionCode)
                || "CNRELAndroid".equals(versionCode)) {
                targetRegion = RegionType.CN;
            } else if ("OSRELiOS".equals(versionCode) || "OSRELWin".equals(versionCode)
                || "OSRELAndroid".equals(versionCode)) {
                // Use the OS region list.
                targetRegion = RegionType.OS;
            }
        }

        // Invoke event.
        QueryAllRegionsEvent event = new QueryAllRegionsEvent(regionListResponses.get(targetRegion));
        event.call();
        // Respond with event result.
        ctx.result(event.getRegionList());

        // Log to console.
        Grasscutter.getLogger().info("[Dispatch] Client {} request: query_region_list for region {}", ctx.ip(), targetRegion.name());
    }

    /**
     * @route /query_cur_region/{region}
     */
    private static void queryCurrentRegion(Context ctx) {
        // Get region to query.
        String regionName = ctx.pathParam("region");
        String versionName = ctx.queryParam("version");
        var region = regions.get(regionName);


        if (versionName == null){
            Grasscutter.getLogger().error("Client {} request: query_cur_region/{} with missing version", ctx.ip(), regionName);
            return;
        }

        Version version;
        try {
            version = VersionIdentify.getClientVersionFromQueryRegion(versionName);
            if (versionName.equals("CNCBWin4.7.50")) {
                version = Version.GI_4_8_0;
                ctx.result("{\"content\":\"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\",\"sign\":\"ftRF9MJWHc/MPjMmchq1F2IvghrgxSvneh47RzrEGz7KpXgNb+71Zr6LPyEjMKQt5Fj9NPRtUspZSN4ssHgLkt7UP930ys5eUE2MQZtorq3ha1ruPniiQb882TB5LScu3oiLKg3uWbqI6oimckSrIjizS1L3s1dg6ZYRbYZNP0IdSyfdpku+Yxwt/LakfQFIMFItDb7TlXQJze8hp0VfeailDCJ5ZNFUP9eBlzENNCB+fnWu3hIdP8I2EtvUoLLEbNBNV5wFdiGZktJZc2vznQHvFCsVz6tn3mUQvXx7gayl2MmGxh0WZR2jMWzRd/Xd0kURqQfZVfD97OdD2dElWg==\"}");
                return;
            }
        } catch (Exception e) {
            Grasscutter.getLogger().error("Client {} request: query_cur_region/{} with invalid version {} and exception", ctx.ip(), regionName, versionName, e);
            return;
        }

        if (version == null){
            Grasscutter.getLogger().error("Client {} request: query_cur_region/{} with unknown version {}", ctx.ip(), regionName, versionName);
            return;
        }

        // Get region data.
        String regionData = "CAESGE5vdCBGb3VuZCB2ZXJzaW9uIGNvbmZpZw==";
        if (ctx.queryParamMap().values().size() > 0) {
            if (region != null)
                regionData = region.getBase64(version);
        }


        if (version.getId() > Version.GI_2_7_0.getId()) {
            try {
                QueryCurrentRegionEvent event = new QueryCurrentRegionEvent(regionData); event.call();

                Grasscutter.getLogger().warn("Params: {}", ctx.queryParamMap());
                if (ctx.queryParam("dispatchSeed") == null) {
                    // More love for UA Patch players
                    var rsp = new QueryCurRegionRspJson();

                    rsp.content = event.getRegionInfo();
                    rsp.sign = "TW9yZSBsb3ZlIGZvciBVQSBQYXRjaCBwbGF5ZXJz";

                    ctx.json(rsp);
                    return;
                }

                String key_id = ctx.queryParam("key_id");

                if (key_id == null)
                    throw new Exception("Key ID was not set");

                Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                Grasscutter.getLogger().warn("Key id: {}", key_id);
                cipher.init(Cipher.ENCRYPT_MODE, Crypto.EncryptionKeys.get(Integer.valueOf(key_id)));
                var regionInfo = Utils.base64Decode(event.getRegionInfo());

                //Encrypt regionInfo in chunks
                ByteArrayOutputStream encryptedRegionInfoStream = new ByteArrayOutputStream();

                //Thank you so much GH Copilot
                int chunkSize = 256 - 11;
                int regionInfoLength = regionInfo.length;
                int numChunks = (int) Math.ceil(regionInfoLength / (double) chunkSize);

                for (int i = 0; i < numChunks; i++) {
                    byte[] chunk = Arrays.copyOfRange(regionInfo, i * chunkSize, Math.min((i + 1) * chunkSize, regionInfoLength));
                    byte[] encryptedChunk = cipher.doFinal(chunk);
                    encryptedRegionInfoStream.write(encryptedChunk);
                }

                Signature privateSignature = Signature.getInstance("SHA256withRSA");
                privateSignature.initSign(Crypto.CUR_SIGNING_KEY);
                privateSignature.update(regionInfo);

                var rsp = new QueryCurRegionRspJson();

                rsp.content = Utils.base64Encode(encryptedRegionInfoStream.toByteArray());
                rsp.sign = Utils.base64Encode(privateSignature.sign());

                ctx.json(rsp);
            }
            catch (Exception e) {
                Grasscutter.getLogger().error("An error occurred while handling query_cur_region.", e);
            }
        }
        else {
            // Invoke event.
            QueryCurrentRegionEvent event = new QueryCurrentRegionEvent(regionData); event.call();
            // Respond with event result.
            ctx.result(event.getRegionInfo());
        }
        // Log to console.
        Grasscutter.getLogger().info(String.format("Client %s request: query_cur_region/%s", ctx.ip(), regionName));
    }

    /**
     * Region data container.
     */
    public static class RegionData {
        private final QueryCurrRegionHttpRsp regionQuery;

        public RegionData(QueryCurrRegionHttpRsp prq) {
            this.regionQuery = prq;
        }

        public QueryCurrRegionHttpRsp getRegionQuery() {
            return this.regionQuery;
        }

        public String getBase64(Version version) {
            return Utils.base64Encode(regionQuery.encodeToByteArray(version));
        }
    }

    /**
     * Gets the current region query.
     * @return A {@link QueryCurrRegionHttpRsp} object.
     */
    public static QueryCurrRegionHttpRsp getCurrentRegion() {
        return SERVER.runMode == ServerRunMode.HYBRID ? regions.get("os_usa").getRegionQuery() : null;
    }
}
