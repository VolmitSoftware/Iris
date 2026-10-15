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

package art.arcane.iris.builtin.carve;

import com.volmit.FeatureAttachment.Target;
import com.volmit.IrisFeature;
import com.volmit.config.Desc;
import com.volmit.config.Feature;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;

/** A dimension's {@code caveLavaHeight}, written as the bare number. */
@Desc("The subterranean fluid (cave lava) layer height, in engine-local Y where 0 is the bottom of the dimension, not world Y. With the default -64..320 height, 8 means world Y -56.")
@Feature(id = "iris:carve/lava-height", name = "Cave lava height", key = "caveLavaHeight", root = "height", targets = Target.DIMENSION)
public final class CaveLavaHeightSettings implements IrisFeature {
    public static final int DEFAULT_HEIGHT = 8;

    @MinNumber(0)
    @MaxNumber(318)
    @Desc("The subterranean fluid (cave lava) layer height, in engine-local Y where 0 is the bottom of the dimension, not world Y. With the default -64..320 height, 8 means world Y -56.")
    private int height = DEFAULT_HEIGHT;

    public CaveLavaHeightSettings() {
    }

    public CaveLavaHeightSettings(int height) {
        this.height = height;
    }

    public int height() {
        return height;
    }
}
