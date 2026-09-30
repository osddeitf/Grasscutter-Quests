package emu.grasscutter.game.ability.actions;

import com.github.davidmoten.guavamini.Lists;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.LuaCallType;
import emu.grasscutter.data.common.AbilityMath;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.GameEntity;
import lombok.val;
import org.anime_game_servers.gi_lua.models.ScriptArgs;
import org.anime_game_servers.gi_lua.models.constants.EventType;
import teyvat.ability.config.actions.ServerLuaTriggerEvent;

import java.util.Arrays;

public class ActionServerLuaTriggerEvent extends AbilityActionHandler<ServerLuaTriggerEvent> {

    @Override
    public Class<?> getType() {
        return ServerLuaTriggerEvent.class;
    }

    @Override
    public boolean execute(Ability ability, ServerLuaTriggerEvent action, byte[] abilityData, GameEntity target) {
        val scene = target.getScene();
        val scriptManager = scene.getScriptManager();
        val callType = LuaCallType.fromString(action.luaCallType);

        val params = switch (callType) {
            case FROM_GROUP -> {
                var groupId = target.getGroupId();
                yield Lists.newArrayList(new ScriptArgs(groupId, EventType.EVENT_LUA_NOTIFY));
            }
            case SPECIFIC_GROUP -> action.callParamList.stream()
                .map(groupId -> new ScriptArgs(groupId, EventType.EVENT_LUA_NOTIFY))
                .toList();
            default -> {
                Grasscutter.getLogger().error("Unimplemented ActionServerLuaCall {}", action.luaCallType);
                yield null;
            }
        };

        if(params == null) return false;
        params.forEach(p -> {
            p.setTargetEntityId(target.getId());
            p.setSourceEntityId(target.getId());
            p.setEventSource(action.sourceName);

            p.setParam1((int) AbilityMath.evaluate(action.param1, ability));
            p.setParam2((int) AbilityMath.evaluate(action.param2, ability));
            p.setParam3((int) AbilityMath.evaluate(action.param3, ability));

            scriptManager.callEvent(p);
        });
        return true;
    }
}
