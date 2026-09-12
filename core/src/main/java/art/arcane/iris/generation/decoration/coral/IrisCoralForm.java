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

package art.arcane.iris.generation.decoration.coral;

import art.arcane.volmlib.util.documentation.Description;

@Description("The overall silhouette a procedural coral grows into. Each form drives a completely different generation routine in the coral generator, from tree-like branching arms to rounded brain blobs and thin wavy tendrils.")
public enum IrisCoralForm {
    @Description("A tree-like coral built from a stout central stalk that sprouts several upward-leaning arms (distributed by golden angle or randomly), each optionally splitting once into sub-arms, with small coral tip clusters (fans / sea pickles) at the ends. The most structurally complex form and the closest analogue to a procedural tree.")
    BRANCHING,

    @Description("A thin, single-block-thick vertical fan plane that rises and widens with height like a sheet of fire or horn coral. Reads as a flat decorative blade rather than a volume.")
    FAN,

    @Description("A rounded, noise-perturbed ellipsoid blob of brain coral that bulges out of the seafloor like a boulder, with an irregular wrinkled surface driven by 3D value noise.")
    BRAIN,

    @Description("A stout, thick vertical column of coral block topped with a dense tip cluster. Wider and blunter than a tendril, reading as a solid coral pillar or stack.")
    PILLAR,

    @Description("Several very thin (one block wide) wavy stalks that rise upward and wander laterally using value noise, like a clump of swaying coral whips or kelp-thin growths.")
    TENDRIL
}
