package art.arcane.iris.command;

import art.arcane.volmlib.util.director.context.DirectorContextHandlers;
import art.arcane.volmlib.util.director.context.DirectorContextHandlerType;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;

import java.util.Map;

public interface DirectorContextHandler<T> extends DirectorContextHandlerType<T, VolmitSender> {
    Map<Class<?>, DirectorContextHandler<?>> contextHandlers = buildContextHandlers();

    static Map<Class<?>, DirectorContextHandler<?>> buildContextHandlers() {
        return DirectorContextHandlers.buildOrEmpty(
                DirectorSystem.initializePackage("art.arcane.iris.command.context"),
                DirectorContextHandler.class,
                h -> ((DirectorContextHandler<?>) h).getType(),
                e -> {
                    IrisLogging.reportError(e);
                });
    }
}
