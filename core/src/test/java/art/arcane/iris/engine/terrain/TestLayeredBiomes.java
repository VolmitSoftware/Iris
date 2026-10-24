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

package art.arcane.iris.engine.terrain;

import art.arcane.iris.modules.terrain.LayeredBiome;
import art.arcane.iris.modules.terrain.CaveCeilingLayersSettings;
import art.arcane.iris.builtin.terrain.LayersSettings;
import art.arcane.iris.modules.terrain.LockLayersMaxSettings;
import art.arcane.iris.modules.terrain.LockLayersSettings;
import art.arcane.iris.modules.terrain.SeaLayersSettings;
import art.arcane.iris.modules.terrain.WallSettings;
import art.arcane.iris.core.modules.ModuleSettings;

/** What a biome file declares about its layers, for tests that write biomes as pack JSON ({@link TerrainTestSettings}). */
public final class TestLayeredBiomes {
    private TestLayeredBiomes() {
    }

    public static LayeredBiome of(ModuleSettings settings) {
        return new LayeredBiome(
                settings.find(LayersSettings.class).orElse(null),
                settings.find(SeaLayersSettings.class).orElse(null),
                settings.find(CaveCeilingLayersSettings.class).orElse(null),
                settings.find(LockLayersSettings.class).orElse(null),
                settings.find(LockLayersMaxSettings.class).orElse(null),
                settings.find(WallSettings.class).orElse(null));
    }
}
