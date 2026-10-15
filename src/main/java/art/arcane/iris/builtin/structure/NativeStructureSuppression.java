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

package art.arcane.iris.builtin.structure;

import com.volmit.config.Desc;

/** Whether a structure placement replaces the platform structure its structures stand in for. */
@Desc("Controls whether an Iris structure placement suppresses its imported native source.")
public enum NativeStructureSuppression {
    @Desc("Leaves native generation enabled alongside this Iris structure placement.")
    NONE,

    @Desc("Suppresses the structure's vanillaSource when used by a validated dimension-level replacement placement.")
    REPLACE_SOURCE
}
