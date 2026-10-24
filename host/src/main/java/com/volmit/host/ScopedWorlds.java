package com.volmit.host;

import com.volmit.ModuleLog;
import com.volmit.world.PackWorld;
import java.util.Objects;

/** The views of worlds one module's stages get, whose log is the module's own ({@link ModuleWorld}). It keeps
 * the view of the world it was last asked for, so the stages of a host that generates one world per resolved graph
 * get it without a new view per invocation; a host that alternates worlds on one graph asks the world each time.
 * The kept world is released when the next one is asked for or the graph is dropped. Safe for concurrent use.
 */
final class ScopedWorlds {
    private final ModuleLog log;
    private volatile Scoped last;

    ScopedWorlds(ModuleLog log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    PackWorld of(PackWorld world) {
        var scoped = last;
        if (scoped != null && scoped.world() == world) {
            return scoped.view();
        }
        var view = ModuleWorld.of(world, log);
        last = new Scoped(world, view);
        return view;
    }

    private record Scoped(PackWorld world, PackWorld view) {}
}
