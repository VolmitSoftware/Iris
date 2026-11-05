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
package art.arcane.iris.engine.procedural.part;

import com.volmit.config.MinItems;
import com.volmit.config.Desc;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;

import java.util.Collections;
import java.util.List;

@Desc("A procedural object built from parts: stems, blobs, branches and clusters, each placing blocks of a role. Anything from a tree to a rock arch to a crystal-studded boulder. Iris bakes a pool of variants from it once. Place it from any object placement as procedural/<path>, or a single variant as procedural/<path>#<index>.")
public final class ProceduralObject {
    @MinNumber(1)
    @MaxNumber(64)
    @Desc("How many different variants to bake. More means more variety for a little memory.")
    private int variants = 6;

    @Desc("The seed every variant is baked from. The same seed and settings always bake the same variants.")
    private long seed = 1337;


    @MinItems(1)
    @Desc("The blocks of each role. A part whose role has no material uses the first one.")
    private List<ProceduralMaterial> materials = List.of();

    @MinItems(1)
    @Desc("The parts the object grows from, each starting at the object's base.")
    private List<ProceduralPart> parts = List.of();

    @MinItems(1)
    @Desc("Blocks placed against the finished object, such as vines, moss or fruit.")
    private List<ProceduralDecoration> decorations = List.of();

    @Desc("If true leaves get the distances from wood vanilla needs to keep them. Leaves more than 6 blocks from wood are made persistent; the object view in /iris studio map shows them. If false all leaves are persistent.")
    private boolean leafDecay = true;

    @Desc("If true every block that can hold water is waterlogged, so coral stays alive.")
    private boolean waterlogged = false;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Sinks the object into the ground by this fraction of its height.")
    private double sink = 0;

    public int variants() {
        return variants;
    }

    public long seed() {
        return seed;
    }

    public List<ProceduralMaterial> materials() {
        return materials == null ? List.of() : Collections.unmodifiableList(materials);
    }

    /** The role of a root part that sets none: the first material's. */
    public String defaultRole() {
        return materials().isEmpty() ? "main" : materials().getFirst().role();
    }

    public List<ProceduralPart> parts() {
        return parts == null ? List.of() : Collections.unmodifiableList(parts);
    }

    public List<ProceduralDecoration> decorations() {
        return decorations == null ? List.of() : Collections.unmodifiableList(decorations);
    }

    public boolean leafDecay() {
        return leafDecay;
    }

    public boolean waterlogged() {
        return waterlogged;
    }

    public double sink() {
        return sink;
    }
}
