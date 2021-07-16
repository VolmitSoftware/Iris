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

package com.volmit.iris.util.fakenews;

import com.volmit.iris.core.IrisDataManager;
import com.volmit.iris.engine.IrisEngineFramework;
import com.volmit.iris.engine.framework.*;
import com.volmit.iris.engine.hunk.Hunk;
import com.volmit.iris.engine.object.*;
import lombok.Getter;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;


public class FakeEngine implements Engine {


    @Getter
    private double maxBiomeObjectDensity;

    @Getter
    private double maxBiomeLayerDensity;

    @Getter
    private double maxBiomeDecoratorDensity;

    @Getter
    private final IrisDimension dimension;

    private final EngineFramework framework;

    @Getter
    private final World world;

    public FakeEngine(IrisDimension dimension, FakeWorld world) {
        this.dimension = dimension;
        this.world = world;
        computeBiomeMaxes();
        this.framework = new IrisEngineFramework(this);
    }

    private void computeBiomeMaxes() {
        for (IrisBiome i : getDimension().getAllBiomes(this)) {
            double density = 0;

            for (IrisObjectPlacement j : i.getObjects()) {
                density += j.getDensity() * j.getChance();
            }

            maxBiomeObjectDensity = Math.max(maxBiomeObjectDensity, density);
            density = 0;

            for (IrisDecorator j : i.getDecorators()) {
                density += Math.max(j.getStackMax(), 1) * j.getChance();
            }

            maxBiomeDecoratorDensity = Math.max(maxBiomeDecoratorDensity, density);
            density = 0;

            for (IrisBiomePaletteLayer j : i.getLayers()) {
                density++;
            }

            maxBiomeLayerDensity = Math.max(maxBiomeLayerDensity, density);
        }
    }

    @Override
    public void close() {
    }

    @Override
    public boolean isClosed() {
        return false;
    }

    @Override
    public IrisDataManager getData() {
        return dimension.getLoader().copy();
    }

    @Override
    public EngineWorldManager getWorldManager() {
        return null;
    }

    @Override
    public void setParallelism(int parallelism) {
    }

    @Override
    public int getParallelism() {
        return 0;
    }

    @Override
    public EngineTarget getTarget() {
        return null;
    }

    @Override
    public EngineFramework getFramework() {
        return null;
    }

    @Override
    public void setMinHeight(int min) {
    }

    @Override
    public void recycle() {
    }

    @Override
    public int getIndex() {
        return 0;
    }

    @Override
    public int getMinHeight() {
        return 0;
    }

    @Override
    public int getHeight() {
        return 64;
    }

    @Override
    public double modifyX(double x) {
        return 0;
    }

    @Override
    public double modifyZ(double z) {
        return 0;
    }

    @Override
    public void generate(int x, int z, Hunk<BlockData> blocks, Hunk<Biome> biomes) {
    }

    @Override
    public EngineMetrics getMetrics() {
        return null;
    }

    @Override
    public EngineEffects getEffects() {
        return null;
    }

    @Override
    public EngineCompound getCompound() {
        return null;
    }

    @Override
    public IrisBiome getFocus() {
        return null;
    }

    @Override
    public void fail(String error, Throwable e) {
    }

    @Override
    public boolean hasFailed() {
        return false;
    }

    @Override
    public int getCacheID() {
        return 0;
    }

    @Override
    public void hotload() {
    }
}
