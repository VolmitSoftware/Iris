package com.volmit.iris.engine.object;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.framework.Engine;
import lombok.RequiredArgsConstructor;
import org.bukkit.event.Listener;

@RequiredArgsConstructor
public abstract class IrisEngineService implements Listener {
    protected final Engine engine;

    public abstract void onEnable(boolean hotload);

    public abstract void onDisable(boolean hotload);

    public final void postShutdown(Runnable r) {
        Iris.instance.postShutdown(r);
    }
}
