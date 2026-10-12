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

import art.arcane.iris.engine.object.placement.ObjectPlacements;
import art.arcane.iris.core.modules.IrisPackWorld;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import com.volmit.service.ObjectsAt;
import com.volmit.world.PackWorld;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * How {@code iris:objects} tells Iris where its objects are: the objects the biome and region of a chunk would place,
 * and the biomes and regions whose surface placements name an object.
 */
public final class ObjectLocating implements ObjectsAt {
    @Override
    public Set<String> objectsAt(PackWorld world, int chunkX, int chunkZ) {
        return ObjectModule.placing(engine(world), world.log()).guess(chunkX, chunkZ);
    }

    @Override
    public PlacedBy placedBy(PackWorld world, String objectKey) {
        Engine engine = engine(world);
        Set<String> biomes = new LinkedHashSet<>();
        for (IrisBiome biome : engine.getDimension().getReachableBiomes(engine)) {
            if (biome != null && placesObject(ObjectPlacements.surface(biome), objectKey)) {
                biomes.add(biome.getLoadKey());
            }
        }

        Set<String> regions = new LinkedHashSet<>();
        for (IrisRegion region : engine.getDimension().getAllRegions(engine)) {
            if (region != null && placesObject(ObjectPlacements.surface(region), objectKey)) {
                regions.add(region.getLoadKey());
            }
        }
        return new PlacedBy(biomes, regions);
    }

    private static boolean placesObject(List<IrisObjectPlacement> placements, String objectKey) {
        for (IrisObjectPlacement placement : placements) {
            if (placement != null && placement.getPlace().contains(objectKey)) {
                return true;
            }
        }
        return false;
    }

    private static Engine engine(PackWorld world) {
        return IrisPackWorld.of(world).engine();
    }
}
