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
package art.arcane.iris.builtin.terrain;


/**
 * What a biome declares about the layers of its ground: the layers of its surface, of its sea floor and of its cave ceilings, whether
 * they are locked to the highest height the biome reaches, and its wall. A setting the biome does not declare is null.
 */
public record LayeredBiome(LayersSettings layers, SeaLayersSettings seaLayers, CaveCeilingLayersSettings ceilingLayers,
                           LockLayersSettings lock, LockLayersMaxSettings lockMax, WallSettings wall) {
    /** A biome that declares nothing about its layers: the one layer of grass the settings default to. */
    public static final LayeredBiome UNDECLARED = new LayeredBiome(null, null, null, null, null, null);
}
