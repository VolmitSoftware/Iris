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

package art.arcane.iris.builtin.objects;

import art.arcane.iris.builtin.objects.coral.Coral;
import art.arcane.iris.builtin.objects.crystal.Crystal;
import art.arcane.iris.builtin.objects.formation.Formation;
import art.arcane.iris.builtin.objects.fungi.Fungus;
import art.arcane.iris.builtin.objects.ruin.Ruin;
import art.arcane.iris.builtin.objects.tree.Tree;
import com.volmit.FeatureAttachment.Target;
import com.volmit.IrisFeature;
import com.volmit.config.Desc;
import com.volmit.config.Feature;
import com.volmit.config.MinItems;
import com.volmit.config.Snippet;

import java.util.Collections;
import java.util.List;

/**
 * The procedural objects a region or biome declares, written as {@code "proceduralObjects": {"trees": [...], ...}}: things
 * Iris generates from scratch at world-gen time and places like object placements.
 */
@Snippet("procedural-objects")
@Desc("Procedurally generated objects placed in a biome or region. Unlike the objects block (which loads iob files), these are generated from scratch at world-gen time.")
@Feature(id = "iris:objects/procedural", name = "Procedural objects", key = "proceduralObjects", targets = {Target.REGION, Target.BIOME})
public final class ProceduralObjectSettings implements IrisFeature {
    @MinItems(1)
    @Desc("Procedurally generated trees.")
    private List<Tree> trees = List.of();

    @MinItems(1)
    @Desc("Procedurally generated ruins (crumbling pillars, walls, arches, rubble).")
    private List<Ruin> ruins = List.of();

    @MinItems(1)
    @Desc("Procedurally generated natural rock formations (spires, hoodoos, arches, sea stacks, boulders, basalt columns).")
    private List<Formation> formations = List.of();

    @MinItems(1)
    @Desc("Procedurally generated underwater coral structures.")
    private List<Coral> coral = List.of();

    @MinItems(1)
    @Desc("Procedurally generated mushrooms and shelf fungi.")
    private List<Fungus> fungi = List.of();

    @MinItems(1)
    @Desc("Procedurally generated crystal clusters (usually placed in caves).")
    private List<Crystal> crystals = List.of();

    public ProceduralObjectSettings() {
    }

    public List<Tree> trees() {
        return view(trees);
    }

    public List<Ruin> ruins() {
        return view(ruins);
    }

    public List<Formation> formations() {
        return view(formations);
    }

    public List<Coral> coral() {
        return view(coral);
    }

    public List<Fungus> fungi() {
        return view(fungi);
    }

    public List<Crystal> crystals() {
        return view(crystals);
    }

    private static <T> List<T> view(List<T> list) {
        return list == null ? List.of() : Collections.unmodifiableList(list);
    }
}
