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
 * Whether a dimension is post processed, written as {@code "postProcessing": true}. Iris has never read this flag: surface smoothing runs
 * whatever it says, so it is declared to keep the key valid in packs.
 */
@Desc("Use post processing or not")
@Feature(id = "iris:post/enabled", name = "Post processing", key = "postProcessing", root = "enabled", targets = Level.DIMENSION)
public final class PostProcessingSettings implements IrisFeature {
    public static final boolean DEFAULT_ENABLED = true;

    @Desc("Use post processing or not")
    private boolean enabled = DEFAULT_ENABLED;

    public PostProcessingSettings() {
    }

    public boolean enabled() {
        return enabled;
    }
}
