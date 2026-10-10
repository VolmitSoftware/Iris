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

import com.volmit.FeatureAttachment.Target;
import com.volmit.IrisFeature;
import com.volmit.config.Desc;
import com.volmit.config.Feature;

/** Whether a biome's layers are locked to its highest possible height, written as {@code "lockLayers": true}. A biome without the key is not locked. */
@Desc("Layers no longer descend from the surface block, they descend from the max possible height the biome can produce (constant) creating mesa like layers.")
@Feature(id = "iris:terrain/lock-layers", name = "Lock layers", key = "lockLayers", root = "locked", targets = Target.BIOME)
public final class LockLayersSettings implements IrisFeature {
    @Desc("Layers no longer descend from the surface block, they descend from the max possible height the biome can produce (constant) creating mesa like layers.")
    private boolean locked = false;

    public LockLayersSettings() {
    }

    public boolean locked() {
        return locked;
    }
}
