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

import art.arcane.iris.builtin.objects.ObjectHost;
import art.arcane.iris.builtin.objects.ProceduralObjectSettings;
import art.arcane.iris.builtin.objects.ProceduralPlacement;
import art.arcane.iris.builtin.objects.tree.Tree;
import art.arcane.iris.engine.object.procedural.coral.ResolvedCoral;
import art.arcane.iris.engine.object.procedural.crystal.ResolvedCrystal;
import art.arcane.iris.engine.object.procedural.formation.ResolvedFormation;
import art.arcane.iris.engine.object.procedural.fungi.ResolvedFungus;
import art.arcane.iris.engine.object.procedural.ruin.ResolvedRuin;
import art.arcane.iris.engine.object.procedural.tree.ResolvedTree;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/** The procedural objects a region or biome declares, each made placeable once per world, in the order the settings list them. */
public final class ResolvedProceduralObjects {
    private ResolvedProceduralObjects() {
    }

    /** The placements {@code settings} declare in the world of {@code host}, resolved the first time a world asks. */
    public static List<ProceduralPlacement> of(ObjectHost host, ProceduralObjectSettings settings) {
        return host.procedural(settings, declared -> resolve(declared, host));
    }

    static List<ProceduralPlacement> resolve(ProceduralObjectSettings settings, ObjectHost host) {
        List<ProceduralPlacement> all = new ArrayList<>();
        for (Tree tree : settings.trees()) {
            all.add(new ResolvedTree(tree, host));
        }
        addAll(all, settings.ruins(), host, ResolvedRuin::new);
        addAll(all, settings.formations(), host, ResolvedFormation::new);
        addAll(all, settings.coral(), host, ResolvedCoral::new);
        addAll(all, settings.fungi(), host, ResolvedFungus::new);
        addAll(all, settings.crystals(), host, ResolvedCrystal::new);
        return List.copyOf(all);
    }

    private static <S> void addAll(List<ProceduralPlacement> all, List<S> settings, ObjectHost host,
                                   BiFunction<S, ObjectHost, ResolvedProceduralPlacement> resolve) {
        for (S entry : settings) {
            all.add(resolve.apply(entry, host));
        }
    }
}
