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

package art.arcane.iris.engine.object.procedural;

import art.arcane.iris.builtin.objects.ProceduralObjectSettings;
import art.arcane.iris.builtin.objects.tree.Tree;
import art.arcane.iris.engine.object.procedural.coral.ResolvedCoral;
import art.arcane.iris.engine.object.procedural.crystal.ResolvedCrystal;
import art.arcane.iris.engine.object.procedural.formation.ResolvedFormation;
import art.arcane.iris.engine.object.procedural.fungi.ResolvedFungus;
import art.arcane.iris.engine.object.procedural.ruin.ResolvedRuin;
import art.arcane.iris.engine.object.procedural.tree.ResolvedTree;
import art.arcane.volmlib.util.collection.KList;

import java.util.List;
import java.util.function.Function;

/** The procedural objects a region or biome declares, each made placeable once, in the order the settings list them. */
public final class ResolvedProceduralObjects {
    private final KList<IrisProceduralPlacement> all = new KList<>();

    public ResolvedProceduralObjects(ProceduralObjectSettings settings) {
        for (Tree tree : settings.trees()) {
            all.add(new ResolvedTree(tree));
        }
        addAll(settings.ruins(), ResolvedRuin::new);
        addAll(settings.formations(), ResolvedFormation::new);
        addAll(settings.coral(), ResolvedCoral::new);
        addAll(settings.fungi(), ResolvedFungus::new);
        addAll(settings.crystals(), ResolvedCrystal::new);
    }

    private <S> void addAll(List<S> settings, Function<S, ResolvedProceduralPlacement> resolve) {
        for (S entry : settings) {
            all.add(resolve.apply(entry));
        }
    }

    public KList<IrisProceduralPlacement> getAllPlacements() {
        return all;
    }

    public boolean isEmpty() {
        return all.isEmpty();
    }
}
