package com.volmit.iris.object;

import com.google.gson.Gson;
import com.volmit.iris.scaffold.cache.AtomicCache;
import com.volmit.iris.util.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@Data
@DontObfuscate
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents an Iris zone")
public class IrisFeature {
    @Required
    @DontObfuscate
    @Desc("The block radius of this zone")
    private double blockRadius = 32;

    @MinNumber(0)
    @MaxNumber(1)
    @Required
    @DontObfuscate
    @Desc("The chance an object that should be place actually will place. Set to below 1 to affect objects in this zone")
    private double objectChance = 1;

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


    private transient AtomicCache<Double> actualRadius = new AtomicCache<>();
    public double getActualRadius()
    {
        return actualRadius.aquire(() -> IrisInterpolation.getRealRadius(getInterpolator(),getInterpolationRadius()));
    }

    public static IrisFeature read(DataInputStream s) throws IOException
    {
        return new Gson().fromJson(s.readUTF(), IrisFeature.class);
    }

    public void write(DataOutputStream s) throws IOException {
        s.writeUTF(new Gson().toJson(this));
    }

    public int getRealSize() {
        return (int) Math.ceil((getActualRadius() + blockRadius) * 2);
    }
}
