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

package art.arcane.iris.engine.framework.structure.assembly;

/** Whether a structure can produce pieces. */
public enum Viability {
    /** Its pieces cannot be assembled, or its graph has errors. */
    NOT_VIABLE,
    /** Assembly completes for the samples tried, but a start or branch may still come up empty. */
    VIABLE,
    /** Every start and branch ends in pieces, so a placement of it always produces a structure. */
    GUARANTEED;

    public boolean isViable() {
        return this != NOT_VIABLE;
    }
}
