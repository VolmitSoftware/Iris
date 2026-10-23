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

package art.arcane.iris.builtin.terrain;

import com.volmit.config.Level;
import com.volmit.IrisFeature;
import com.volmit.config.Desc;
import com.volmit.config.Feature;

/** How many layers a locked biome iterates below the surface, written as {@code "lockLayersMax": 7}. A biome without the key iterates seven. */
@Desc("The max layers to iterate below the surface for locked layer biomes (mesa).")
@Feature(id = "iris:terrain/lock-layers-max", name = "Lock layers max", key = "lockLayersMax", root = "layers", targets = Level.BIOME)
public final class LockLayersMaxSettings implements IrisFeature {
    public static final int DEFAULT_LAYERS = 7;

    @Desc("The max layers to iterate below the surface for locked layer biomes (mesa).")
    private int layers = DEFAULT_LAYERS;

    public LockLayersMaxSettings() {
    }

    public int layers() {
        return layers;
    }
}
