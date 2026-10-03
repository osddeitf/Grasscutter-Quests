package emu.grasscutter.scripts;

import emu.grasscutter.scripts.scriptlib_handlers.ScriptLibControllerHandlerProvider;
import emu.grasscutter.scripts.scriptlib_handlers.ScriptLibGroupHandlerProvider;
import emu.grasscutter.server.game.BaseGameSystem;
import emu.grasscutter.server.game.GameServer;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.Getter;
import lombok.val;
import org.anime_game_servers.gi_lua.models.loader.CommonScriptLoadParams;
import org.anime_game_servers.gi_lua.models.scene.SceneMeta;
import org.anime_game_servers.gi_lua.script_lib.ScriptLib;
import org.anime_game_servers.lua.engine.LuaScript;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class ScriptSystem extends BaseGameSystem {
    @Getter private static final ScriptLoaderLib scriptLoader = new ScriptLoaderLib();
    Int2ObjectMap<SceneMeta> sceneMetaCache = new Int2ObjectOpenHashMap<>();

    @Getter
    ScriptLibControllerHandlerProvider scriptLibControllerHandlerProvider = new ScriptLibControllerHandlerProvider();

    @Getter
    ScriptLibGroupHandlerProvider scriptLibGroupHandlerProvider = new ScriptLibGroupHandlerProvider();
    public ScriptSystem(GameServer server) {
        super(server);
        ScriptLib.setStaticHandler(new StaticScriptLibHandler());
    }

    /**
     * Common scripts with generic ServerLuaCall functions, not tied to any group, searched in order.
     * Listed explicitly, as most common scripts are libraries required by group scripts.
     */
    private static final List<CommonScriptLoadParams> SERVER_LUA_CALL_SCRIPTS = List.of(
        new CommonScriptLoadParams("V5_0/", "Natsaurus_Transfer.lua")
    );
    private List<LuaScript> serverLuaCallScripts;

    private synchronized List<LuaScript> getServerLuaCallScripts() {
        if (serverLuaCallScripts == null) {
            val scripts = new ArrayList<LuaScript>();
            SERVER_LUA_CALL_SCRIPTS.forEach(params -> scriptLoader.loadData(params, scripts::add));
            serverLuaCallScripts = scripts;
        }
        return serverLuaCallScripts;
    }

    /** The first generic ServerLuaCall script defining the function */
    @Nullable
    public LuaScript getServerLuaCallScript(String funcName) {
        return getServerLuaCallScripts().stream().filter(script -> script.hasMethod(funcName)).findFirst().orElse(null);
    }

    public SceneMeta getSceneMeta(int sceneId) {
        if(sceneMetaCache.containsKey(sceneId)){
            return sceneMetaCache.get(sceneId);
        }
        val meta = SceneMeta.of(sceneId, scriptLoader);
        sceneMetaCache.put(sceneId, meta);
        return meta;
    }
}
