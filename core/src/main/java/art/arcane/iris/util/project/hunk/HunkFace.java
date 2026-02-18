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

package art.arcane.iris.util.project.hunk;

public enum HunkFace {
    TOP(art.arcane.volmlib.util.hunk.HunkFace.TOP),
    BOTTOM(art.arcane.volmlib.util.hunk.HunkFace.BOTTOM),
    EAST(art.arcane.volmlib.util.hunk.HunkFace.EAST),
    WEST(art.arcane.volmlib.util.hunk.HunkFace.WEST),
    NORTH(art.arcane.volmlib.util.hunk.HunkFace.NORTH),
    SOUTH(art.arcane.volmlib.util.hunk.HunkFace.SOUTH);

    private final art.arcane.volmlib.util.hunk.HunkFace shared;

    HunkFace(art.arcane.volmlib.util.hunk.HunkFace shared) {
        this.shared = shared;
    }

    public art.arcane.volmlib.util.hunk.HunkFace shared() {
        return shared;
    }

    public static HunkFace fromShared(art.arcane.volmlib.util.hunk.HunkFace shared) {
        return HunkFace.valueOf(shared.name());
    }
}
