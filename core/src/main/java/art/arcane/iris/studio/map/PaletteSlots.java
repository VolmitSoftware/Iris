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

package art.arcane.iris.studio.map;

import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.interpolation.IrisInterpolation;

/**
 * How a palette splits the noise range between its blocks, the way IrisBiomePaletteLayer#getBlockData lists them
 * and CNG#fit picks from that list.
 */
final class PaletteSlots {
    private PaletteSlots() {
    }

    /**
     * Each block fills as many slots of the noise range as its weight; blocks that can't be found get none.
     *
     * @return the palette entry each slot belongs to
     */
    static int[] entryPerSlot(KList<IrisBlockData> palette, IrisData data) {
        KList<Integer> entries = new KList<>();
        for (int entry = 0; entry < palette.size(); entry++) {
            IrisBlockData block = palette.get(entry);
            if (block.getBlockData(data) == null) {
                continue;
            }
            for (int copy = 0; copy < block.getWeight(); copy++) {
                entries.add(entry);
            }
        }
        return entries.stream().mapToInt(Integer::intValue).toArray();
    }

    static int slotForNoise(double noise, int slotCount) {
        int slot = (int) Math.round(IrisInterpolation.lerp(0, slotCount - 1, noise));
        return slot >= 0 && slot < slotCount ? slot : 0;
    }
}
