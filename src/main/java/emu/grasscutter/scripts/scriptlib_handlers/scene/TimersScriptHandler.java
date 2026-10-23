package emu.grasscutter.scripts.scriptlib_handlers.scene;

import emu.grasscutter.Loggers;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.managers.blossom.BlossomSchedule;
import emu.grasscutter.game.managers.blossom.enums.BlossomRefreshType;
import emu.grasscutter.scripts.lua_engine.GroupEventLuaContext;
import emu.grasscutter.scripts.scriptlib_handlers.BaseHandler;
import emu.grasscutter.server.packet.send.PacketBlossomChestCreateNotify;
import lombok.Getter;
import lombok.val;
import org.anime_game_servers.gi_lua.models.ScriptArgs;
import org.anime_game_servers.gi_lua.models.constants.EventType;
import org.anime_game_servers.gi_lua.models.constants.ScriptGadgetState;
import org.anime_game_servers.gi_lua.script_lib.handler.scene.RefreshBlossomGroupParams;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;

public class TimersScriptHandler extends BaseHandler implements org.anime_game_servers.gi_lua.script_lib.handler.scene.TimersScriptHandler<GroupEventLuaContext> {
    @Getter
    private static final Logger logger = Loggers.getScriptSystem();


    @Override
    public int createGroupTimerEvent(GroupEventLuaContext context, int groupId, String source, double time) {
        val actualGroupId = getGroupIdOrCurrentId(context, groupId);
        return context.getSceneScriptManager().createGroupTimerEvent(actualGroupId, source, time);
    }

    @Override
    public int cancelGroupTimerEvent(GroupEventLuaContext context, int groupId, String source) {
        val actualGroupId = getGroupIdOrCurrentId(context, groupId);
        return context.getSceneScriptManager().cancelGroupTimerEvent(actualGroupId, source);
    }

    // time axis

    @Override
    public int initTimeAxis(GroupEventLuaContext context, String timeAxisKey, List<Float> timers, boolean loop) {
        logger.debug("[LUA] Call InitTimeAxis with {},{},{}", timeAxisKey, timers, loop);
        val groupId = getGroupIdOrCurrentId(context, 0);
        return context.getSceneScriptManager().initTimeAxis(groupId, timeAxisKey, timers, loop);
    }

    @Override
    public int endTimeAxis(GroupEventLuaContext context, String timeAxisKey) {
        logger.debug("[LUA] Call EndTimeAxis with {}", timeAxisKey);
        return context.getSceneScriptManager().endTimeAxis(getGroupIdOrCurrentId(context, 0), timeAxisKey);
    }
    @Override
    public int continueTimeAxis(GroupEventLuaContext context, String key) {
        logger.debug("[LUA] Call ContinueTimeAxis with {}", key);
        return context.getSceneScriptManager().continueTimeAxis(getGroupIdOrCurrentId(context, 0), key);
    }


    @Override
    public int endAllTimeAxis(GroupEventLuaContext context) {
        logger.debug("[LUA] Call EndAllTimeAxis");
        return context.getSceneScriptManager().endAllTimeAxis(getGroupIdOrCurrentId(context, 0));
    }


    @Override
    public int pauseTimeAxis(GroupEventLuaContext context, String key) {
        logger.debug("[LUA] Call PauseTimeAxis with {}", key);
        return context.getSceneScriptManager().pauseTimeAxis(getGroupIdOrCurrentId(context, 0), key);
    }

}
