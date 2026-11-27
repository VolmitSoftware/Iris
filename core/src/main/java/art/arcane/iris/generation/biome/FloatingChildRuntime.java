/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.generation.biome;

import art.arcane.iris.generation.cave.IrisCaveProfileSampler;
import art.arcane.iris.generation.runtime.PackRuntime;
import art.arcane.iris.generation.runtime.SeedTable;

import java.util.Optional;

/**
 * What one engine derives from an {@link IrisFloatingChildBiomes} entry: its edge taper profile per base seed and the
 * cave profile sampler of the biome it carves with.
 */
public final class FloatingChildRuntime {
    private static final int EDGE_PROFILE_SEED_CAPACITY = 8;

    private final IrisFloatingChildBiomes entry;
    private final PackRuntime runtime;
    private final SeedTable<FloatingIslandEdgeProfile> edgeProfiles;
    private volatile Optional<IrisCaveProfileSampler> carvingProfileSampler;

    public FloatingChildRuntime(IrisFloatingChildBiomes entry, PackRuntime runtime) {
        this.entry = entry;
        this.runtime = runtime;
        this.edgeProfiles = new SeedTable<>(EDGE_PROFILE_SEED_CAPACITY, seed -> entry.createEdgeTaperProfile(seed, runtime));
    }

    public FloatingIslandEdgeProfile edgeTaperProfile(long baseSeed) {
        return edgeProfiles.get(baseSeed);
    }

    public IrisCaveProfileSampler carvingProfileSampler() {
        Optional<IrisCaveProfileSampler> resolved = carvingProfileSampler;
        if (resolved == null) {
            resolved = Optional.ofNullable(entry.createCarvingProfileSampler(runtime));
            carvingProfileSampler = resolved;
        }
        return resolved.orElse(null);
    }
}
