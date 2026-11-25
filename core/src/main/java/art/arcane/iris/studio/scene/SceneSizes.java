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

package art.arcane.iris.studio.scene;

import java.util.List;

/**
 * The sizes a view builds its scenes at.
 *
 * @param blocksAcross smallest first
 * @param opening      the size shown first, one of {@code blocksAcross}
 */
public record SceneSizes(List<Integer> blocksAcross, int opening) {
    public SceneSizes {
        blocksAcross = List.copyOf(blocksAcross);
        if (!blocksAcross.contains(opening)) {
            throw new IllegalArgumentException("Opening size " + opening + " is not one of " + blocksAcross);
        }
    }
}
