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

package art.arcane.iris.core.modules;

/**
 * Thrown by a host's {@link ModuleServices.CandidateChunks} to end a search for a module's start of a platform structure
 * when the host's own checks ran out of budget; {@link ModuleServices#locateStart} reports it as a reached search limit.
 */
public final class StructureSearchLimitException extends RuntimeException {
    public StructureSearchLimitException() {
        super(null, null, false, false);
    }
}
