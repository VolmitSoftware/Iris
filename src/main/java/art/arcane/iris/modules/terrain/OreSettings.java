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

package art.arcane.iris.modules.terrain;

import com.volmit.config.Level;
import com.volmit.IrisFeature;
import com.volmit.config.Desc;
import com.volmit.config.Feature;

import java.util.Collections;
import java.util.List;

/**
 * The ore generators one dimension, region or biome declares, written as {@code "ores": [...]}, the list of generators.
 * Levels stack: a column's ores come from its biome first, then its region, then the dimension. IrisApi types only,
 * like {@link OreGenerator}.
 */
@Desc("Collection of ores to be generated")
@Feature(id = "iris:terrain/ores", name = "Ores", key = "ores", root = "generators", targets = {Level.DIMENSION, Level.REGION, Level.BIOME})
public final class OreSettings implements IrisFeature {
    @Desc("Collection of ores to be generated")
    private List<OreGenerator> generators = List.of();

    public OreSettings() {
    }

    public OreSettings(List<OreGenerator> generators) {
        this.generators = List.copyOf(generators);
    }

    public List<OreGenerator> generators() {
        return generators == null ? List.of() : Collections.unmodifiableList(generators);
    }
}
