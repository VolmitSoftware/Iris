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

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.object.procedural.formation.IrisFormation;
import art.arcane.iris.engine.object.material.IrisMaterialPalette;
import art.arcane.iris.engine.object.procedural.IrisProceduralBlocks;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.math.Vector3i;
import art.arcane.volmlib.util.math.RNG;

public final class FormationBlockResolver {
    private FormationBlockResolver() {
    }

    public static PlatformBlockState resolve(IrisFormation f, IrisData data, FormationCanvas.Role role, Vector3i raw) {
        int x = raw.getBlockX();
        int y = raw.getBlockY();
        int z = raw.getBlockZ();
        RNG paletteRng = new RNG(f.getSeed());

        if (role == FormationCanvas.Role.CAP && capDefined(f)) {
            PlatformBlockState cap = IrisProceduralBlocks.resolve(f.getCapBlock(), f.getCapPalette(), data, x, y, z, paletteRng);
            if (cap != null) {
                return cap;
            }
        }

        if (strataDefined(f)) {
            IrisMaterialPalette strata = f.getStrataPalette();
            int thickness = Math.max(1, f.getStrataThickness());
            int band = Math.floorDiv(y, thickness);
            PlatformBlockState strataState = strata.get(new RNG(f.getSeed() + (band * 31L)), x, band, z, data);
            if (strataState != null) {
                return strataState;
            }
        }

        return IrisProceduralBlocks.resolve(f.getBlock(), f.getBlockPalette(), data, x, y, z, paletteRng);
    }

    private static boolean capDefined(IrisFormation f) {
        return IrisProceduralBlocks.paletteSet(f.getCapPalette()) || (f.getCapBlock() != null && !f.getCapBlock().isEmpty());
    }

    private static boolean strataDefined(IrisFormation f) {
        return IrisProceduralBlocks.paletteSet(f.getStrataPalette());
    }
}
