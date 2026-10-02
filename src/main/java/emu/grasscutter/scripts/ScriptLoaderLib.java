package emu.grasscutter.scripts;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.Loggers;
import emu.grasscutter.utils.FileUtils;
import kotlinx.io.JvmCoreKt;
import kotlinx.io.Source;
import kotlinx.io.SourcesJvmKt;
import lombok.Getter;
import lombok.val;
import org.anime_game_servers.gi_lua.models.loader.GIScriptLoader;
import org.anime_game_servers.gi_lua.models.loader.ScriptSource;
import org.anime_game_servers.jnlua_engine.JNLuaEngine;
import org.anime_game_servers.jnlua_engine.JNLuaScript;
import org.anime_game_servers.lua.engine.LuaEngine;
import org.anime_game_servers.lua.engine.LuaScript;
import org.anime_game_servers.lua.engine.RequireMode;
import org.anime_game_servers.lua.engine.ScriptConfig;
import org.anime_game_servers.lua.models.ScriptType;
import org.anime_game_servers.lua.utils.LuaHelpersJvmKt;
import org.anime_game_servers.luaj_engine.LuaJEngine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.terasology.jnlua.script.LuaBindings;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ScriptLoaderLib implements GIScriptLoader {
    private static final Logger logger = Loggers.getScriptSystem();
    /**
     * suggest GC to remove it if the memory is less
     */
    private Map<Path, SoftReference<LuaScript>> scriptsCache = new ConcurrentHashMap<>();
    @Getter private LuaEngine luaEngine;

    private ScriptConfig scriptConfig;

    public ScriptLoaderLib() {
        this.scriptConfig = fromGcConfig();
        // Create script engine
        if(Grasscutter.getConfig().server.game.useJNLua){
            logger.info("Using JNLua");
            luaEngine = new JNLuaEngine(scriptConfig);
        } else {
            logger.info("Using LuaJ");
            luaEngine = new LuaJEngine(scriptConfig);
        }

        addDefaultsForEngine(luaEngine);
    }

    private ScriptConfig fromGcConfig(){
        val gcConfig = Grasscutter.getConfig();

        return new ScriptConfig(this, RequireMode.ENABLED_WITH_WORKAROUND);
    }

    /**
     * Drops the cached scripts, for bulk loading where they'd otherwise stay reachable (and JNLua's native states alive)
     */
    public void clearCache() {
        scriptsCache.clear();
    }

    /**
     * Closes the lua state of a JNLua script. Its native state holds JNI references back to the java side,
     * so it's never garbage collected (nor finalized) without an explicit close.
     * TODO: reflection until the lua engine exposes closing scripts
     */
    public static void closeScript(@Nullable LuaScript script) {
        if (!(script instanceof JNLuaScript)) return;
        try {
            val bindingField = JNLuaScript.class.getDeclaredField("binding");
            bindingField.setAccessible(true);
            ((LuaBindings) bindingField.get(script)).getLuaState().close();
        } catch (ReflectiveOperationException e) {
            logger.error("Unable to close lua script", e);
        }
    }

    public static <T> Optional<T> tryGet(SoftReference<T> softReference) {
        try {
            return Optional.ofNullable(softReference.get());
        }catch (NullPointerException npe) {
            return Optional.empty();
        }
    }

    @Override
    public Path getScriptPath(String scriptName) {
        var path = FileUtils.getScriptOverwritePath(scriptName);
        if(path != null && Files.exists(path)) {
            return path;
        }

        path = FileUtils.getScriptPath(scriptName);
        if(path != null && Files.exists(path)) {
            return path;
        }

        return null;
    }

    @Nullable
    @Override
    public LuaScript getScript(@NotNull ScriptLoadParams scriptLoadParams) {
        val basePath = scriptLoadParams.getBasePath();
        val scriptPath = getScriptPath(basePath);
        if(scriptPath == null) {
            logger.error("Loading script {} failed! - {}", scriptLoadParams.getScriptName(), "scriptPath is null");
            return null;
        }
        var sc = tryGet(scriptsCache.get(scriptPath));
        if (sc.isPresent()) {
            return sc.get();
        }
        try {
            var script = luaEngine.getScript(scriptPath, scriptLoadParams.getScriptType());
            if(script == null) {
                logger.error("Loading script {} failed! - {}", scriptPath, "script is null");
                return null;
            }
            scriptsCache.put(scriptPath, new SoftReference<>(script));
            return script;
        } catch (Exception e) {
            logger.error("Loading script {} failed! - {}", scriptPath, e.getLocalizedMessage());
            return null;
        }
    }

    @Nullable
    @Override
    public Source openScript(@NotNull ScriptLoadParams scriptLoadParams) {
        val basePath = scriptLoadParams.getBasePath();
        val scriptPath = getScriptPath(basePath);
        if(scriptPath == null) {
            logger.error("Loading script {} failed! - {}", scriptLoadParams.getScriptName(), "scriptPath is null");
            return null;
        }
        if (Files.exists(scriptPath)) {
            try {
                return LuaHelpersJvmKt.asSource(scriptPath);
            } catch (IOException e) {
                logger.error("[Lua] exception while reading file {}:", scriptPath, e);
            }
        } else {
            logger.warn("[Lua] trying to load non existent lua file {}", scriptPath);
        }
        return null;
    }
}
