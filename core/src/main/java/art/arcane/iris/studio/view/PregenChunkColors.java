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

package art.arcane.iris.studio.view;

import java.awt.Color;

/** The colour each state of a chunk has on the pregeneration map. */
final class PregenChunkColors {
    static final Color GENERATING = new Color(204, 168, 107);
    static final Color GENERATED = new Color(101, 194, 149);
    static final Color EXISTS = new Color(93, 133, 112);
    static final Color NETWORK = new Color(157, 138, 176);
    static final Color NETWORK_GENERATING = new Color(129, 112, 145);

    private PregenChunkColors() {
    }
}
