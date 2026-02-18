package art.arcane.iris.engine.object;

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.annotations.*;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.collection.KSet;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.iris.util.project.noise.CNG;
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
    @MinNumber(0)
    @MaxNumber(1)
    @Desc("The threshold for noise mask")
    private double noiseThreshold = -1;

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
        return cache.computeIfAbsent(randomRotation(rng), pos -> {
            var rotated = new KSet<IrisPosition>();
            engine.getData().getObjectLoader().load(object).getBlocks().forEach((vector, data) -> {
                if (data.getMaterial().isAir()) return;
                rotated.add(new IrisPosition(objectRotation.rotate(vector, pos.getX(), pos.getY(), pos.getZ())));
            });
            return rotated;
        });
    }

    private IrisPosition randomRotation(RNG rng) {
        if (objectRotation == null || !objectRotation.canRotate())
            return new IrisPosition(0,0,0);
        return new IrisPosition(
                randomDegree(rng, objectRotation.getXAxis()),
                randomDegree(rng, objectRotation.getYAxis()),
                randomDegree(rng, objectRotation.getZAxis())
        );
    }

    private int randomDegree(RNG rng, IrisAxisRotationClamp clamp) {
        if (!clamp.isEnabled()) return 0;
        if (clamp.isLocked()) return (int) clamp.getMax();
        double interval = clamp.getInterval();
        if (interval < 1) interval = 1;

        double min = clamp.getMin(), max = clamp.getMax();
        double value = (interval * (Math.ceil(Math.abs(rng.d(0, 360) / interval)))) % 360D;
        if (clamp.isUnlimited()) return (int) value;

        if (min > max) {
            max = clamp.getMin();
            min = clamp.getMax();
        }
        return (int) (double) M.clip(value, min, max);
    }
}
