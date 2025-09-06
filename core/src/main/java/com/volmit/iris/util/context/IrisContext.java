/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.volmit.iris.util.context;

import com.volmit.iris.Iris;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.IrisComplex;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.scheduling.ChronoLatch;
import lombok.Data;

@Data
public class IrisContext {
    private static final KMap<Thread, IrisContext> context = new KMap<>();
    private static final ChronoLatch cl = new ChronoLatch(60000);
    private final Engine engine;
    private ChunkContext chunkContext;

    public IrisContext(Engine engine) {
        this.engine = engine;
    }

    public static IrisContext getOr(Engine engine) {
        IrisContext c = get();

        if (c == null) {
            c = new IrisContext(engine);
            touch(c);
        }

        return c;
    }

    public static IrisContext get() {
        return context.get(Thread.currentThread());
    }

    public static void touch(IrisContext c) {
        context.put(Thread.currentThread(), c);

        if (!cl.couldFlip()) return;
        synchronized (cl) {
            if (cl.flip()) {
                dereference();
            }
        }
    }

    public static void dereference() {
        for (Thread i : context.k()) {
            if (!i.isAlive() || context.get(i).engine.isClosed()) {
                if (context.get(i).engine.isClosed()) {
                    Iris.debug("Dereferenced Context<Engine> " + i.getName() + " " + i.threadId());
                }

                context.remove(i);
            }
        }
    }

    public void touch() {
        IrisContext.touch(this);
    }

    public IrisData getData() {
        return engine.getData();
    }

    public IrisComplex getComplex() {
        return engine.getComplex();
    }

    public KMap<String, Object> asContext() {
        var hash32 = engine.getHash32().getNow(null);
        var dimension = engine.getDimension();
        var mantle = engine.getMantle();
        return new KMap<String, Object>()
                .qput("studio", engine.isStudio())
                .qput("closed", engine.isClosed())
                .qput("pack", new KMap<>()
                        .qput("key", dimension == null ? "" : dimension.getLoadKey())
                        .qput("version", dimension == null ? "" : dimension.getVersion())
                        .qput("hash", hash32 == null ? "" : Long.toHexString(hash32)))
                .qput("mantle", new KMap<>()
                        .qput("idle", mantle.getAdjustedIdleDuration())
                        .qput("loaded", mantle.getLoadedRegionCount())
                        .qput("queued", mantle.getUnloadRegionCount()));
    }
}
