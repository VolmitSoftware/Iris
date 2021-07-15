package com.volmit.iris.util;

import com.volmit.iris.generator.IrisEngineFramework;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.object.*;
import com.volmit.iris.scaffold.engine.Engine;
import com.volmit.iris.scaffold.engine.EngineCompound;
import com.volmit.iris.scaffold.engine.EngineEffects;
import com.volmit.iris.scaffold.engine.EngineFramework;
import com.volmit.iris.scaffold.engine.EngineMetrics;
import com.volmit.iris.scaffold.engine.EngineTarget;
import com.volmit.iris.scaffold.engine.EngineWorldManager;
import com.volmit.iris.scaffold.hunk.Hunk;
import com.volmit.iris.util.FakeWorld;
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
    private IrisDimension dimension;

    private EngineFramework framework;

    @Getter
    private World world;

    public FakeEngine(IrisDimension dimension, FakeWorld world) {
        this.dimension = dimension;
        this.world = world;
        computeBiomeMaxes();
        this.framework = new IrisEngineFramework(this);
    }

    private void computeBiomeMaxes() {
        for(IrisBiome i : getDimension().getAllBiomes(this))
        {
            double density = 0;

            for(IrisObjectPlacement j : i.getObjects())
            {
                density += j.getDensity() * j.getChance();
            }

            maxBiomeObjectDensity = Math.max(maxBiomeObjectDensity, density);
            density = 0;

            for(IrisDecorator j : i.getDecorators())
            {
                density += Math.max(j.getStackMax(), 1) * j.getChance();
            }

            maxBiomeDecoratorDensity = Math.max(maxBiomeDecoratorDensity, density);
            density = 0;

            for(IrisBiomePaletteLayer j : i.getLayers())
            {
                density++;
            }

            maxBiomeLayerDensity = Math.max(maxBiomeLayerDensity, density);
        }
    }

    @Override
    public void close() {    }

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
    public void setParallelism(int parallelism) { }

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
    public void setMinHeight(int min) { }

    @Override
    public void recycle() { }

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
    public void generate(int x, int z, Hunk<BlockData> blocks, Hunk<Biome> biomes) { }

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
    public void fail(String error, Throwable e) { }

    @Override
    public boolean hasFailed() {
        return false;
    }

    @Override
    public int getCacheID() {
        return 0;
    }

    @Override
    public void hotload() { }
}
