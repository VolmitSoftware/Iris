package com.volmit.iris.util.decree;

import com.volmit.iris.Iris;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.plugin.VolmitSender;

public interface DecreeContextHandler<T> {
    static KMap<Class<?>, DecreeContextHandler<?>> contextHandlers = buildContextHandlers();

    static KMap<Class<?>, DecreeContextHandler<?>> buildContextHandlers() {
        KMap<Class<?>, DecreeContextHandler<?>> contextHandlers = new KMap<>();

        try
        {
            Iris.initialize("com.volmit.iris.util.decree.handlers.context").forEach((i)
                    -> contextHandlers.put(((DecreeContextHandler<?>)i).getType(), (DecreeContextHandler<?>)i));
        }

        catch(Throwable e)
        {
            Iris.reportError(e);
            e.printStackTrace();
        }

        return contextHandlers;
    }

    Class<T> getType();

    T handle(VolmitSender sender);
}
