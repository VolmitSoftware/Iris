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

package art.arcane.iris.generation.decoration;

import art.arcane.volmlib.util.documentation.Description;

@Description("Represents a location where decorations should go")
public enum IrisDecorationPart {
    @Description("The default, decorate anywhere")
    NONE,

    @Description("Targets shore lines (typically for sugar cane)")
    SHORE_LINE,

    @Description("Target sea surfaces (typically for lilypads)")
    SEA_SURFACE,

    @Description("Targets the sea floor (entire placement must be bellow sea level)")
    SEA_FLOOR,

    @Description("Decorates on cave & carving ceilings or underside of overhangs")
    CEILING,
}
