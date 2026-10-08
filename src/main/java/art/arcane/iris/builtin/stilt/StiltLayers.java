/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.builtin.stilt;

import com.volmit.object.ObjectBlock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The bottom and top layers of the blocks a stilt can repeat, with their positions already transformed
 * around the anchor. Each layer keeps the blocks at its lowest or highest height seen so far.
 */
public final class StiltLayers {
    private final List<ObjectBlock> bottom = new ArrayList<>();
    private final List<ObjectBlock> top = new ArrayList<>();
    private int bottomY = Integer.MAX_VALUE;
    private int topY = Integer.MIN_VALUE;

    public void include(ObjectBlock transformed) {
        int y = transformed.position().y();
        if (y < bottomY) {
            bottomY = y;
            bottom.clear();
        }
        if (y == bottomY) {
            bottom.add(transformed);
        }
        if (y > topY) {
            topY = y;
            top.clear();
        }
        if (y == topY) {
            top.add(transformed);
        }
    }

    public boolean isEmpty() {
        return bottom.isEmpty();
    }

    public List<ObjectBlock> bottom() {
        return Collections.unmodifiableList(bottom);
    }

    public List<ObjectBlock> top() {
        return Collections.unmodifiableList(top);
    }

    public int bottomY() {
        return bottomY;
    }

    public int topY() {
        return topY;
    }
}
