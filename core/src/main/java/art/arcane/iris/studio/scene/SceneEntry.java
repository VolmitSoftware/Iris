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

import java.util.Locale;

/**
 * One thing a view lists.
 *
 * @param key      what the view finds it by
 * @param group    the heading the studio lists it under
 * @param variants how many scenes it has, at least 1
 */
public record SceneEntry(String key, String name, String group, int variants) {
    private static final String PRESET_GROUP = "Presets";

    /** An entry for an enum constant a pack picks by name, shown as "Tall oak" for TALL_OAK. */
    public static SceneEntry preset(Enum<?> constant, int variants) {
        return new SceneEntry(constant.name(), readable(constant.name()), PRESET_GROUP, variants);
    }

    static String readable(String constantName) {
        String lower = constantName.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
