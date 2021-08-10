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

package com.volmit.iris.engine.mantle.components;

import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.engine.mantle.EngineMantle;
import com.volmit.iris.engine.mantle.IrisMantleComponent;
import com.volmit.iris.engine.object.biome.IrisBiome;
import com.volmit.iris.engine.object.feature.IrisFeaturePositional;
import com.volmit.iris.engine.object.feature.IrisFeaturePotential;
import com.volmit.iris.engine.object.regional.IrisRegion;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.mantle.MantleFlag;
import com.volmit.iris.util.math.RNG;

import java.util.function.Consumer;

public class MantleFeatureComponent extends IrisMantleComponent {
    public MantleFeatureComponent(EngineMantle engineMantle) {
        super(engineMantle, MantleFlag.FEATURE);
    }

    @Override
    public void generateLayer(int x, int z, Consumer<Runnable> post) {
        RNG rng = new RNG(Cache.key(x, z) + seed());
        int xxx = 8 + (x << 4);
        int zzz = 8 + (z << 4);
        IrisRegion region = getComplex().getRegionStream().get(xxx, zzz);
        IrisBiome biome = getComplex().getTrueBiomeStreamNoFeatures().get(xxx, zzz);
        generateFeatures(rng, x, z, region, biome);
    }

    @ChunkCoordinates
    private void generateFeatures(RNG rng, int cx, int cz, IrisRegion region, IrisBiome biome) {
        for (IrisFeaturePotential i : getFeatures()) {
            placeZone(rng, cx, cz, i);
        }

        for (IrisFeaturePotential i : region.getFeatures()) {
            placeZone(rng, cx, cz, i);
        }

        for (IrisFeaturePotential i : biome.getFeatures()) {
            placeZone(rng, cx, cz, i);
        }
    }

    private void placeZone(RNG rng, int cx, int cz, IrisFeaturePotential i) {
        int x = (cx << 4) + rng.nextInt(16);
        int z = (cz << 4) + rng.nextInt(16);
        getMantle().set(x, 0, z, new IrisFeaturePositional(x, z, i.getZone()));
    }

    private KList<IrisFeaturePotential> getFeatures() {
        return getEngineMantle().getEngine().getDimension().getFeatures();
    }
}
