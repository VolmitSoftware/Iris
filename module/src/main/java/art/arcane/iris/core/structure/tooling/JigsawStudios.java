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

package art.arcane.iris.core.structure.tooling;

import art.arcane.iris.core.service.JigsawStudioService;
import com.volmit.tooling.ToolPack;

/**
 * The Jigsaw Studio of one pack, which its jigsaw commands share. It is created by the first command that needs it, so a
 * pack nobody opens a studio for never builds one.
 */
public final class JigsawStudios {
    private JigsawStudioService studio;

    /** The studio, created with {@code pack}'s log when no command has asked for it yet. */
    public synchronized JigsawStudioService of(ToolPack pack) {
        if (studio == null) {
            studio = new JigsawStudioService(pack.log());
        }
        return studio;
    }
}
