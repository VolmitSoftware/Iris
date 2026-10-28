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
import art.arcane.iris.modules.objects.ProceduralPlacement;
import com.volmit.resource.PackResources;
import com.volmit.world.PackWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/** The procedural objects a region or biome declares, each made placeable once per world, in the order the settings list them. */
public final class ResolvedProceduralObjects {
    private ResolvedProceduralObjects() {
    }

    /** The placements {@code settings} declare in {@code world}, resolved the first time the world asks. */
    public static List<ProceduralPlacement> of(PackWorld world, ProceduralObjectSettings settings) {
        PackResources resources = world.resources();
        return world.state(ResolvedByDeclaration.class, ignored -> new ResolvedByDeclaration()).placements
                .computeIfAbsent(settings, declared -> resolve(declared, resources));
    }

    /** The placements {@code settings} declare, resolved anew, for work outside any world such as working out the pack's reach. */
    public static List<ProceduralPlacement> resolve(ProceduralObjectSettings settings, PackResources resources) {
        List<ProceduralPlacement> all = new ArrayList<>();
        for (Tree tree : settings.trees()) {
            all.add(new ResolvedTree(tree, resources));
        }
        addAll(all, settings.ruins(), resources, ResolvedRuin::new);
        addAll(all, settings.formations(), resources, ResolvedFormation::new);
        addAll(all, settings.coral(), resources, ResolvedCoral::new);
        addAll(all, settings.fungi(), resources, ResolvedFungus::new);
        addAll(all, settings.crystals(), resources, ResolvedCrystal::new);
        return List.copyOf(all);
    }

    private static <S> void addAll(List<ProceduralPlacement> all, List<S> settings, PackResources resources,
                                   BiFunction<S, PackResources, ResolvedProceduralPlacement> resolve) {
        for (S entry : settings) {
            all.add(resolve.apply(entry, resources));
        }
    }

    /** A world's resolved placements, by the settings that declare them. */
    private static final class ResolvedByDeclaration {
        private final Map<ProceduralObjectSettings, List<ProceduralPlacement>> placements = new ConcurrentHashMap<>();
    }
}
