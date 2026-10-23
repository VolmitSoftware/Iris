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

package art.arcane.iris.engine.modifier;

import art.arcane.iris.builtin.carve.CaveDepthSettings;
import art.arcane.iris.engine.object.terrain.carving.CarvingResolver;
import art.arcane.iris.engine.object.terrain.carving.DeclaredByLevel;
import art.arcane.iris.engine.object.terrain.carving.ResolvedCarvingEntry;
import com.volmit.resource.ResourceId;
import com.volmit.service.CaveBiomes;
import com.volmit.world.PackWorld;

import java.util.Optional;

/**
 * What the carve module tells the host about cave biomes ({@link CaveBiomes}): the biome of the dimension's carving band at a
 * position, and how far below the surface a biome's caves begin.
 */
public final class CaveBiomeBands implements CaveBiomes {
    @Override
    public Optional<ResourceId> overriddenBiome(PackWorld world, int x, int y, int z) {
        CarvingResolver resolver = CarvingResolver.of(world);
        ResolvedCarvingEntry rootEntry = resolver.rootEntryAt(y);
        if (rootEntry == null) {
            return Optional.empty();
        }
        ResolvedCarvingEntry entry = resolver.resolveFromRoot(rootEntry, x, z);
        if (resolver.entryBiome(entry) == null) {
            return Optional.empty();
        }
        return Optional.of(world.resources().idOf(entry.biomeKey().trim()));
    }

    @Override
    public int minDepthBelowSurface(PackWorld world, ResourceId biome) {
        return Depths.of(world).minDepthBelowSurface(biome);
    }

    /** The minimum depths the world's biomes declare ({@code caveMinDepthBelowSurface}), found by biome id. */
    private static final class Depths {
        private final DeclaredByLevel<CaveDepthSettings> declared;

        private Depths(PackWorld world) {
            this.declared = DeclaredByLevel.of(world, CaveDepthSettings.class);
        }

        static Depths of(PackWorld world) {
            return world.state(Depths.class, Depths::new);
        }

        /** How many blocks below the surface the biome's caves begin, as its {@code caveMinDepthBelowSurface} says; 0 where it says none. */
        int minDepthBelowSurface(ResourceId biome) {
            CaveDepthSettings depth = declared.ofBiome(biome);
            return depth == null ? 0 : depth.depth();
        }
    }
}
