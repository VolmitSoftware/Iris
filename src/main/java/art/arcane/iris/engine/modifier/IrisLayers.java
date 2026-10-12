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
package art.arcane.iris.engine.modifier;

import art.arcane.iris.builtin.terrain.CaveCeilingLayersSettings;
import art.arcane.iris.builtin.terrain.ExplodeBiomePaletteSizeSettings;
import art.arcane.iris.builtin.terrain.ExplodeBiomePalettesSettings;
import art.arcane.iris.builtin.terrain.LayersSettings;
import art.arcane.iris.builtin.terrain.LockLayersMaxSettings;
import art.arcane.iris.builtin.terrain.LockLayersSettings;
import art.arcane.iris.builtin.terrain.SeaLayersSettings;
import art.arcane.iris.builtin.terrain.WallSettings;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.builtin.terrain.ColumnSlope;
import art.arcane.iris.builtin.terrain.LayeredBiome;
import art.arcane.iris.builtin.terrain.PaletteExplosion;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import art.arcane.volmlib.util.collection.KList;
import com.volmit.block.BlockState;

/** What Iris's carving and floating islands, which hold Iris's own types, hand the terrain's layers and palettes, and take back as the platform's blocks. */
final class IrisLayers {
    private IrisLayers() {
    }

    /** What the biome declares about its layers. */
    static LayeredBiome biome(IrisBiome biome) {
        return new LayeredBiome(
                biome.getModuleSettings().find(LayersSettings.class).orElse(null),
                biome.getModuleSettings().find(SeaLayersSettings.class).orElse(null),
                biome.getModuleSettings().find(CaveCeilingLayersSettings.class).orElse(null),
                biome.getModuleSettings().find(LockLayersSettings.class).orElse(null),
                biome.getModuleSettings().find(LockLayersMaxSettings.class).orElse(null),
                biome.getModuleSettings().find(WallSettings.class).orElse(null));
    }

    /** The biome with only its wall, for a caller that asks for nothing else. */
    static LayeredBiome wallOf(IrisBiome biome) {
        return new LayeredBiome(null, null, null, null, null, biome.getModuleSettings().find(WallSettings.class).orElse(null));
    }

    static PaletteExplosion explosion(IrisDimension dimension) {
        if (dimension == null) {
            return PaletteExplosion.NONE;
        }
        return new PaletteExplosion(
                dimension.getModuleSettings().find(ExplodeBiomePalettesSettings.class).map(ExplodeBiomePalettesSettings::explode).orElse(false),
                dimension.getModuleSettings().find(ExplodeBiomePaletteSizeSettings.class).map(ExplodeBiomePaletteSizeSettings::size)
                        .orElse(ExplodeBiomePaletteSizeSettings.DEFAULT_SIZE));
    }

    static ColumnSlope slope(Engine engine) {
        return (x, z) -> engine.getComplex().getSlopeStream().get(x, z);
    }

    static PlatformBlockState platform(BlockState block) {
        return block == null ? null : B.getState(block.key());
    }

    static KList<PlatformBlockState> platform(KList<BlockState> blocks) {
        KList<PlatformBlockState> converted = new KList<>(blocks.size());
        for (BlockState block : blocks) {
            converted.add(platform(block));
        }
        return converted;
    }
}
