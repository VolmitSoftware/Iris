/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.engine.platform;

import com.volmit.iris.Iris;
import com.volmit.iris.core.events.IrisEngineHotloadEvent;
import com.volmit.iris.core.project.loader.IrisData;
import com.volmit.iris.engine.IrisEngine;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.EngineTarget;
import com.volmit.iris.engine.object.common.IrisWorld;
import com.volmit.iris.engine.object.dimensional.IrisDimension;
import com.volmit.iris.util.parallel.MultiBurst;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class EngineProvider {
    private final AtomicReference<CompletableFuture<Engine>> engine = new AtomicReference<>();

    public void provideEngine(IrisWorld world, String dimension, File dataLocation, boolean studio, Consumer<Engine> post) {
        close();
        engine.set(MultiBurst.burst.completeValue(() -> {
            IrisData data = new IrisData(dataLocation);
            IrisDimension realDimension = data.getDimensionLoader().load(dimension);

            if(realDimension == null)
            {
                throw new RuntimeException("Cannot find dimension in " + data.getDataFolder().getAbsolutePath() + " with key " + dimension);
            }

            EngineTarget target = new EngineTarget(world, realDimension, data);
            Engine engine = new IrisEngine(target, studio);
            post.accept(engine);
            return engine;
        }));
        engine.get().whenComplete((e, x) -> Iris.callEvent(new IrisEngineHotloadEvent(e)));
    }

    public Engine getEngine() {
        try {
            Engine e = engine.get().get();

            if (e == null) {
                throw new RuntimeException("NULL");
            }

            return e;
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException("INTERRUPTED");
        } catch (ExecutionException e) {
            e.printStackTrace();
            throw new RuntimeException("EXECUTION ERROR");
        }
    }

    public void close() {
        if (engine.get() != null && engine.get().isDone()) {
            Engine e = getEngine();

            if (e != null) {
                e.close();
            }
        }
    }
}
