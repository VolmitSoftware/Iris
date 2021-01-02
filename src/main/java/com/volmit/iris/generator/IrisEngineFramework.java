package com.volmit.iris.generator;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.generator.actuator.IrisBiomeActuator;
import com.volmit.iris.generator.actuator.IrisDecorantActuator;
import com.volmit.iris.generator.actuator.IrisTerrainActuator;
import com.volmit.iris.generator.modifier.IrisCaveModifier;
import com.volmit.iris.generator.modifier.IrisDepositModifier;
import com.volmit.iris.generator.modifier.IrisPostModifier;
import com.volmit.iris.generator.modifier.IrisRavineModifier;
import com.volmit.iris.scaffold.engine.*;
import com.volmit.iris.util.ChronoLatch;
import lombok.Getter;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

import java.util.concurrent.atomic.AtomicBoolean;

public class IrisEngineFramework implements EngineFramework {

    @Getter
    private final Engine engine;

    @Getter
    private final IrisComplex complex;

    @Getter
    final EngineParallaxManager engineParallax;

    @Getter
    private final EngineActuator<BlockData> terrainActuator;

    @Getter
    private final EngineActuator<BlockData> decorantActuator;

    @Getter
    private final EngineActuator<Biome> biomeActuator;

    @Getter
    private final EngineModifier<BlockData> depositModifier;

    @Getter
    private final EngineModifier<BlockData> caveModifier;

    @Getter
    private final EngineModifier<BlockData> ravineModifier;

    @Getter
    private final EngineModifier<BlockData> postModifier;

    private final AtomicBoolean cleaning;
    private final ChronoLatch cleanLatch;

    public IrisEngineFramework(Engine engine)
    {
        this.engine = engine;
        this.complex = new IrisComplex(getEngine());
        this.engineParallax = new IrisEngineParallax(getEngine());
        this.terrainActuator = new IrisTerrainActuator(getEngine());
        this.decorantActuator = new IrisDecorantActuator(getEngine());
        this.biomeActuator = new IrisBiomeActuator(getEngine());
        this.depositModifier = new IrisDepositModifier(getEngine());
        this.ravineModifier = new IrisRavineModifier(getEngine());
        this.caveModifier = new IrisCaveModifier(engine);
        this.postModifier = new IrisPostModifier(engine);
        cleaning = new AtomicBoolean(false);
        cleanLatch = new ChronoLatch(Math.max(10000, Math.min(IrisSettings.get().getParallax().getParallaxChunkEvictionMS(), IrisSettings.get().getParallax().getParallaxRegionEvictionMS())));
    }

    @Override
    public synchronized void recycle() {
        if(!cleanLatch.flip())
        {
            return;
        }

        if (cleaning.get())
        {
            cleanLatch.flipDown();
            return;
        }

        cleaning.set(true);

        try
        {
            getEngine().getParallax().cleanup();
            getData().getObjectLoader().clean();
        }

        catch(Throwable e)
        {
            Iris.error("Cleanup failed!");
            e.printStackTrace();
        }

        cleaning.lazySet(false);
    }

    @Override
    public void close()
    {
        getEngineParallax().close();
        getTerrainActuator().close();
        getDecorantActuator().close();
        getBiomeActuator().close();
        getDepositModifier().close();
        getRavineModifier().close();
        getCaveModifier().close();
        getPostModifier().close();
    }
}
