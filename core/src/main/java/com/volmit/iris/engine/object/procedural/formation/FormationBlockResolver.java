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

package com.volmit.iris.engine.object.procedural.formation;

import com.volmit.iris.engine.object.IrisMaterialPalette;
import com.volmit.iris.engine.object.procedural.ObjectBuilder;
import com.volmit.iris.engine.object.procedural.IrisProceduralBlocks;
import org.bukkit.block.data.BlockData;
import com.volmit.iris.util.math.Vector3i;
import com.volmit.iris.util.math.RNG;

public final class FormationBlockResolver {
    private FormationBlockResolver() {
    }

    public static BlockData resolve(Formation f, ObjectBuilder blocks, FormationCanvas.Role role, Vector3i raw) {
        int x = raw.getBlockX();
        int y = raw.getBlockY();
        int z = raw.getBlockZ();
        RNG paletteRng = new RNG(f.seed());

        if (role == FormationCanvas.Role.CAP && capDefined(f)) {
            BlockData cap = IrisProceduralBlocks.resolve(f.capBlock(), f.capPalette(), blocks, x, y, z, paletteRng);
            if (cap != null) {
                return cap;
            }
        }

        if (strataDefined(f)) {
            IrisMaterialPalette strata = f.strataPalette().orElseThrow();
            int thickness = Math.max(1, f.strataThickness());
            int band = Math.floorDiv(y, thickness);
            BlockData strataState = blocks.pick(strata, f.seed() + (band * 31L), x, band, z);
            if (strataState != null) {
                return strataState;
            }
        }

        return IrisProceduralBlocks.resolve(f.block(), f.blockPalette(), blocks, x, y, z, paletteRng);
    }

    private static boolean capDefined(Formation f) {
        return IrisProceduralBlocks.paletteSet(f.capPalette()) || f.capBlock().filter(block -> !block.isEmpty()).isPresent();
    }

    private static boolean strataDefined(Formation f) {
        return IrisProceduralBlocks.paletteSet(f.strataPalette());
    }
}
