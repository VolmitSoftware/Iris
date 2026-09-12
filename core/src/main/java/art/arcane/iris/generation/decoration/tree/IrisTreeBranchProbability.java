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

package art.arcane.iris.generation.decoration.tree;

import art.arcane.volmlib.util.documentation.Description;

@Description("Controls how the chance of spawning a branch varies with trunk height (0 at the base, 1 at the top).")
public enum IrisTreeBranchProbability {
    @Description("Same branch chance at every height.")
    CONSTANT,
    @Description("Ramps straight from base chance to crown chance.")
    LINEAR,
    @Description("S-curve that switches on around a midpoint height.")
    SIGMOID,
    @Description("Chance rises with height raised to an exponent, clustering branches near the crown.")
    TOP_HEAVY,
    @Description("Bell curve peaking in a height band.")
    GAUSSIAN,
    @Description("Irregular organic branching from deterministic noise.")
    NOISE,
    @Description("Chance falls with height (1-t)^exponent, clustering branches near the base.")
    BOTTOM_HEAVY,
    @Description("Regular whorl rings of branches at evenly spaced heights (periods controls count).")
    PERIODIC,
    @Description("Branches only within a height window around mean +/- std.")
    BAND,
    @Description("Inverse of GAUSSIAN: branches avoid the mean band and favor base and crown.")
    INVERSE_GAUSSIAN,
    @Description("Exponential decay from the base upward (constant * e^-exponent*t).")
    EXPONENTIAL_DECAY
}
