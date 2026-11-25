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

import java.util.Map;

/**
 * Which scene of a view is wanted.
 *
 * @param variant   which of the entry's variants; a view wraps it around the variants it has
 * @param size      blocks across, one of the view's {@link SceneSizes}; 0 for a view without sizes
 * @param overrides values typed into the view's form, by field name, which change the scene only
 */
public record SceneRequest(String key, int variant, int size, Map<String, String> overrides) {
    public SceneRequest {
        overrides = Map.copyOf(overrides);
    }
}
