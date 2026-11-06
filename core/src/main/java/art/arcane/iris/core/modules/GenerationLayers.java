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

package art.arcane.iris.core.modules;

import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterSlice;
import com.volmit.host.MantleRegistration;
import com.volmit.mantle.MantleLifetime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The module mantle layers a pack registers with {@link MantleLifetime#GENERATION}: they only serve the chunks
 * generating around them, so Iris removes them when it cleans a generated mantle chunk, as it deletes its own
 * transient slices. Persistent layers, and layers no loaded module registers, stay.
 */
public final class GenerationLayers {
    /** No generation layers: cleanup leaves module layers alone. */
    public static final GenerationLayers NONE = new GenerationLayers(Set.of());

    private static final int SECTION_SHIFT = 4;
    private static final int LOCAL_MASK = 15;

    private final Set<String> layerIds;

    private GenerationLayers(Set<String> layerIds) {
        this.layerIds = layerIds;
    }

    public static GenerationLayers of(Collection<MantleRegistration<?>> registrations) {
        Set<String> generation = registrations.stream()
                .filter(registration -> registration.lifetime() == MantleLifetime.GENERATION)
                .map(registration -> registration.key().id().toString())
                .collect(Collectors.toUnmodifiableSet());
        return generation.isEmpty() ? NONE : new GenerationLayers(generation);
    }

    /** Removes the generation layers' values from every block of {@code chunk}. */
    public void removeFrom(MantleChunk<Matter> chunk) {
        if (layerIds.isEmpty()) {
            return;
        }
        synchronized (chunk) {
            for (Kept kept : keptValues(chunk)) {
                MatterSlice<ModuleLayerValues> slice = chunk.getOrCreate(kept.mantleY() >> SECTION_SHIFT).slice(ModuleLayerValues.class);
                slice.set(kept.x(), kept.mantleY() & LOCAL_MASK, kept.z(), kept.values().isEmpty() ? null : kept.values());
            }
        }
    }

    /** The blocks whose values change, with what they keep; collected first so the slices are not changed while iterated. */
    private List<Kept> keptValues(MantleChunk<Matter> chunk) {
        List<Kept> kept = new ArrayList<>();
        chunk.iterate(ModuleLayerValues.class, (x, mantleY, z, values) -> {
            ModuleLayerValues remaining = values.withoutAll(layerIds);
            if (remaining != values) {
                kept.add(new Kept(x, mantleY, z, remaining));
            }
        });
        return kept;
    }

    private record Kept(int x, int mantleY, int z, ModuleLayerValues values) {
    }
}
