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

import javax.swing.JComponent;
import java.util.function.IntConsumer;

/**
 * A slider floating on the right of the 3D noise view that sets how many blocks the highest noise rises above the
 * lowest. Its steps grow with the height, so low terrain is as easy to set as a tall one.
 */
final class TerrainHeightSlider {
    private static final String TOOLTIP = "How many blocks the highest noise rises above the lowest";
    private static final int LOWEST_BLOCKS = 1;
    private static final int TALLEST_BLOCKS = 4064;
    private static final int DEFAULT_BLOCKS = 64;
    private static final int POSITIONS = 1000;

    private final FloatingSlider slider = new FloatingSlider(TOOLTIP, this::showHeightAt);
    private final IntConsumer onHeightChanged;
    private int blocks = DEFAULT_BLOCKS;

    /**
     * @param onHeightChanged told the height in blocks whenever the user moves the slider
     */
    TerrainHeightSlider(IntConsumer onHeightChanged) {
        this.onHeightChanged = onHeightChanged;
        slider.showQuietly(0, POSITIONS, positionOf(DEFAULT_BLOCKS));
        slider.showLabel(Integer.toString(blocks));
    }

    /**
     * The view with this slider floating over its right edge, halfway down.
     */
    JComponent floatingOver(JComponent view) {
        return slider.floatingOver(view);
    }

    int blocks() {
        return blocks;
    }

    // Noise measured in blocks keeps its own heights, so the slider has nothing to set for it.
    void setUsable(boolean usable) {
        slider.setVisible(usable);
    }

    private void showHeightAt(int position) {
        int nextBlocks = blocksAt(position);
        if (nextBlocks == blocks) {
            return;
        }
        blocks = nextBlocks;
        slider.showLabel(Integer.toString(blocks));
        onHeightChanged.accept(blocks);
    }

    private static int blocksAt(int position) {
        double share = position / (double) POSITIONS;
        return (int) Math.round(Math.exp(Math.log(LOWEST_BLOCKS) + share * (Math.log(TALLEST_BLOCKS) - Math.log(LOWEST_BLOCKS))));
    }

    private static int positionOf(int heightBlocks) {
        double share = (Math.log(heightBlocks) - Math.log(LOWEST_BLOCKS)) / (Math.log(TALLEST_BLOCKS) - Math.log(LOWEST_BLOCKS));
        return (int) Math.round(share * POSITIONS);
    }
}
