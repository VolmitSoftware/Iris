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

package com.volmit.iris.engine.object.procedural.fungi;

import com.volmit.iris.engine.object.procedural.ObjectBuilder;
import com.volmit.iris.engine.object.procedural.ProceduralShape;
import com.volmit.iris.engine.object.procedural.IrisProceduralBlocks;
import org.bukkit.block.data.BlockData;
import com.volmit.iris.util.math.Vector3i;
import com.volmit.iris.util.math.RNG;

import java.util.HashMap;
import java.util.Map;

public final class FungusGenerator {
    private FungusGenerator() {
    }

    public static ProceduralShape generate(Fungus fungus, int height, RNG rng, ObjectBuilder blocks) {
        Map<Vector3i, FungusCellRole> roles = new HashMap<>();
        long baseSeed = rng.getSeed();

        if (fungus.shelf()) {
            FungusShelfBuilder.build(roles, fungus, rng, baseSeed);
        } else {
            buildUpright(roles, fungus, height, rng, baseSeed);
        }

        if (roles.isEmpty()) {
            return null;
        }

        RNG paletteRng = new RNG(fungus.seed());
        Map<Vector3i, BlockData> resolved = new HashMap<>();
        for (Map.Entry<Vector3i, FungusCellRole> entry : roles.entrySet()) {
            Vector3i pos = entry.getKey();
            BlockData bd = resolveRole(fungus, entry.getValue(), blocks, pos, paletteRng);
            if (bd == null) {
                continue;
            }
            resolved.put(pos, bd);
        }

        return IrisProceduralBlocks.assemble(resolved);
    }

    private static void buildUpright(Map<Vector3i, FungusCellRole> roles, Fungus fungus, int height, RNG rng, long baseSeed) {
        int stemHeight = Math.max(1, height);
        Map<Vector3i, BlockData> stemCells = new HashMap<>();
        double[] top = FungusStemBuilder.build(stemCells, fungus, stemHeight, baseSeed);
        for (Vector3i v : stemCells.keySet()) {
            roles.put(v, FungusCellRole.STEM);
        }

        int radius = pickRadius(fungus, rng);
        double cx = top[0];
        double cz = top[2];
        int capBaseY = (int) Math.round(top[1]);
        FungusCapBuilder.build(roles, fungus, radius, cx, capBaseY, cz, baseSeed + 5557L);
    }

    private static int pickRadius(Fungus fungus, RNG rng) {
        int lo = Math.min(fungus.capRadiusMin(), fungus.capRadiusMax());
        int hi = Math.max(fungus.capRadiusMin(), fungus.capRadiusMax());
        lo = Math.max(1, lo);
        hi = Math.max(lo, hi);
        return rng.i(lo, hi + 1);
    }

    private static BlockData resolveRole(Fungus fungus, FungusCellRole role, ObjectBuilder blocks, Vector3i pos, RNG paletteRng) {
        int x = pos.getBlockX();
        int y = pos.getBlockY();
        int z = pos.getBlockZ();
        return switch (role) {
            case STEM -> IrisProceduralBlocks.resolve(fungus.stem(), fungus.stemPalette(), blocks, x, y, z, paletteRng);
            case CAP -> IrisProceduralBlocks.resolve(fungus.cap(), fungus.capPalette(), blocks, x, y, z, paletteRng);
            case GILL -> {
                BlockData gill = IrisProceduralBlocks.resolve(fungus.gillBlock(), fungus.gillPalette(), blocks, x, y, z, paletteRng);
                yield gill != null ? gill : IrisProceduralBlocks.resolve(fungus.cap(), fungus.capPalette(), blocks, x, y, z, paletteRng);
            }
            case SPOT -> {
                BlockData spot = IrisProceduralBlocks.resolve(fungus.spotBlock(), fungus.spotPalette(), blocks, x, y, z, paletteRng);
                yield spot != null ? spot : IrisProceduralBlocks.resolve(fungus.cap(), fungus.capPalette(), blocks, x, y, z, paletteRng);
            }
        };
    }
}
