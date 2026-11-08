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

package art.arcane.iris.core.tooling;

import com.volmit.tooling.world.ToolWorlds;

/**
 * The tool worlds a platform adapter offers, bound with {@code IrisServices} by an adapter that has them. A platform
 * that binds none answers {@link UnsupportedToolWorlds}.
 */
public interface ToolWorldsHost {
    /** The tool worlds of the pack in the folder {@code packName}, which tools reach through {@code ToolPack.worlds()}. */
    ToolWorlds worldsOf(String packName);
}
