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

package art.arcane.iris.engine.object.structure.vanilla;

import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.MaxNumber;
import art.arcane.iris.engine.object.annotations.MinNumber;
import art.arcane.iris.engine.object.annotations.RegistryListVanillaStructureSet;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("An exact registered structure-set frequency multiplier. Iris retains the set's structures, weights, biome eligibility, placement algorithm, salt, exclusion zone, starts, processors, mobs, loot, and locate behavior while scaling only its native placement density.")
@Data
public class IrisStructureSetFrequencyOverride {
    @RegistryListVanillaStructureSet
    @Desc("Exact registered structure-set key, for example 'minecraft:nether_complexes'. Structure keys such as 'minecraft:fortress' are not valid here.")
    private String structureSet = "";

    @MinNumber(0.01)
    @MaxNumber(16)
    @Desc("Requested placement-density multiplier. Random-spread sets scale their placement frequency first and then their integer chunk spacing; integer spacing and separation constraints can make the realized increase slightly lower or higher. The default 1 leaves the registered placement unchanged.")
    private double multiplier = 1D;
}
