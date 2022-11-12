package com.volmit.iris.engine.object.matter;

import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.object.IrisStyledRange;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.util.math.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode()
@Accessors(chain = true)
@Desc("Represents a matter translator")
public class IrisMatterTranslate {
    @Desc("For varied coordinate shifts use ranges not the literal coordinate")
    private IrisStyledRange rangeX = null;
    @Desc("For varied coordinate shifts use ranges not the literal coordinate")
    private IrisStyledRange rangeY = null;
    @Desc("For varied coordinate shifts use ranges not the literal coordinate")
    private IrisStyledRange rangeZ = null;
    @Desc("Define an absolute shift instead of varied.")
    private int x = 0;
    @Desc("Define an absolute shift instead of varied.")
    private int y = 0;
    @Desc("Define an absolute shift instead of varied.")
    private int z = 0;

    public int xOffset(IrisData data, RNG rng, int rx, int rz) {
        if (rangeX != null) {
            return (int) Math.round(rangeX.get(rng, rx, rz, data));
        }

        return x;
    }

    public int yOffset(IrisData data, RNG rng, int rx, int rz) {
        if (rangeY != null) {
            return (int) Math.round(rangeY.get(rng, rx, rz, data));
        }

        return y;
    }

    public int zOffset(IrisData data, RNG rng, int rx, int rz) {
        if (rangeZ != null) {
            return (int) Math.round(rangeZ.get(rng, rx, rz, data));
        }

        return z;
    }
}
