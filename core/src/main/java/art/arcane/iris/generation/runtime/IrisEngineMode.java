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

package art.arcane.iris.generation.runtime;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.iris.generation.stage.IrisTransitionGeometryActuator;

public abstract class IrisEngineMode implements EngineMode {
    private final Engine engine;
    private final EngineStage transitionStage;
    private final KList<EngineStage> stages;
    private final KList<EngineStage> terrainStages;
    private boolean closed;

    public IrisEngineMode(Engine engine) {
        this.engine = engine;
        this.transitionStage = new IrisTransitionGeometryActuator(engine);
        this.stages = new KList<>();
        this.terrainStages = new KList<>();
        this.closed = false;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }

        dump();
        terrainStages.forEach(EngineStage::close);
        terrainStages.clear();
        transitionStage.close();
        closed = true;
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
    public EngineStage getTransitionStage() {
        return transitionStage;
    }

    @Override
    public KList<EngineStage> getTerrainStages() {
        return terrainStages;
    }

    @Override
    public void registerTerrainStage(EngineStage stage) {
        terrainStages.add(stage);
    }

    @Override
    public void registerStage(EngineStage stage) {
        stages.add(stage);
    }
}
