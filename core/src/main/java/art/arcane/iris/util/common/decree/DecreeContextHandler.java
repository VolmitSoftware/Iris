package art.arcane.iris.util.decree;

import art.arcane.volmlib.util.director.context.DirectorContextHandlers;
import art.arcane.volmlib.util.director.context.DirectorContextHandlerType;
import art.arcane.iris.Iris;
import art.arcane.iris.util.plugin.VolmitSender;

import java.util.Map;

public interface DecreeContextHandler<T> extends DirectorContextHandlerType<T, VolmitSender> {
    Map<Class<?>, DecreeContextHandler<?>> contextHandlers = buildContextHandlers();

    static Map<Class<?>, DecreeContextHandler<?>> buildContextHandlers() {
        return DirectorContextHandlers.buildOrEmpty(
                Iris.initialize("art.arcane.iris.util.decree.context"),
                DecreeContextHandler.class,
                h -> ((DecreeContextHandler<?>) h).getType(),
                e -> {
                    Iris.reportError(e);
                    e.printStackTrace();
                });
    }
}
