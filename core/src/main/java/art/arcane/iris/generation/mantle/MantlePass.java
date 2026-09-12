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

package art.arcane.iris.generation.mantle;

import java.util.List;

/**
 * One priority level of mantle components.
 *
 * @param components           every component registered at this priority, enabled or not
 * @param passChunkRadius      chunk ring this pass must complete: ceilDiv of this pass' widest
 *                             component output reach plus every later pass' reach
 * @param downstreamBlockRadius block reach summed over all LATER passes only. A component must be
 *                             generated across its own reach plus this, or later passes read
 *                             half-written data from it
 */
public record MantlePass(List<MantleComponent> components, int passChunkRadius, int downstreamBlockRadius) {
}
