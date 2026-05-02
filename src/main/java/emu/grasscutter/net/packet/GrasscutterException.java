package emu.grasscutter.net.packet;

import emu.grasscutter.Grasscutter;
import lombok.Getter;
import lombok.SneakyThrows;
import org.anime_game_servers.multi_proto.gi.messages.general.Retcode;

import java.util.Objects;

public class GrasscutterException extends Exception {
    @Getter Retcode retcode;

    public GrasscutterException(String message) {
        this(message, null, null);
    }

    public GrasscutterException(String message, Exception ex) {
        this(message, ex, null);
    }

    public GrasscutterException(String message, Retcode retcode) {
        this(message, null, retcode);
    }

    public GrasscutterException(String message, Exception ex, Retcode retcode) {
        super(message, ex);
        this.retcode = retcode;
    }

    public GrasscutterException(Exception ex) {
        this(ex, null);
    }

    public GrasscutterException(Exception ex, Retcode retcode) {
        super(ex);
        this.retcode = retcode;
    }

    public <R> Retcode logAndGetRetcode(R request) {
        // log the error with custom message
        Object[] args = { request.getClass().getSimpleName(), this.getMessage(), this.getCause() };
        Grasscutter.getLogger().error("Handle packet failed for {}: {}", args);
        return Objects.requireNonNullElse(this.getRetcode(), Retcode.RET_SVR_ERROR);
    }
}
