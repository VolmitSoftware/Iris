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

package art.arcane.iris.engine.mantle.components;

import com.volmit.noise.NoiseFactory;

/**
 * What the cave carvers need to know about one world that does not change while it runs: the noise the world's pack builds,
 * the carve seed, the dimension's height, its fluid and cave-lava heights (above the dimension's bottom) and the platform's Y of
 * its bottom, which converts the absolute world Y of a cave biome band.
 */
public record CarveWorld(NoiseFactory noise, long carveSeed, int height, int fluidHeight, int lavaHeight, int minHeight) {
}
