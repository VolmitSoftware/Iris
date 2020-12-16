package com.volmit.iris.generator;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.scaffold.engine.*;
import com.volmit.iris.scaffold.hunk.Hunk;
import com.volmit.iris.scaffold.parallel.MultiBurst;
import com.volmit.iris.util.J;
import com.volmit.iris.util.PrecisionStopwatch;
import com.volmit.iris.util.RNG;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.BlockPopulator;
import org.jetbrains.annotations.NotNull;

import java.util.Random;
import java.util.concurrent.Semaphore;

public class IrisEngine extends BlockPopulator implements Engine
{
    @Getter
    private final EngineCompound compound;

    @Getter
    private final EngineTarget target;

    @Getter
    private final EngineFramework framework;

    @Getter
    private final EngineEffects effects;

    @Getter
    private final EngineWorldManager worldManager;

    @Setter
    @Getter
    private volatile int parallelism;

    @Getter
    private final int index;

    @Getter
    private final EngineMetrics metrics;

    @Setter
    @Getter
    private volatile int minHeight;
    private int permits;
    private boolean failing;
    private boolean closed;
    private int cacheId;
    private Semaphore s;
    private int art;

    public IrisEngine(EngineTarget target, EngineCompound compound, int index)
    {
        Iris.info("Initializing Engine: " + target.getWorld().getName() + "/" + target.getDimension().getLoadKey() + " (" + target.getHeight() + " height)");
        metrics = new EngineMetrics(32);
        permits = 10000;
        this.s = new Semaphore(permits);
        this.target = target;
        this.framework = new IrisEngineFramework(this);
        worldManager = new IrisWorldManager(this);
        this.compound = compound;
        minHeight = 0;
        failing = false;
        closed = false;
        this.index = index;
        cacheId = RNG.r.nextInt();
        effects = new IrisEngineEffects(this);
        art = J.ar(effects::tickRandomPlayer, 0);
    }

    @Override
    public void close()
    {
        J.car(art);
        closed = true;
        getWorldManager().close();
        getFramework().close();
    }

    @Override
    public int getCurrentlyGenerating() {
        return permits - s.availablePermits();
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public double modifyX(double x) {
        return x / getDimension().getTerrainZoom();
    }

    @Override
    public double modifyZ(double z) {
        return z / getDimension().getTerrainZoom();
    }

    @Override
    public void generate(int x, int z, Hunk<BlockData> vblocks, Hunk<BlockData> postblocks, Hunk<Biome> vbiomes) {
        try
        {
            boolean multicore = !IrisSettings.get().isUseGleamPregenerator(); //TODO: LATER
            s.acquire(1);
            PrecisionStopwatch p = PrecisionStopwatch.start();
            Hunk<Biome> biomes = vbiomes;
            Hunk<BlockData> blocks = vblocks.synchronize().listen((xx,y,zz,t) -> catchBlockUpdates(x+xx,y+getMinHeight(),z+zz, t));
            Hunk<BlockData> pblocks = postblocks.synchronize().listen((xx,y,zz,t) -> catchBlockUpdates(x+xx,y+getMinHeight(),z+zz, t));
            Hunk<BlockData> fringe = Hunk.fringe(blocks, pblocks);

            if(multicore)
            {
                MultiBurst.burst.burst(
                        () -> getFramework().getEngineParallax().generateParallaxArea(x, z),
                        () -> getFramework().getBiomeActuator().actuate(x, z, biomes),
                        () -> getFramework().getTerrainActuator().actuate(x, z, blocks)
                );



                MultiBurst.burst.burst(
                        () -> getFramework().getCaveModifier().modify(x, z, blocks),
                        () ->  getFramework().getRavineModifier().modify(x, z, blocks),
                        () -> getFramework().getPostModifier().modify(x, z, blocks),
                        () ->  getFramework().getDecorantActuator().actuate(x, z, fringe),
                        () -> getFramework().getEngineParallax().insertParallax(x, z, fringe)
                );
            }

            else
            {
                getFramework().getEngineParallax().generateParallaxArea(x, z);
                getFramework().getBiomeActuator().actuate(x, z, biomes);
                getFramework().getTerrainActuator().actuate(x, z, blocks);
                getFramework().getCaveModifier().modify(x, z, blocks);
                getFramework().getRavineModifier().modify(x, z, blocks);
                getFramework().getPostModifier().modify(x, z, blocks);
                getFramework().getDecorantActuator().actuate(x, z, fringe);
                getFramework().getEngineParallax().insertParallax(x, z, fringe);
            }

            getFramework().getDepositModifier().modify(x, z, blocks);
            getMetrics().getTotal().put(p.getMilliseconds());
            s.release(1);
            getFramework().recycle();
        }

        catch(Throwable e)
        {
            fail("Failed to generate " + x + ", " + z, e);
        }
    }

    @Override
    public void populate(@NotNull World world, @NotNull Random random, @NotNull Chunk c)
    {
        getWorldManager().spawnInitialEntities(c);
        updateChunk(c);
    }

    @Override
    public void fail(String error, Throwable e) {
        failing = true;
        Iris.error(error);
        e.printStackTrace();
    }

    @Override
    public boolean hasFailed() {
        return failing;
    }

    @Override
    public int getCacheID() {
        return cacheId;
    }

    @Override
    public void hotload() {
        cacheId = RNG.r.nextInt();
    }
}
