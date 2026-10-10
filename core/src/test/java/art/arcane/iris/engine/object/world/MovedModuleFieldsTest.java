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

package art.arcane.iris.engine.object.world;

import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertThrows;

/** The carving, floating and ore settings are fields of modules now, not of Iris's pack classes: they stay off them. */
public class MovedModuleFieldsTest {
    @Test
    public void theCaveFloatingAndOreSettingsAreNoFieldsOfTheBiome() {
        for (String field : List.of("caveProfile", "carvingBiome", "caveMinDepthBelowSurface", "floatingChildBiomes", "mergeFloatingChildBiomes",
                "oreDepositFrequencyMultiplier", "oreDepositSizeMultiplier", "depositVariants", "ores")) {
            assertThrows(field, NoSuchFieldException.class, () -> IrisBiome.class.getDeclaredField(field));
        }
    }

    @Test
    public void theCaveAndOreSettingsAreNoFieldsOfTheRegion() {
        for (String field : List.of("caveProfile", "depositVariants", "ores")) {
            assertThrows(field, NoSuchFieldException.class, () -> IrisRegion.class.getDeclaredField(field));
        }
    }

    @Test
    public void theCarvingAndOreSettingsAreNoFieldsOfTheDimension() {
        for (String field : List.of("caveProfile", "carving", "carvingEnabled", "depositVariants", "ores", "hideOresForHiddenOre")) {
            assertThrows(field, NoSuchFieldException.class, () -> IrisDimension.class.getDeclaredField(field));
        }
    }
}
