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

/** How a structure placement picks the height it anchors a structure at. The placement identity hashes the order. */
@Desc("Selects the vertical environment used to anchor an Iris structure placement.")
public enum StructureAnchorMode {
    @Desc("Preserve the historical underground boolean behavior.")
    LEGACY,

    @Desc("Anchor to the terrain surface and use minHeight/maxHeight as a surface gate.")
    SURFACE,

    @Desc("Choose a deterministic Y inside the minHeight/maxHeight band.")
    HEIGHT_BAND,

    @Desc("Anchor in carved space with solid support below.")
    CAVE_FLOOR,

    @Desc("Anchor in carved space with solid support above.")
    CAVE_CEILING,

    @Desc("Anchor in carved space without nearby floor or ceiling support.")
    CAVE_CENTER,

    @Desc("Anchor at any carved-space position.")
    CAVE_ANY;

    /** Whether the anchor looks for carved space. */
    public boolean isCave() {
        return switch (this) {
            case CAVE_FLOOR, CAVE_CEILING, CAVE_CENTER, CAVE_ANY -> true;
            default -> false;
        };
    }
}
