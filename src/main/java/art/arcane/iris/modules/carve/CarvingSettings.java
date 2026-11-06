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

package art.arcane.iris.modules.carve;

import com.volmit.config.Level;
import com.volmit.IrisFeature;
import com.volmit.config.Desc;
import com.volmit.config.Feature;
import com.volmit.config.PackSettings;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.world.LinkedBiomes;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The cave biome overrides a dimension declares, written as {@code "carving": [...]}, the list of {@link CarvingEntry}s.
 * The biome of every enabled entry takes part in the pack's layout like any biome the dimension reaches.
 */
@Desc("Dimension-level cave biome carving overrides with absolute world Y ranges")
@Feature(id = "iris:carve/bands", name = "Cave biome bands", key = "carving", root = "entries", targets = Level.DIMENSION)
public final class CarvingSettings implements IrisFeature, LinkedBiomes {
    @Desc("Dimension-level cave biome carving overrides with absolute world Y ranges")
    private List<CarvingEntry> entries = List.of();

    public CarvingSettings() {
    }

    public CarvingSettings(List<CarvingEntry> entries) {
        this.entries = List.copyOf(entries);
    }

    public List<CarvingEntry> entries() {
        return entries == null ? List.of() : Collections.unmodifiableList(entries);
    }

    /**
     * The biome key {@code reference} stands for: the biome of the entry with that id, ignoring surrounding blanks, or
     * {@code reference} itself when no entry has the id. When several entries share an id the last one counts.
     */
    public String biomeOf(String reference) {
        CarvingEntry entry = null;
        for (CarvingEntry candidate : entries()) {
            if (candidate != null && !candidate.id().isBlank() && candidate.id().trim().equals(reference)) {
                entry = candidate;
            }
        }
        return entry == null ? reference : entry.biome();
    }

    @Override
    public Set<ResourceId> usedBiomes(PackResources pack, PackSettings dimension) {
        Set<ResourceId> biomes = new LinkedHashSet<>();
        for (CarvingEntry entry : entries()) {
            if (entry != null && entry.enabled() && entry.biome() != null && !entry.biome().isBlank()) {
                biomes.add(pack.idOf(entry.biome()));
            }
        }
        return biomes;
    }
}
