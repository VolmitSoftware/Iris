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

package art.arcane.iris.engine.object.fungi;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.object.IrisFungus;
import art.arcane.iris.engine.object.IrisObject;
import art.arcane.iris.engine.object.IrisProceduralBlocks;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.math.Vector3i;
import art.arcane.volmlib.util.math.RNG;

import java.util.HashMap;
import java.util.Map;

public final class FungusGenerator {
    private FungusGenerator() {
    }

    public static IrisObject generate(IrisFungus fungus, int height, RNG rng, IrisData data) {
        Map<Vector3i, FungusCellRole> roles = new HashMap<>();
        long baseSeed = rng.getSeed();

        if (fungus.isShelf()) {
            FungusShelfBuilder.build(roles, fungus, rng, baseSeed);
        } else {
            buildUpright(roles, fungus, height, rng, baseSeed);
        }

        if (roles.isEmpty()) {
            return null;
        }

        RNG paletteRng = new RNG(fungus.getSeed());
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

    private static void buildUpright(Map<Vector3i, FungusCellRole> roles, IrisFungus fungus, int height, RNG rng, long baseSeed) {
        int stemHeight = Math.max(1, height);
        double[] top = FungusStemBuilder.build(roles, fungus, stemHeight, baseSeed);

        int radius = pickRadius(fungus, rng);
        double cx = top[0];
        double cz = top[2];
        int capBaseY = (int) Math.round(top[1]);
        FungusCapBuilder.build(roles, fungus, radius, cx, capBaseY, cz, baseSeed + 5557L);
    }

    private static int pickRadius(IrisFungus fungus, RNG rng) {
        int lo = Math.min(fungus.getCapRadiusMin(), fungus.getCapRadiusMax());
        int hi = Math.max(fungus.getCapRadiusMin(), fungus.getCapRadiusMax());
        lo = Math.max(1, lo);
        hi = Math.max(lo, hi);
        return rng.i(lo, hi + 1);
    }

    private static PlatformBlockState resolveRole(IrisFungus fungus, FungusCellRole role, IrisData data, Vector3i pos, RNG paletteRng) {
        int x = pos.getBlockX();
        int y = pos.getBlockY();
        int z = pos.getBlockZ();
        return switch (role) {
            case STEM -> IrisProceduralBlocks.resolve(fungus.getStem(), fungus.getStemPalette(), data, x, y, z, paletteRng);
            case CAP -> IrisProceduralBlocks.resolve(fungus.getCap(), fungus.getCapPalette(), data, x, y, z, paletteRng);
            case GILL -> {
                PlatformBlockState gill = IrisProceduralBlocks.resolve(fungus.getGillBlock(), fungus.getGillPalette(), data, x, y, z, paletteRng);
                yield gill != null ? gill : IrisProceduralBlocks.resolve(fungus.getCap(), fungus.getCapPalette(), data, x, y, z, paletteRng);
            }
            case SPOT -> {
                PlatformBlockState spot = IrisProceduralBlocks.resolve(fungus.getSpotBlock(), fungus.getSpotPalette(), data, x, y, z, paletteRng);
                yield spot != null ? spot : IrisProceduralBlocks.resolve(fungus.getCap(), fungus.getCapPalette(), data, x, y, z, paletteRng);
            }
        };
    }
}
