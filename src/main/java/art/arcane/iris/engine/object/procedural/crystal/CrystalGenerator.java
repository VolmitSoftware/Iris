/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.engine.object.procedural.crystal;

import art.arcane.iris.builtin.objects.crystal.Crystal;
import com.volmit.block.PaletteSpec;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.procedural.IrisProceduralBlocks;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.math.Vector3i;
import art.arcane.volmlib.util.math.RNG;

import java.util.HashMap;
import java.util.Map;

public final class CrystalGenerator {
    private CrystalGenerator() {
    }

    public static IrisObject generate(Crystal crystal, int variantIndex, RNG rng, IrisData data) {
        CrystalCanvas canvas = new CrystalCanvas();
        long structureSeed = rng.getSeed();

        CrystalBaseBuilder.build(canvas, crystal, structureSeed + 977L);
        CrystalShardBuilder.build(canvas, crystal, rng);

        Map<Vector3i, PlatformBlockState> resolved = new HashMap<>();
        RNG paletteRng = new RNG(crystal.seed());
        RNG tipRng = new RNG(crystal.seed() + (variantIndex * 31337L) + 4099L);

        for (Map.Entry<Vector3i, CrystalRole> entry : canvas.getCells().entrySet()) {
            Vector3i position = entry.getKey();
            int x = position.getBlockX();
            int y = position.getBlockY();
            int z = position.getBlockZ();
            PlatformBlockState blockData = resolveRole(crystal, entry.getValue(), data, x, y, z, paletteRng, tipRng);
            if (blockData == null) {
                continue;
            }
            resolved.put(position, blockData);
        }

        return IrisProceduralBlocks.assemble(resolved);
    }

    private static PlatformBlockState resolveRole(Crystal crystal, CrystalRole role, IrisData data, int x, int y, int z, RNG paletteRng, RNG tipRng) {
        return switch (role) {
            case BASE -> resolveBase(crystal, data, x, y, z, paletteRng);
            case SHARD -> resolveShard(crystal, data, x, y, z, paletteRng);
            case TIP -> resolveTip(crystal, data, x, y, z, paletteRng, tipRng);
        };
    }

    private static PlatformBlockState resolveBase(Crystal crystal, IrisData data, int x, int y, int z, RNG paletteRng) {
        boolean hasBase = IrisProceduralBlocks.paletteSet(crystal.basePalette())
                || (crystal.baseBlock() != null && !crystal.baseBlock().isEmpty());
        if (hasBase) {
            PlatformBlockState base = IrisProceduralBlocks.resolve(crystal.baseBlock(), crystal.basePalette(), data, x, y, z, paletteRng);
            if (base != null) {
                return base;
            }
        }
        return resolveShard(crystal, data, x, y, z, paletteRng);
    }

    private static PlatformBlockState resolveShard(Crystal crystal, IrisData data, int x, int y, int z, RNG paletteRng) {
        return IrisProceduralBlocks.resolve(crystal.block(), crystal.blockPalette(), data, x, y, z, paletteRng);
    }

    private static PlatformBlockState resolveTip(Crystal crystal, IrisData data, int x, int y, int z, RNG paletteRng, RNG tipRng) {
        boolean hasTip = IrisProceduralBlocks.paletteSet(crystal.tipPalette())
                || (crystal.tipBlock() != null && !crystal.tipBlock().isEmpty());

        if (hasTip) {
            if (tipRng.chance(crystal.tipChance())) {
                PlatformBlockState tip = IrisProceduralBlocks.resolve(crystal.tipBlock(), crystal.tipPalette(), data, x, y, z, paletteRng);
                if (tip != null) {
                    return tip;
                }
            }
            return resolveShard(crystal, data, x, y, z, paletteRng);
        }

        if (crystal.glow() && crystal.glowBlock() != null && !crystal.glowBlock().isEmpty()) {
            if (tipRng.chance(crystal.tipChance())) {
                PlatformBlockState glow = IrisProceduralBlocks.resolve(crystal.glowBlock(), (PaletteSpec) null, data, x, y, z, paletteRng);
                if (glow != null) {
                    return glow;
                }
            }
        }

        return resolveShard(crystal, data, x, y, z, paletteRng);
    }
}
