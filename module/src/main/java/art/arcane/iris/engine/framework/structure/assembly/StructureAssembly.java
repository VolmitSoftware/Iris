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

package art.arcane.iris.engine.framework.structure.assembly;

import art.arcane.volmlib.util.math.RNG;
import com.volmit.geometry.BlockPosition;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.service.Providers;
import com.volmit.service.Service;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Builds the pieces of a structure the pack names; the placement decides where they go and fits them to the terrain.
 * Every method answers empty, or {@link Viability#NOT_VIABLE}, for a structure this provider does not know, so a module
 * offering another kind of structure answers only for its own. The placement asks the first provider that knows it.
 */
@Service(Providers.FIRST)
public interface StructureAssembly {
    /** Whether this provider knows {@code structure}. Validation and the commands ask this. */
    boolean knows(PackResources pack, ResourceId structure);

    /** Every structure of the pack this provider knows, in id order. */
    Set<ResourceId> structures(PackResources pack);

    /** How far, in chunks from its start, the structure may reach; empty when this provider does not know it. */
    OptionalInt reachChunks(PackResources pack, ResourceId structure);

    /** How the pieces are stamped; empty when this provider does not know it. */
    Optional<StructureStamp> stamp(PackResources pack, ResourceId structure);

    /** Whether the structure can produce pieces at all. */
    Viability viability(PackResources pack, ResourceId structure);

    /**
     * The pieces from {@code origin}, drawing from {@code random}; empty when this provider does not know the structure.
     * The placement draws its origin, anchor and structure from the same random before this continues on it, so the
     * draws stay in one order whoever assembles.
     */
    Optional<Assembly> assemble(PackResources pack, ResourceId structure, BlockPosition origin, RNG random);
}
