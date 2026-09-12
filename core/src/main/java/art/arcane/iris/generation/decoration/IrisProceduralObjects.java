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

package art.arcane.iris.generation.decoration;

import art.arcane.iris.generation.decoration.coral.IrisCoral;
import art.arcane.iris.generation.decoration.crystal.IrisCrystal;
import art.arcane.iris.generation.decoration.formation.IrisFormation;
import art.arcane.iris.generation.decoration.fungus.IrisFungus;
import art.arcane.iris.generation.decoration.ruin.IrisRuin;
import art.arcane.iris.generation.decoration.tree.IrisProceduralTree;

import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.volmlib.util.collection.KList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("procedural-objects")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("Procedurally generated objects placed in a biome or region. Unlike the objects block (which loads iob files), these are generated from scratch at world-gen time.")
@Data
public class IrisProceduralObjects {
    @ArrayType(min = 1, type = IrisProceduralTree.class)
    @Description("Procedurally generated trees.")
    private KList<IrisProceduralTree> trees = new KList<>();

    @ArrayType(min = 1, type = IrisRuin.class)
    @Description("Procedurally generated ruins (crumbling pillars, walls, arches, rubble).")
    private KList<IrisRuin> ruins = new KList<>();

    @ArrayType(min = 1, type = IrisFormation.class)
    @Description("Procedurally generated natural rock formations (spires, hoodoos, arches, sea stacks, boulders, basalt columns).")
    private KList<IrisFormation> formations = new KList<>();

    @ArrayType(min = 1, type = IrisCoral.class)
    @Description("Procedurally generated underwater coral structures.")
    private KList<IrisCoral> coral = new KList<>();

    @ArrayType(min = 1, type = IrisFungus.class)
    @Description("Procedurally generated mushrooms and shelf fungi.")
    private KList<IrisFungus> fungi = new KList<>();

    @ArrayType(min = 1, type = IrisCrystal.class)
    @Description("Procedurally generated crystal clusters (usually placed in caves).")
    private KList<IrisCrystal> crystals = new KList<>();

    public KList<IrisProceduralPlacement> getAllPlacements() {
        KList<IrisProceduralPlacement> all = new KList<>();
        if (trees != null) {
            all.addAll(trees);
        }
        if (ruins != null) {
            all.addAll(ruins);
        }
        if (formations != null) {
            all.addAll(formations);
        }
        if (coral != null) {
            all.addAll(coral);
        }
        if (fungi != null) {
            all.addAll(fungi);
        }
        if (crystals != null) {
            all.addAll(crystals);
        }
        return all;
    }

    public boolean isEmpty() {
        return getAllPlacements().isEmpty();
    }
}
