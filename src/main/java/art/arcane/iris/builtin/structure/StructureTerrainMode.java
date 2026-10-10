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

@Desc("Controls how terrain is integrated with a structure.")
public enum StructureTerrainMode {
    @Desc("Replays the registered native structure's own authored terrain adaptation (beard surface fitting, BURY/ENCAPSULATE fill, legacy template-air clearing). Does nothing for editable Iris structures, which have no source adaptation to replay.")
    SOURCE,

    @Desc("Disables all terrain integration. Pieces are placed into completely unmodified terrain and may end up embedded in solid rock or floating over dips.")
    PRESERVE,

    @Desc("Clears one straight axis-aligned box around the assembled pieces, expanded by horizontalPadding, ceilingPadding, and floorPadding. Ignores the carve shape and erosion settings.")
    BORE,

    @Desc("Clears the padded envelope using the configured carve shape (BOX, ROUNDED, or noise-eroded ERODED) so the cavity hugs the assembled pieces instead of a straight box.")
    FORCE_CARVE,

    @Desc("Raises surface terrain from processed solid rigid-template foundations at or below each authored ground plane with a 12-block falloff, even when the registered structure has no terrain adaptation. Existing higher terrain and authored air remain untouched.")
    VACUUM,

    @Desc("Fills the padded piece volume with solid blocks before any piece is placed so shells, walls, and floors land in solid ground instead of pre-carved air. Only air and liquid cells are filled; existing terrain and structures are never overwritten. Native pieces then carve their own interiors.")
    ENCASE
}
