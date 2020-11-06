package com.volmit.iris.v2.generator;

import com.volmit.iris.v2.generator.actuator.*;
import com.volmit.iris.v2.generator.modifier.IrisCaveModifier;
import com.volmit.iris.v2.generator.modifier.IrisDepositModifier;
import com.volmit.iris.v2.generator.modifier.IrisPostModifier;
import com.volmit.iris.v2.generator.modifier.IrisRavineModifier;
import com.volmit.iris.v2.scaffold.engine.*;
import lombok.Getter;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

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
