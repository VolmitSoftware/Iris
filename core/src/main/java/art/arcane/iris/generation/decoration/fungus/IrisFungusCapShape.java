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

package art.arcane.iris.generation.decoration.fungus;

import art.arcane.volmlib.util.documentation.Description;

@Description("The silhouette of the mushroom cap. Each shape drives how the cap radius and thickness vary from its apex down to the rim, giving the fungus a distinct profile.")
public enum IrisFungusCapShape {
    @Description("A rounded hemisphere cap that bulges up and curls slightly under at the rim, the classic red toadstool dome.")
    DOME,
    @Description("A wide, nearly flat umbrella that holds its full radius almost to the edge before dropping, the brown-mushroom plate look.")
    FLAT,
    @Description("An upturned bowl whose rim lifts higher than its center, cupping upward like a chanterelle or funnel mushroom.")
    FUNNEL,
    @Description("A tall pointed cone that tapers from a broad base to a sharp apex, the witch-hat parasol silhouette.")
    CONICAL,
    @Description("A very wide but shallow slab, a low overhanging shelf-like roof that spreads far past the stem with minimal vertical rise.")
    FLAT_WIDE
}
