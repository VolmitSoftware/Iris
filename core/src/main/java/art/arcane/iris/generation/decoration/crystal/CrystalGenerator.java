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

package art.arcane.iris.generation.decoration.crystal;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.generation.decoration.IrisProceduralBlocks;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.math.Vector3i;
import art.arcane.volmlib.util.math.RNG;

import java.util.HashMap;
import java.util.Map;

public final class CrystalGenerator {
    private CrystalGenerator() {
    }

    public static IrisObject generate(IrisCrystal crystal, int variantIndex, RNG rng, IrisData data) {
        CrystalCanvas canvas = new CrystalCanvas();
        long structureSeed = rng.getSeed();

        CrystalBaseBuilder.build(canvas, crystal, structureSeed + 977L);
        CrystalShardBuilder.build(canvas, crystal, rng);

        Map<Vector3i, NativeBlockState> resolved = new HashMap<>();
        RNG paletteRng = new RNG(crystal.getSeed());
        RNG tipRng = new RNG(crystal.getSeed() + (variantIndex * 31337L) + 4099L);

        for (Map.Entry<Vector3i, CrystalRole> entry : canvas.getCells().entrySet()) {
            Vector3i position = entry.getKey();
            int x = position.getBlockX();
            int y = position.getBlockY();
            int z = position.getBlockZ();
            NativeBlockState blockData = resolveRole(crystal, entry.getValue(), data, x, y, z, paletteRng, tipRng);
            if (blockData == null) {
                continue;
            }
            resolved.put(position, blockData);
        }

        return IrisProceduralBlocks.assemble(resolved);
    }

    private static NativeBlockState resolveRole(IrisCrystal crystal, CrystalRole role, IrisData data, int x, int y, int z, RNG paletteRng, RNG tipRng) {
        return switch (role) {
            case BASE -> resolveBase(crystal, data, x, y, z, paletteRng);
            case SHARD -> resolveShard(crystal, data, x, y, z, paletteRng);
            case TIP -> resolveTip(crystal, data, x, y, z, paletteRng, tipRng);
        };
    }

    private static NativeBlockState resolveBase(IrisCrystal crystal, IrisData data, int x, int y, int z, RNG paletteRng) {
        boolean hasBase = IrisProceduralBlocks.paletteSet(crystal.getBasePalette())
                || (crystal.getBaseBlock() != null && !crystal.getBaseBlock().isEmpty());
        if (hasBase) {
            NativeBlockState base = IrisProceduralBlocks.resolve(crystal.getBaseBlock(), crystal.getBasePalette(), data, x, y, z, paletteRng);
            if (base != null) {
                return base;
            }
        }
        return resolveShard(crystal, data, x, y, z, paletteRng);
    }

    private static NativeBlockState resolveShard(IrisCrystal crystal, IrisData data, int x, int y, int z, RNG paletteRng) {
        return IrisProceduralBlocks.resolve(crystal.getBlock(), crystal.getBlockPalette(), data, x, y, z, paletteRng);
    }

    private static NativeBlockState resolveTip(IrisCrystal crystal, IrisData data, int x, int y, int z, RNG paletteRng, RNG tipRng) {
        boolean hasTip = IrisProceduralBlocks.paletteSet(crystal.getTipPalette())
                || (crystal.getTipBlock() != null && !crystal.getTipBlock().isEmpty());

        if (hasTip) {
            if (tipRng.chance(crystal.getTipChance())) {
                NativeBlockState tip = IrisProceduralBlocks.resolve(crystal.getTipBlock(), crystal.getTipPalette(), data, x, y, z, paletteRng);
                if (tip != null) {
                    return tip;
                }
            }
            return resolveShard(crystal, data, x, y, z, paletteRng);
        }

        if (crystal.isGlow() && crystal.getGlowBlock() != null && !crystal.getGlowBlock().isEmpty()) {
            if (tipRng.chance(crystal.getTipChance())) {
                NativeBlockState glow = IrisProceduralBlocks.resolve(crystal.getGlowBlock(), null, data, x, y, z, paletteRng);
                if (glow != null) {
                    return glow;
                }
            }
        }

        return resolveShard(crystal, data, x, y, z, paletteRng);
    }
}
