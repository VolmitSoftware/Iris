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

package art.arcane.iris.engine.object.procedural;

import art.arcane.iris.builtin.objects.ruin.Ruin;
import art.arcane.iris.engine.object.procedural.ruin.ResolvedRuin;
import art.arcane.iris.builtin.objects.fungi.Fungus;
import art.arcane.iris.engine.object.procedural.fungi.ResolvedFungus;
import art.arcane.iris.builtin.objects.formation.Formation;
import art.arcane.iris.engine.object.procedural.formation.ResolvedFormation;
import art.arcane.iris.builtin.objects.crystal.Crystal;
import art.arcane.iris.engine.object.procedural.crystal.ResolvedCrystal;
import art.arcane.iris.builtin.objects.coral.Coral;
import art.arcane.iris.engine.object.procedural.coral.ResolvedCoral;
import art.arcane.iris.engine.object.procedural.tree.IrisProceduralTree;
import art.arcane.iris.engine.object.annotations.ArrayType;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.Snippet;
import art.arcane.volmlib.util.collection.KList;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import lombok.AllArgsConstructor;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("procedural-objects")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Procedurally generated objects placed in a biome or region. Unlike the objects block (which loads iob files), these are generated from scratch at world-gen time.")
@Data
public class IrisProceduralObjects {
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    private final transient Map<Object, ResolvedProceduralPlacement> placeables = new ConcurrentHashMap<>();

    @ArrayType(min = 1, type = IrisProceduralTree.class)
    @Desc("Procedurally generated trees.")
    private KList<IrisProceduralTree> trees = new KList<>();

    @ArrayType(min = 1, type = Ruin.class)
    @Desc("Procedurally generated ruins (crumbling pillars, walls, arches, rubble).")
    private KList<Ruin> ruins = new KList<>();

    @ArrayType(min = 1, type = Formation.class)
    @Desc("Procedurally generated natural rock formations (spires, hoodoos, arches, sea stacks, boulders, basalt columns).")
    private KList<Formation> formations = new KList<>();

    @ArrayType(min = 1, type = Coral.class)
    @Desc("Procedurally generated underwater coral structures.")
    private KList<Coral> coral = new KList<>();

    @ArrayType(min = 1, type = Fungus.class)
    @Desc("Procedurally generated mushrooms and shelf fungi.")
    private KList<Fungus> fungi = new KList<>();

    @ArrayType(min = 1, type = Crystal.class)
    @Desc("Procedurally generated crystal clusters (usually placed in caves).")
    private KList<Crystal> crystals = new KList<>();

    public KList<IrisProceduralPlacement> getAllPlacements() {
        KList<IrisProceduralPlacement> all = new KList<>();
        if (trees != null) {
            all.addAll(trees);
        }
        addPlaceable(all, ruins, ResolvedRuin::new);
        addPlaceable(all, formations, ResolvedFormation::new);
        addPlaceable(all, coral, ResolvedCoral::new);
        addPlaceable(all, fungi, ResolvedFungus::new);
        addPlaceable(all, crystals, ResolvedCrystal::new);
        return all;
    }

    /** The settings in {@code settings}, each made placeable once, in order. */
    @SuppressWarnings("unchecked")
    private <S> void addPlaceable(KList<IrisProceduralPlacement> all, KList<S> settings, Function<S, ResolvedProceduralPlacement> resolve) {
        if (settings == null) {
            return;
        }
        for (S entry : settings) {
            all.add(placeables.computeIfAbsent(entry, key -> resolve.apply((S) key)));
        }
    }

    public boolean isEmpty() {
        return getAllPlacements().isEmpty();
    }
}
