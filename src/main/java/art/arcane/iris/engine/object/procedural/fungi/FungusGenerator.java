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

package art.arcane.iris.engine.object.procedural.fungi;

import art.arcane.iris.builtin.objects.fungi.Fungus;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.procedural.IrisProceduralBlocks;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.math.Vector3i;
import art.arcane.volmlib.util.math.RNG;

import java.util.HashMap;
import java.util.Map;

public final class FungusGenerator {
    private FungusGenerator() {
    }

    public static IrisObject generate(Fungus fungus, int height, RNG rng, IrisData data) {
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
        Map<Vector3i, PlatformBlockState> resolved = new HashMap<>();
        for (Map.Entry<Vector3i, FungusCellRole> entry : roles.entrySet()) {
            Vector3i pos = entry.getKey();
            PlatformBlockState bd = resolveRole(fungus, entry.getValue(), data, pos, paletteRng);
            if (bd == null) {
                continue;
            }
            resolved.put(pos, bd);
        }

        return IrisProceduralBlocks.assemble(resolved);
    }

    private static void buildUpright(Map<Vector3i, FungusCellRole> roles, Fungus fungus, int height, RNG rng, long baseSeed) {
        int stemHeight = Math.max(1, height);
        Map<Vector3i, PlatformBlockState> stemCells = new HashMap<>();
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

    private static PlatformBlockState resolveRole(Fungus fungus, FungusCellRole role, IrisData data, Vector3i pos, RNG paletteRng) {
        int x = pos.getBlockX();
        int y = pos.getBlockY();
        int z = pos.getBlockZ();
        return switch (role) {
            case STEM -> IrisProceduralBlocks.resolve(fungus.stem(), fungus.stemPalette(), data, x, y, z, paletteRng);
            case CAP -> IrisProceduralBlocks.resolve(fungus.cap(), fungus.capPalette(), data, x, y, z, paletteRng);
            case GILL -> {
                PlatformBlockState gill = IrisProceduralBlocks.resolve(fungus.gillBlock(), fungus.gillPalette(), data, x, y, z, paletteRng);
                yield gill != null ? gill : IrisProceduralBlocks.resolve(fungus.cap(), fungus.capPalette(), data, x, y, z, paletteRng);
            }
            case SPOT -> {
                PlatformBlockState spot = IrisProceduralBlocks.resolve(fungus.spotBlock(), fungus.spotPalette(), data, x, y, z, paletteRng);
                yield spot != null ? spot : IrisProceduralBlocks.resolve(fungus.cap(), fungus.capPalette(), data, x, y, z, paletteRng);
            }
        };
    }
}
