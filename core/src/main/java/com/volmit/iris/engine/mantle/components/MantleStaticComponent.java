package com.volmit.iris.engine.mantle.components;

import com.volmit.iris.engine.mantle.EngineMantle;
import com.volmit.iris.engine.mantle.IrisMantleComponent;
import com.volmit.iris.engine.mantle.MantleWriter;
import com.volmit.iris.engine.object.IrisObjectScale;
import com.volmit.iris.engine.object.IrisStaticPlacement;
import com.volmit.iris.engine.object.NoiseStyle;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.context.ChunkContext;
import com.volmit.iris.util.mantle.MantleFlag;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.noise.CNG;
import lombok.Getter;

public class MantleStaticComponent extends IrisMantleComponent {
    private final CNG cng;
    @Getter
    private final int radius = computeRadius();

    public MantleStaticComponent(EngineMantle engineMantle) {
        super(engineMantle, MantleFlag.STATIC, 1);
        cng = NoiseStyle.STATIC.create(new RNG(seed()));
    }

    @Override
    public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
        RNG rng = new RNG(cng.fit(Integer.MIN_VALUE, Integer.MAX_VALUE, x, z));
        for (IrisStaticPlacement placement : getDimension().getStaticPlacements().getAll(x, z)) {
            placement.place(writer, rng, getData());
        }
    }

    private int computeRadius() {
        var placements = getDimension().getStaticPlacements();

        KSet<String> objects = new KSet<>();
        KMap<IrisObjectScale, KList<String>> scalars = new KMap<>();
        for (var staticPlacement : placements.getObjects()) {
            var placement = staticPlacement.placement();
            if (placement.getScale().canScaleBeyond()) {
                scalars.put(placement.getScale(), placement.getPlace());
            } else {
                objects.addAll(placement.getPlace());
            }
        }

        int jigsaw = placements.getStructures()
                .stream()
                .mapToInt(staticPlacement -> staticPlacement.maxDimension(getData()))
                .max()
                .orElse(0);
        int object = MantleObjectComponent.computeObjectRadius(objects, scalars, getEngineMantle().getTarget().getBurster(), getData());

        return Math.max(jigsaw, object);
    }
}
