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

import com.volmit.tooling.CommandContext;
import com.volmit.tooling.PlatformStructures;

import java.util.Optional;

/** Asks a command's platform for its structures, and tells the sender when it has none. */
public final class PlatformStructuresAccess {
    public static final String UNAVAILABLE = "PlatformStructures is unavailable on this platform";

    private PlatformStructuresAccess() {
    }

    /** The platform's structures; empty after telling the sender that this platform has none. */
    public static Optional<PlatformStructures> require(CommandContext context) {
        Optional<PlatformStructures> platform = context.platformStructures();
        if (platform.isEmpty()) {
            context.sender().error(UNAVAILABLE);
        }
        return platform;
    }
}
