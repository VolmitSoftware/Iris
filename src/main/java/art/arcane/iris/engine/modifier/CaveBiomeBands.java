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
import art.arcane.iris.core.modules.IrisPackWorld;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.terrain.carving.CarvingResolver;
import art.arcane.iris.engine.object.terrain.carving.ResolvedCarvingEntry;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import com.volmit.service.CaveBiomes;
import com.volmit.world.PackWorld;

import java.util.Optional;

/**
 * What the carve module tells the host about cave biomes ({@link CaveBiomes}): the biome of the dimension's carving band at a
 * position, and how far below the surface a biome's caves begin.
 */
public final class CaveBiomeBands implements CaveBiomes {
    @Override
    public Optional<String> overriddenBiome(PackWorld world, int x, int y, int z) {
        Engine engine = IrisPackWorld.of(world).engine();
        ResolvedCarvingEntry rootEntry = CarvingResolver.resolveRootEntry(engine, y + engine.getWorld().minHeight(), null);
        if (rootEntry == null) {
            return Optional.empty();
        }
        ResolvedCarvingEntry entry = CarvingResolver.resolveFromRoot(engine, rootEntry, x, z, null);
        if (CarvingResolver.resolveEntryBiome(engine, entry, null) == null) {
            return Optional.empty();
        }
        return Optional.of(entry.biomeKey().trim());
    }

    @Override
    public int minDepthBelowSurface(PackWorld world, String biomeKey) {
        IrisBiome biome = IrisPackWorld.of(world).engine().getData().getBiomeLoader().load(biomeKey);
        return biome == null ? 0 : minDepthBelowSurface(biome);
    }

    /** How many blocks below the surface {@code biome}'s caves begin, as its {@code caveMinDepthBelowSurface} says; 0 where it says none. */
    public static int minDepthBelowSurface(IrisBiome biome) {
        return biome.getModuleSettings().find(CaveDepthSettings.class).map(CaveDepthSettings::depth).orElse(0);
    }
}
