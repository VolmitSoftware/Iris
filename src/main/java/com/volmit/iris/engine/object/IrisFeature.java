/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.volmit.iris.engine.object;

import com.google.gson.Gson;
import com.volmit.iris.engine.cache.AtomicCache;
import com.volmit.iris.util.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@Data

@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents an Iris zone")
public class IrisFeature {
    @Required

    @Desc("The block radius of this zone")
    private double blockRadius = 32;

    @MinNumber(0)
    @MaxNumber(1)
    @Required

    @Desc("The chance an object that should be place actually will place. Set to below 1 to affect objects in this zone")
    private double objectChance = 1;

    @Required

    @Desc("The interpolation radius of this zone")
    private double interpolationRadius = 7;

    @Required

    @MaxNumber(1)
    @MinNumber(0)
    @Desc("The strength of this effect")
    private double strength = 0.75;

    @Required

    @Desc("The interpolator to use for smoothing the strength")
    private InterpolationMethod interpolator = InterpolationMethod.BILINEAR_STARCAST_9;

    @Required

    @Desc("If set, this will shift the terrain height in blocks (up or down)")
    private double shiftHeight = 0;

    @Required

    @Desc("If set, this will force the terrain closer to the specified height.")
    private double convergeToHeight = -1;

    @Required

    @Desc("Multiplies the input noise")
    private double multiplyHeight = 1;

    @Required

    @Desc("Invert the zone so that anything outside this zone is affected.")
    private boolean invertZone = false;

    @Required

    @Desc("Add additional noise to this spot")
    private IrisGeneratorStyle addNoise = NoiseStyle.FLAT.style();


    private transient AtomicCache<Double> actualRadius = new AtomicCache<>();

    public double getActualRadius() {
        return actualRadius.aquire(() -> IrisInterpolation.getRealRadius(getInterpolator(), getInterpolationRadius()));
    }

    public static IrisFeature read(DataInputStream s) throws IOException {
        return new Gson().fromJson(s.readUTF(), IrisFeature.class);
    }

    public void write(DataOutputStream s) throws IOException {
        s.writeUTF(new Gson().toJson(this));
    }

    public int getRealSize() {
        return (int) Math.ceil((getActualRadius() + blockRadius) * 2);
    }
}
