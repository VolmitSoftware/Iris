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

package art.arcane.iris.engine.framework.structure;

import art.arcane.iris.engine.framework.PlacedStructurePiece;
import art.arcane.iris.engine.framework.StructureAssembler;
import art.arcane.iris.engine.framework.structure.assembly.AssembledPiece;
import art.arcane.iris.engine.framework.structure.assembly.Assembly;
import art.arcane.iris.engine.framework.structure.assembly.StructureAssembly;
import art.arcane.iris.engine.framework.structure.assembly.StructureStamp;
import art.arcane.iris.engine.framework.structure.assembly.Viability;
import art.arcane.iris.engine.object.structure.IrisStructure;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.ModuleLog;
import com.volmit.geometry.BlockPosition;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * How {@code iris:jigsaw} builds the structures of the pack's {@code structures} folder: it assembles them from their
 * jigsaw pools and pieces ({@link StructureAssembler}) and tells the placement how they are stamped. It knows exactly the
 * structures the pack has a structure file for.
 */
public final class JigsawStructureAssembly implements StructureAssembly {
    private final ModuleLog log;

    public JigsawStructureAssembly(ModuleLog log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean knows(PackResources pack, ResourceId structure) {
        return structureOf(pack, structure) != null;
    }

    @Override
    public Set<ResourceId> structures(PackResources pack) {
        Set<ResourceId> known = new LinkedHashSet<>();
        for (String key : StructureResources.structureKeys(pack)) {
            known.add(pack.idOf(key));
        }
        return known;
    }

    @Override
    public OptionalInt reachChunks(PackResources pack, ResourceId structure) {
        IrisStructure known = structureOf(pack, structure);
        return known == null ? OptionalInt.empty() : OptionalInt.of(known.getMaxSizeChunks());
    }

    @Override
    public Optional<StructureStamp> stamp(PackResources pack, ResourceId structure) {
        IrisStructure known = structureOf(pack, structure);
        if (known == null) {
            return Optional.empty();
        }
        List<String> loot = known.getLoot() == null ? List.of()
                : known.getLoot().stream().filter(table -> table != null && !table.isBlank()).toList();
        return Optional.of(new StructureStamp(known.getPlaceMode(), known.getEdit(), loot,
                known.getVanillaSource() == null ? "" : known.getVanillaSource()));
    }

    @Override
    public Viability viability(PackResources pack, ResourceId structure) {
        IrisStructure known = structureOf(pack, structure);
        if (known == null) {
            return Viability.NOT_VIABLE;
        }
        String key = structure.path();
        if (!StructureGraphCatalog.compile(pack, key, known, log).isAssemblyViable()) {
            return Viability.NOT_VIABLE;
        }
        return StructureGraphCatalog.guaranteesRuntimeOutput(pack, key, known, log) ? Viability.GUARANTEED : Viability.VIABLE;
    }

    @Override
    public Optional<Assembly> assemble(PackResources pack, ResourceId structure, BlockPosition origin, RNG random) {
        IrisStructure known = structureOf(pack, structure);
        if (known == null) {
            return Optional.empty();
        }
        StructureAssemblyResult result = StructureAssembler.forPack(pack, structure.path(), known, origin, log).assemble(random);
        return Optional.of(new Assembly(result.status(), result.detail(), result.pieces().stream()
                .map(piece -> assembled(pack, piece)).toList()));
    }

    private static AssembledPiece assembled(PackResources pack, PlacedStructurePiece piece) {
        return new AssembledPiece(pack.idOf(piece.getObjectKey()), piece.getX(), piece.getY(), piece.getZ(), piece.getRotation(),
                piece.getMinX(), piece.getMinY(), piece.getMinZ(), piece.getMaxX(), piece.getMaxY(), piece.getMaxZ());
    }

    private static IrisStructure structureOf(PackResources pack, ResourceId structure) {
        return StructureResources.structure(pack, structure.path());
    }
}
