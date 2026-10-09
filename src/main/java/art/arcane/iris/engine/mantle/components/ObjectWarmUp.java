/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.iris.engine.object.procedural.IrisProceduralObjects;
import art.arcane.iris.engine.object.procedural.IrisProceduralPlacement;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;

import java.util.Comparator;

/**
 * Builds what object placing would build on first use, for every object placement and procedural object the world's
 * biomes and regions declare: the placement's surface warp and density noise, and a procedural object's variants. None
 * of it depends on the random it is warmed with; the warp is seeded from the world, the density noise is cached per seed
 * and the variants are baked whatever picks one.
 */
final class ObjectWarmUp {
    private static final long ROOT_SEED_SALT = 7777L;
    private static final int PROBE_COORDINATE = 0;

    private final Engine engine;
    private final RNG root;
    private int warmed;

    ObjectWarmUp(Engine engine) {
        this.engine = engine;
        this.root = new RNG(engine.getSeedManager().getComponent() + ROOT_SEED_SALT);
    }

    void warm() {
        KList<IrisBiome> biomes = engine.getAllBiomes();
        biomes.sort(Comparator.comparing(IrisBiome::getLoadKey));
        for (IrisBiome biome : biomes) {
            warmPlacements(biome.getObjects());
            warmProcedural(biome.getProceduralObjects());
        }

        KList<IrisRegion> regions = engine.getDimension().getAllRegions(engine);
        regions.sort(Comparator.comparing(IrisRegion::getLoadKey));
        for (IrisRegion region : regions) {
            warmPlacements(region.getObjects());
            warmProcedural(region.getProceduralObjects());
        }
    }

    private void warmPlacements(KList<IrisObjectPlacement> placements) {
        if (placements == null) {
            return;
        }
        for (IrisObjectPlacement placement : placements) {
            if (placement == null) {
                continue;
            }
            RNG rng = root.nextParallelRNG(warmed++);
            placement.getSurfaceWarp(rng, engine.getData(), engine);
            placement.getDensity(rng, PROBE_COORDINATE, PROBE_COORDINATE, engine.getData());
        }
    }

    private void warmProcedural(IrisProceduralObjects procedural) {
        if (procedural == null) {
            return;
        }
        for (IrisProceduralPlacement placement : procedural.getAllPlacements()) {
            if (placement != null) {
                placement.getVariantObject(engine.getData(), root.nextParallelRNG(warmed++));
            }
        }
    }
}
