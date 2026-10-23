package emu.grasscutter.game.entity.gadget.platform;

import emu.grasscutter.data.binout.routes.Route;
import emu.grasscutter.data.binout.routes.RouteType;
import emu.grasscutter.utils.Position;
import lombok.Getter;
import lombok.Setter;
import lombok.val;
import org.anime_game_servers.multi_proto.gi.messages.scene.entity.MovingPlatformType;
import org.anime_game_servers.multi_proto.gi.messages.scene.entity.PlatformInfo;
import org.anime_game_servers.gi_lua.models.scene.group.SceneGadget;

public class ConfigRoute extends BaseRoute {

    @Getter @Setter private int routeId;
    /** A Reciprocate route heading back to its first point */
    @Getter @Setter private boolean reversed;

    public ConfigRoute(Position startRot, boolean startRoute, boolean isActive, int routeId) {
        super(startRot, startRoute, isActive);
        this.routeId = routeId;
    }

    public ConfigRoute(Position startRot, int routeId) {
        super(startRot);
        this.routeId = routeId;
    }

    @Override
    public PlatformInfo toProto() {
        val proto = super.toProto();
        proto.setRouteId(routeId);
        proto.setMovingPlatformType(MovingPlatformType.MOVING_PLATFORM_USE_CONFIG);
        return proto;
    }

    /** Turns a Reciprocate route around at either end, before it heads to its next point */
    public void turnAtEnd(Route route) {
        if (route.getType() != RouteType.Reciprocate) return;
        int last = route.getPoints().length - 1;
        if (getStartIndex() >= last) this.reversed = true;
        else if (getStartIndex() <= 0) this.reversed = false;
    }

    /** Whether the current point ends the route: the last one of a route neither looping nor reciprocating */
    public boolean isAtEnd(Route route) {
        return route.getType() != RouteType.Loop && route.getType() != RouteType.Reciprocate
            && getStartIndex() >= route.getPoints().length - 1;
    }

    /** The point after the current one: a Loop route wraps around, a Reciprocate one follows its direction */
    public int nextIndex(Route route) {
        int last = route.getPoints().length - 1;
        int index = getStartIndex();
        return switch (route.getType()) {
            case Loop -> index >= last ? 0 : index + 1;
            case Reciprocate -> this.reversed ? index - 1 : index + 1;
            default -> index + 1;
        };
    }
}
