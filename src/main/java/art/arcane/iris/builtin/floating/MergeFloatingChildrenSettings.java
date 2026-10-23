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

package art.arcane.iris.builtin.floating;

import com.volmit.config.Level;
import com.volmit.IrisFeature;
import com.volmit.config.Desc;
import com.volmit.config.Feature;

/** A biome's {@code mergeFloatingChildBiomes}, written as the bare boolean. */
@Desc("When true, every floating child entry is sampled independently and their solid masks are unioned, so multiple floating islands can stack, overlap, and collide instead of the picker choosing only one child per column.")
@Feature(id = "iris:floating/merge", name = "Merge floating child biomes", key = "mergeFloatingChildBiomes", root = "merge", targets = Level.BIOME)
public final class MergeFloatingChildrenSettings implements IrisFeature {
    @Desc("When true, every floating child entry is sampled independently and their solid masks are unioned, so multiple floating islands can stack, overlap, and collide instead of the picker choosing only one child per column.")
    private boolean merge = false;

    public MergeFloatingChildrenSettings() {
    }

    public MergeFloatingChildrenSettings(boolean merge) {
        this.merge = merge;
    }

    public boolean merge() {
        return merge;
    }
}
