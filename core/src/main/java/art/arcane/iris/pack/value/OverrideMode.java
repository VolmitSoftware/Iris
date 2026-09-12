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

package art.arcane.iris.pack.value;

import art.arcane.volmlib.util.documentation.Description;

@Description("Controls how an override list is combined with the inherited object set from the target biome.")
public enum OverrideMode {
    @Description("Ignore the override list entirely. Use only the inherited objects from the target biome (subject to inheritObjects).")
    INHERIT_ONLY,

    @Description("Append override list entries after the inherited objects from the target biome. Both sets are placed.")
    MERGE,

    @Description("Use only the override list. The inherited objects from the target biome are discarded regardless of inheritObjects.")
    REPLACE
}
