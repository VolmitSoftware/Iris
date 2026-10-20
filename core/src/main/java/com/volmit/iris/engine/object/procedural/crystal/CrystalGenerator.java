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

package com.volmit.iris.engine.object.procedural.crystal;

import com.volmit.iris.engine.object.IrisMaterialPalette;
import com.volmit.iris.engine.object.procedural.ObjectBuilder;
import com.volmit.iris.engine.object.procedural.ProceduralShape;
import com.volmit.iris.engine.object.procedural.IrisProceduralBlocks;
import org.bukkit.block.data.BlockData;
import com.volmit.iris.util.math.Vector3i;
import com.volmit.iris.util.math.RNG;

import java.util.HashMap;
import java.util.Map;

public final class CrystalGenerator {
    private CrystalGenerator() {
    }

    public static ProceduralShape generate(Crystal crystal, int variantIndex, RNG rng, ObjectBuilder blocks) {
        CrystalCanvas canvas = new CrystalCanvas();
        long structureSeed = rng.getSeed();

        CrystalBaseBuilder.build(canvas, crystal, structureSeed + 977L);
        CrystalShardBuilder.build(canvas, crystal, rng);

        Map<Vector3i, BlockData> resolved = new HashMap<>();
        RNG paletteRng = new RNG(crystal.seed());
        RNG tipRng = new RNG(crystal.seed() + (variantIndex * 31337L) + 4099L);

        for (Map.Entry<Vector3i, CrystalRole> entry : canvas.getCells().entrySet()) {
            Vector3i position = entry.getKey();
            int x = position.getBlockX();
            int y = position.getBlockY();
            int z = position.getBlockZ();
            BlockData blockData = resolveRole(crystal, entry.getValue(), blocks, x, y, z, paletteRng, tipRng);
            if (blockData == null) {
                continue;
            }
            resolved.put(position, blockData);
        }

        return IrisProceduralBlocks.assemble(resolved);
    }

    private static BlockData resolveRole(Crystal crystal, CrystalRole role, ObjectBuilder blocks, int x, int y, int z, RNG paletteRng, RNG tipRng) {
        return switch (role) {
            case BASE -> resolveBase(crystal, blocks, x, y, z, paletteRng);
            case SHARD -> resolveShard(crystal, blocks, x, y, z, paletteRng);
            case TIP -> resolveTip(crystal, blocks, x, y, z, paletteRng, tipRng);
        };
    }

    private static BlockData resolveBase(Crystal crystal, ObjectBuilder blocks, int x, int y, int z, RNG paletteRng) {
        boolean hasBase = IrisProceduralBlocks.paletteSet(crystal.basePalette())
                || (crystal.baseBlock() != null && !crystal.baseBlock().isEmpty());
        if (hasBase) {
            BlockData base = IrisProceduralBlocks.resolve(crystal.baseBlock(), crystal.basePalette(), blocks, x, y, z, paletteRng);
            if (base != null) {
                return base;
            }
        }
        return resolveShard(crystal, blocks, x, y, z, paletteRng);
    }

    private static BlockData resolveShard(Crystal crystal, ObjectBuilder blocks, int x, int y, int z, RNG paletteRng) {
        return IrisProceduralBlocks.resolve(crystal.block(), crystal.blockPalette(), blocks, x, y, z, paletteRng);
    }

    private static BlockData resolveTip(Crystal crystal, ObjectBuilder blocks, int x, int y, int z, RNG paletteRng, RNG tipRng) {
        boolean hasTip = IrisProceduralBlocks.paletteSet(crystal.tipPalette())
                || (crystal.tipBlock() != null && !crystal.tipBlock().isEmpty());

        if (hasTip) {
            if (tipRng.chance(crystal.tipChance())) {
                BlockData tip = IrisProceduralBlocks.resolve(crystal.tipBlock(), crystal.tipPalette(), blocks, x, y, z, paletteRng);
                if (tip != null) {
                    return tip;
                }
            }
            return resolveShard(crystal, blocks, x, y, z, paletteRng);
        }

        if (crystal.glow() && crystal.glowBlock() != null && !crystal.glowBlock().isEmpty()) {
            if (tipRng.chance(crystal.tipChance())) {
                BlockData glow = IrisProceduralBlocks.resolve(crystal.glowBlock(), (IrisMaterialPalette) null, blocks, x, y, z, paletteRng);
                if (glow != null) {
                    return glow;
                }
            }
        }

        return resolveShard(crystal, blocks, x, y, z, paletteRng);
    }
}
