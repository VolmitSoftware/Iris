package com.volmit.iris.engine.mantle.components;

import com.volmit.iris.engine.mantle.EngineMantle;
import com.volmit.iris.engine.mantle.IrisMantleComponent;
import com.volmit.iris.engine.mantle.MantleWriter;
import com.volmit.iris.engine.object.IrisStaticPlacement;
import com.volmit.iris.engine.object.NoiseStyle;
import com.volmit.iris.util.context.ChunkContext;
import com.volmit.iris.util.mantle.MantleFlag;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.noise.CNG;

public class MantleStaticComponent extends IrisMantleComponent {
    private final CNG cng;

    public MantleStaticComponent(EngineMantle engineMantle) {
        super(engineMantle, MantleFlag.STATIC);
        cng = NoiseStyle.STATIC.create(new RNG(seed()));
    }

    @Override
    public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
        RNG rng = new RNG(cng.fit(Integer.MIN_VALUE, Integer.MAX_VALUE, x, z));
        for (IrisStaticPlacement placement : getDimension().getStaticPlacements().getAll(x, z)) {
            placement.place(writer, rng, getData());
        }
    }
}
