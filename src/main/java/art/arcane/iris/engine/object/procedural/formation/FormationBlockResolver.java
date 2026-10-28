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

package art.arcane.iris.engine.object.procedural.formation;

import com.volmit.block.PaletteSpec;
import java.util.Optional;
import art.arcane.iris.modules.objects.formation.Formation;
import art.arcane.iris.modules.objects.ObjectBuilder;
import art.arcane.iris.engine.object.procedural.IrisProceduralBlocks;
import com.volmit.block.BlockState;
import art.arcane.volmlib.util.math.Vector3i;
import art.arcane.volmlib.util.math.RNG;

public final class FormationBlockResolver {
    private FormationBlockResolver() {
    }

    public static BlockState resolve(Formation f, ObjectBuilder blocks, FormationCanvas.Role role, Vector3i raw) {
        int x = raw.getBlockX();
        int y = raw.getBlockY();
        int z = raw.getBlockZ();
        RNG paletteRng = new RNG(f.seed());

        if (role == FormationCanvas.Role.CAP && capDefined(f)) {
            BlockState cap = IrisProceduralBlocks.resolve(f.capBlock(), f.capPalette(), blocks, x, y, z, paletteRng);
            if (cap != null) {
                return cap;
            }
        }

        if (strataDefined(f)) {
            PaletteSpec strata = f.strataPalette().orElseThrow();
            int thickness = Math.max(1, f.strataThickness());
            int band = Math.floorDiv(y, thickness);
            BlockState strataState = blocks.pick(strata, f.seed() + (band * 31L), x, band, z);
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
