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

package art.arcane.iris.structure.placement;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.RegistryListVanillaStructureSet;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("An exact registered structure-set frequency multiplier. Iris retains the set's structures, weights, biome eligibility, placement algorithm, salt, exclusion zone, starts, processors, mobs, loot, and locate behavior while scaling only its native placement density.")
@Data
public class IrisStructureSetFrequencyOverride {
    @RegistryListVanillaStructureSet
    @Description("Exact registered structure-set key, for example 'minecraft:nether_complexes'. Structure keys such as 'minecraft:fortress' are not valid here.")
    private String structureSet = "";

    @MinNumber(0.01)
    @MaxNumber(16)
    @Description("Requested placement-density multiplier. Random-spread sets scale their placement frequency first and then their integer chunk spacing; integer spacing and separation constraints can make the realized increase slightly lower or higher. The default 1 leaves the registered placement unchanged.")
    private double multiplier = 1D;
}
