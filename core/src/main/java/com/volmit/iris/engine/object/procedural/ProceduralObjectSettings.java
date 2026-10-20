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

package com.volmit.iris.engine.object.procedural;

import com.volmit.iris.engine.object.procedural.coral.Coral;
import com.volmit.iris.engine.object.procedural.crystal.Crystal;
import com.volmit.iris.engine.object.procedural.formation.Formation;
import com.volmit.iris.engine.object.procedural.fungi.Fungus;
import com.volmit.iris.engine.object.procedural.ruin.Ruin;
import com.volmit.iris.engine.object.procedural.tree.Tree;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.object.annotations.ArrayType;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.Snippet;

import java.util.Collections;
import java.util.List;

/**
 * The procedural objects a region or biome declares, written as {@code "proceduralObjects": {"trees": [...], ...}}: things
 * Iris generates from scratch at world-gen time and places like object placements.
 */
@Snippet(ProceduralObjectCatalog.SET_FOLDER)
@Desc("Procedurally generated objects placed in a biome or region. Unlike the objects block (which loads iob files), these are generated from scratch at world-gen time.")
public final class ProceduralObjectSettings {
    private final transient AtomicCache<List<ResolvedProceduralPlacement>> resolved = new AtomicCache<>();

    @ArrayType(min = 1, type = Tree.class)
    @Desc("Procedurally generated trees.")
    private List<Tree> trees = List.of();

    @ArrayType(min = 1, type = Ruin.class)
    @Desc("Procedurally generated ruins (crumbling pillars, walls, arches, rubble).")
    private List<Ruin> ruins = List.of();

    @ArrayType(min = 1, type = Formation.class)
    @Desc("Procedurally generated natural rock formations (spires, hoodoos, arches, sea stacks, boulders, basalt columns).")
    private List<Formation> formations = List.of();

    @ArrayType(min = 1, type = Coral.class)
    @Desc("Procedurally generated underwater coral structures.")
    private List<Coral> coral = List.of();

    @ArrayType(min = 1, type = Fungus.class)
    @Desc("Procedurally generated mushrooms and shelf fungi.")
    private List<Fungus> fungi = List.of();

    @ArrayType(min = 1, type = Crystal.class)
    @Desc("Procedurally generated crystal clusters (usually placed in caves).")
    private List<Crystal> crystals = List.of();

    public ProceduralObjectSettings() {
    }

    /** Every procedural object declared here, made placeable through the pack's catalog. */
    public List<ResolvedProceduralPlacement> resolved(IrisData data) {
        return resolved.aquire(() -> data.getProceduralObjects().resolve(this));
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
