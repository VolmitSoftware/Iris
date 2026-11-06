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

import art.arcane.iris.modules.objects.Declaration;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.ObjectSettings;
import com.volmit.config.Level;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.service.ObjectsAt;
import com.volmit.world.PackWorld;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * How {@code iris:objects} tells Iris where its objects are: the objects the biome and region of a chunk would place,
 * and the biomes and regions whose surface placements name an object.
 */
public final class ObjectLocating implements ObjectsAt {
    @Override
    public Set<ResourceId> objectsAt(PackWorld world, int chunkX, int chunkZ) {
        return ObjectPlacing.of(world).guess(chunkX, chunkZ).stream()
                .map(world.resources()::idOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public PlacedBy placedBy(PackWorld world, ResourceId object) {
        PackResources resources = world.resources();
        Set<ResourceId> biomes = new LinkedHashSet<>();
        Set<ResourceId> regions = new LinkedHashSet<>();
        for (Declaration<ObjectSettings> declaration : Declaration.allOf(world, ObjectSettings.class)) {
            if (!placesObject(declaration.settings().objects(), resources, object)) {
                continue;
            }
            (declaration.level() == Level.BIOME ? biomes : regions).add(declaration.id());
        }
        return new PlacedBy(biomes, regions);
    }

    private static boolean placesObject(List<ObjectPlacement> placements, PackResources resources, ResourceId object) {
        for (ObjectPlacement placement : placements) {
            if (placement != null && placement.carvingSupport().supportsSurface() && names(placement, resources, object)) {
                return true;
            }
        }
        return false;
    }

    private static boolean names(ObjectPlacement placement, PackResources resources, ResourceId object) {
        return placement.place().stream().anyMatch(key -> key != null && resources.idOf(key).equals(object));
    }
}
