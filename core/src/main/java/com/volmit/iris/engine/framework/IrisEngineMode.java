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

package com.volmit.iris.engine.framework;

import com.volmit.iris.util.collection.KList;

public abstract class IrisEngineMode implements EngineMode {
    private final Engine engine;
    private final KList<EngineStage> stages;
    private boolean closed;

    public IrisEngineMode(Engine engine) {
        this.engine = engine;
        this.stages = new KList<>();
        this.closed = false;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }

        closed = true;
        dump();
    }

    @Override
    public Engine getEngine() {
        return engine;
    }

    @Override
    public KList<EngineStage> getStages() {
        return stages;
    }

    @Override
    public void registerStage(EngineStage stage) {
        stages.add(stage);
    }
}
