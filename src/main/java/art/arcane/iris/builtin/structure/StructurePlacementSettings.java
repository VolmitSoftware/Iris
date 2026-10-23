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

package art.arcane.iris.builtin.structure;

import com.volmit.config.Level;
import com.volmit.IrisFeature;
import com.volmit.config.Desc;
import com.volmit.config.Feature;
import com.volmit.config.MinItems;

import java.util.Collections;
import java.util.List;

/**
 * The structure placements of a dimension, region or biome, written as {@code "structures": [...]}: one
 * {@link StructurePlacement} per entry. IrisApi types only.
 */
@Desc("Iris structure placements of this dimension, region or biome: where and how often its structures start.")
@Feature(id = "iris:structure/placements", name = "Structure placements", key = "structures", root = "placements",
        targets = {Level.DIMENSION, Level.REGION, Level.BIOME})
public final class StructurePlacementSettings implements IrisFeature {
    @MinItems(1)
    @Desc("Iris structure placements, each independent of the others.")
    private List<StructurePlacement> placements = List.of();

    public StructurePlacementSettings() {
    }

    public List<StructurePlacement> placements() {
        return placements == null ? List.of() : Collections.unmodifiableList(placements);
    }
}
