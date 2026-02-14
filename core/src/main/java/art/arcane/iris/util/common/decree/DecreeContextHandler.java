package art.arcane.iris.util.decree;

import art.arcane.volmlib.util.decree.context.DecreeContextHandlers;
import art.arcane.volmlib.util.decree.context.DecreeContextHandlerType;
import art.arcane.iris.Iris;
import art.arcane.iris.util.plugin.VolmitSender;

import java.util.Map;

public interface DecreeContextHandler<T> extends DecreeContextHandlerType<T, VolmitSender> {
    Map<Class<?>, DecreeContextHandler<?>> contextHandlers = buildContextHandlers();

    static Map<Class<?>, DecreeContextHandler<?>> buildContextHandlers() {
        return DecreeContextHandlers.buildOrEmpty(
                Iris.initialize("art.arcane.iris.util.decree.context"),
                DecreeContextHandler.class,
                h -> ((DecreeContextHandler<?>) h).getType(),
                e -> {
                    Iris.reportError(e);
                    e.printStackTrace();
                });
    }
}
