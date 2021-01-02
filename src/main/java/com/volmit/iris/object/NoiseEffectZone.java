package com.volmit.iris.object;

import com.volmit.iris.scaffold.cache.AtomicCache;
import com.volmit.iris.util.*;
import lombok.Data;

@Data
@DontObfuscate
@Desc("Represents a flat zone")
public class NoiseEffectZone {


    @Required
    @DontObfuscate
    @Desc("The x coordinate of this zone")
    private int x;

    @Required
    @DontObfuscate
    @Desc("The z coordinate of this zone")
    private int z;

    @Required
    @DontObfuscate
    @Desc("The block radius of this zone")
    private double blockRadius = 32;

    @Required
    @DontObfuscate
    @Desc("The interpolation radius of this zone")
    private double interpolationRadius = 7;

    @Required
    @DontObfuscate
    @MaxNumber(1)
    @MinNumber(0)
    @Desc("The strength of this effect")
    private double strength = 0.75;

    @Required
    @DontObfuscate
    @Desc("The interpolator to use for smoothing the strength")
    private InterpolationMethod interpolator = InterpolationMethod.BILINEAR_STARCAST_9;

    @Required
    @DontObfuscate
    @Desc("If set, this will shift the terrain height in blocks (up or down)")
    private double shiftHeight = 0;

    @Required
    @DontObfuscate
    @Desc("If set, this will force the terrain closer to the specified height.")
    private double convergeToHeight = -1;

    @Required
    @DontObfuscate
    @Desc("Multiplies the input noise")
    private double multiplyHeight = 1;

    @Required
    @DontObfuscate
    @Desc("Invert the zone so that anything outside this zone is affected.")
    private boolean invertZone = false;

    @Required
    @DontObfuscate
    @Desc("Add additional noise to this spot")
    private IrisGeneratorStyle addNoise = NoiseStyle.FLAT.style();

    private transient AtomicCache<NoiseProvider> provider = new AtomicCache<>();
    private static double BLOCK = 1D / 256D;

    public double filter(double x, double z, double noise)
    {
        if(invertZone ? distance2(x, z) < (blockRadius + (interpolationRadius * 5)) * (blockRadius + + (interpolationRadius * 5)) : distance2(x, z) > (blockRadius + interpolationRadius) * (blockRadius + interpolationRadius))
        {
            return noise;
        }

        NoiseProvider d = provider.aquire(this::getNoiseProvider);
        double s = IrisInterpolation.getNoise(interpolator, (int)x, (int)z, interpolationRadius, d);

        if(s <= 0)
        {
            return noise;
        }

        double fx = noise;

        if(convergeToHeight >= 0)
        {
            fx = convergeToHeight;
        }

        fx *= multiplyHeight;
        fx += shiftHeight;

        return M.lerp(noise, fx, strength * s);
    }

    public double distance(double x, double z) {
        return Math.sqrt(Math.pow(this.x - x, 2) + Math.pow(this.z - z, 2));
    }

    public double distance2(double x, double z) {
        return Math.pow(this.x - x, 2) + Math.pow(this.z - z, 2);
    }

    private NoiseProvider getNoiseProvider() {
        return (x, z) -> distance(x, z) < blockRadius ? invertZone ? 0D : 1d : invertZone ? 1D : 0d;
    }
}
