package com.volmit.iris.object;

import com.google.gson.Gson;
import com.volmit.iris.scaffold.cache.AtomicCache;
import com.volmit.iris.util.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@Data
@DontObfuscate
@NoArgsConstructor
@Desc("Represents an Iris zone")
public class IrisFeaturePositional {
    public IrisFeaturePositional(int x, int z, IrisFeature feature)
    {
        this.x = x;
        this.z = z;
        this.feature = feature;
    }

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
    @Desc("The Terrain Feature to apply")
    private IrisFeature feature;

    private transient AtomicCache<NoiseProvider> provider = new AtomicCache<>();
    private static double BLOCK = 1D / 256D; // TODO: WARNING HEIGHT

    public static IrisFeaturePositional read(DataInputStream s) throws IOException
    {
        return new Gson().fromJson(s.readUTF(), IrisFeaturePositional.class);
    }

    public void write(DataOutputStream s) throws IOException {
        s.writeUTF(new Gson().toJson(this));
    }

    public boolean shouldFilter(double x, double z)
    {
        double actualRadius = getFeature().getActualRadius();
        double dist2 = distance2(x, z);

        if(getFeature().isInvertZone()) {
            if (dist2 < Math.pow(getFeature().getBlockRadius() - actualRadius, 2)) {
                return false;
            }
        }

        if(dist2 > Math.pow(getFeature().getBlockRadius() + actualRadius, 2))
        {
            return false;
        }

        return true;
    }

    public double getStrength(double x, double z)
    {
        double actualRadius = getFeature().getActualRadius();
        double dist2 = distance2(x, z);

        if(getFeature().isInvertZone())
        {
            if (dist2 < Math.pow(getFeature().getBlockRadius() - actualRadius, 2))
            {
                return 0;
            }

            NoiseProvider d = provider.aquire(this::getNoiseProvider);
            double s = IrisInterpolation.getNoise(getFeature().getInterpolator(), (int)x, (int)z, getFeature().getInterpolationRadius(), d);

            if(s <= 0)
            {
                return 0;
            }

            return getFeature().getStrength() * s;
        }

        else
        {
            if(dist2 > Math.pow(getFeature().getBlockRadius() + actualRadius, 2))
            {
                return 0;
            }

            NoiseProvider d = provider.aquire(this::getNoiseProvider);
            double s = IrisInterpolation.getNoise(getFeature().getInterpolator(), (int)x, (int)z, getFeature().getInterpolationRadius(), d);

            if(s <= 0)
            {
                return 0;
            }

            return getFeature().getStrength() * s;
        }
    }

    public double getObjectChanceModifier(double x, double z)
    {
        if(getFeature().getObjectChance()>=1)
        {
            return getFeature().getObjectChance();
        }

        return M.lerp(1, getFeature().getObjectChance(), getStrength(x, z));
    }

    public double filter(double x, double z, double noise) {
        double s = getStrength(x, z);

        if(s <= 0)
        {
            return noise;
        }

        double fx = noise;

        if(getFeature().getConvergeToHeight() >= 0)
        {
            fx = getFeature().getConvergeToHeight();
        }

        fx *= getFeature().getMultiplyHeight();
        fx += getFeature().getShiftHeight();

        return M.lerp(noise, fx, s);
    }

    public double distance(double x, double z) {
        return Math.sqrt(Math.pow(this.x - x, 2) + Math.pow(this.z - z, 2));
    }

    public double distance2(double x, double z) {
        return Math.pow(this.x - x, 2) + Math.pow(this.z - z, 2);
    }

    private NoiseProvider getNoiseProvider() {
        if(getFeature().isInvertZone())
        {
            return (x, z) -> distance(x, z) > getFeature().getBlockRadius() ? 1D : 0D;
        }

        else
        {
            return (x, z) -> distance(x, z) < getFeature().getBlockRadius() ? 1D : 0D;
        }
    }
}
