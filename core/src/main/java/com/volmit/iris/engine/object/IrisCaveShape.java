package com.volmit.iris.engine.object;

import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.RegistryListResource;
import com.volmit.iris.engine.object.annotations.Snippet;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.noise.CNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("cave-shape")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Cave Shape")
@Data
public class IrisCaveShape {
    private transient final KMap<IrisPosition, KSet<IrisPosition>> cache = new KMap<>();

    @Desc("Noise used for the shape of the cave")
    private IrisGeneratorStyle noise = new IrisGeneratorStyle();
    @RegistryListResource(IrisObject.class)
    @Desc("Object used as mask for the shape of the cave")
    private String object = null;
    @Desc("Rotation to apply to objects before using them as mask")
    private IrisObjectRotation objectRotation = new IrisObjectRotation();

    public CNG getNoise(RNG rng, Engine engine) {
        return noise.create(rng, engine.getData());
    }

    public KSet<IrisPosition> getMasked(RNG rng, Engine engine) {
        if (object == null) return null;
        return cache.computeIfAbsent(new IrisPosition(
                        rng.i(0, 360),
                        rng.i(0, 360),
                        rng.i(0, 360)),
                pos -> {
                    var rotated = new KSet<IrisPosition>();
                    engine.getData().getObjectLoader().load(object).getBlocks().forEach((vector, data) -> {
                        if (data.getMaterial().isAir()) return;
                        rotated.add(new IrisPosition(objectRotation.rotate(vector, pos.getX(), pos.getY(), pos.getZ())));
                    });
                    return rotated;
                });
    }
}
