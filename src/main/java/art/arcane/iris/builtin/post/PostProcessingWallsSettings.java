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

package art.arcane.iris.builtin.post;

import com.volmit.config.Level;
import com.volmit.IrisFeature;
import com.volmit.config.Desc;
import com.volmit.config.Feature;

/**
 * Whether post processing paints walls on steep faces in a dimension, written as {@code "postProcessingWalls": false}. A dimension without the key paints them.
 */
@Desc("Add painted walls in post processing")
@Feature(id = "iris:post/walls", name = "Post processing walls", key = "postProcessingWalls", root = "enabled", targets = Level.DIMENSION)
public final class PostProcessingWallsSettings implements IrisFeature {
    public static final boolean DEFAULT_ENABLED = true;

    @Desc("Add painted walls in post processing")
    private boolean enabled = DEFAULT_ENABLED;

    public PostProcessingWallsSettings() {
    }

    public boolean enabled() {
        return enabled;
    }
}
