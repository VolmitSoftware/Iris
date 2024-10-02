/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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

package com.volmit.iris.core.pregenerator.methods;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.nms.IHeadless;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.core.pregenerator.PregenListener;
import com.volmit.iris.core.pregenerator.PregeneratorMethod;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.mantle.Mantle;
import com.volmit.iris.util.parallel.MultiBurst;
import org.bukkit.World;

import java.io.IOException;
import java.util.concurrent.Semaphore;

public class HeadlessPregenMethod implements PregeneratorMethod {
    private final Engine engine;
    private final IHeadless headless;
    private final Semaphore semaphore;
    private final int max;
    private final World world;
    private final MultiBurst burst;

    public HeadlessPregenMethod(Engine engine) {
        this.world = engine.getWorld().realWorld();
        this.max = IrisSettings.getThreadCount(IrisSettings.get().getConcurrency().getParallelism());
        this.engine = engine;
        this.headless = INMS.get().createHeadless(engine);
        burst = new MultiBurst("HeadlessPregen", 8 );
        this.semaphore = new Semaphore(max);
    }

    @Override
    public void init() {
    }

    @Override
    public void close() {
        try {
            semaphore.acquire(max);
        } catch (InterruptedException ignored) {
        }
        try {
            headless.close();
        } catch (IOException e) {
            Iris.error("Failed to close headless");
            e.printStackTrace();
        }
        burst.close();
    }

    @Override
    public void save() {
    }

    @Override
    public boolean supportsRegions(int x, int z, PregenListener listener) {
        return false;
    }

    @Override
    public String getMethod(int x, int z) {
        return "Headless";
    }

    @Override
    public void generateRegion(int x, int z, PregenListener listener) {
    }

    @Override
    public void generateChunk(int x, int z, PregenListener listener) {
        try {
            semaphore.acquire();
        } catch (InterruptedException ignored) {
            semaphore.release();
            return;
        }
        burst.complete(() -> {
            try {
                listener.onChunkGenerating(x, z);
                headless.generateChunk(x, z);
                listener.onChunkGenerated(x, z);
            } finally {
                semaphore.release();
            }
        });
    }

    @Override
    public Mantle getMantle() {
        return engine.getMantle().getMantle();
    }

    @Override
    public World getWorld() {
        return world;
    }
}
