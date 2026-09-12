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

package art.arcane.iris.generation.decoration.formation;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.iris.generation.decoration.IrisProceduralBlocks;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.math.Vector3i;
import art.arcane.volmlib.util.math.RNG;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public final class FormationBlockResolver {
    private final IrisFormation formation;
    private final IrisData data;
    private final RNG paletteRng;
    private final Int2ObjectOpenHashMap<RNG> strataRngs = new Int2ObjectOpenHashMap<>();

    public FormationBlockResolver(IrisFormation formation, IrisData data) {
        this.formation = formation;
        this.data = data;
        paletteRng = new RNG(formation.getSeed());
    }

    public PlatformBlockState resolve(FormationCanvas.Role role, Vector3i raw) {
        int x = raw.getBlockX();
        int y = raw.getBlockY();
        int z = raw.getBlockZ();

        if (role == FormationCanvas.Role.CAP && capDefined(formation)) {
            PlatformBlockState cap = IrisProceduralBlocks.resolve(formation.getCapBlock(), formation.getCapPalette(), data, x, y, z, paletteRng);
            if (cap != null) {
                return cap;
            }
        }

        if (strataDefined(formation)) {
            IrisMaterialPalette strata = formation.getStrataPalette();
            int thickness = Math.max(1, formation.getStrataThickness());
            int band = Math.floorDiv(y, thickness);
            RNG strataRng = strataRngs.get(band);
            if (strataRng == null) {
                strataRng = new RNG(formation.getSeed() + band * 31L);
                strataRngs.put(band, strataRng);
            }
            PlatformBlockState strataState = strata.get(strataRng, x, band, z, data);
            if (strataState != null) {
                return strataState;
            }
        }

        return IrisProceduralBlocks.resolve(formation.getBlock(), formation.getBlockPalette(), data, x, y, z, paletteRng);
    }

    private static boolean capDefined(IrisFormation f) {
        return IrisProceduralBlocks.paletteSet(f.getCapPalette()) || (f.getCapBlock() != null && !f.getCapBlock().isEmpty());
    }

    private static boolean strataDefined(IrisFormation f) {
        return IrisProceduralBlocks.paletteSet(f.getStrataPalette());
    }
}
