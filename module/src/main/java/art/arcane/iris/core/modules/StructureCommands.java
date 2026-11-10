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

package art.arcane.iris.core.modules;

import art.arcane.iris.builtin.structure.StructureVerifier;
import art.arcane.iris.core.structure.authoring.StructureIndexService;
import art.arcane.iris.core.structure.tooling.PlatformStructuresAccess;
import art.arcane.iris.engine.framework.structure.assembly.AssembledPiece;
import art.arcane.iris.engine.framework.structure.assembly.Assembly;
import art.arcane.iris.engine.framework.structure.assembly.StructureAssembly;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.geometry.BlockPosition;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.tooling.Argument;
import com.volmit.tooling.Command;
import com.volmit.tooling.CommandContext;
import com.volmit.tooling.PlatformStructures;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** The {@code /iris module structure} commands of {@code iris:structure}: the index, one structure's graph, and the verification of a world's structures. */
public final class StructureCommands {
    private static final String ROOT_NAME = "structure";
    private static final long INFO_SAMPLE_SEED = 1234L;
    private static final int INFO_ORIGIN_Y = 64;
    private static final long DEFAULT_RADIUS_CHUNKS = 48L;
    private static final long MAX_RADIUS_CHUNKS = 1000L;
    private static final char NAMESPACE_SEPARATOR = ':';
    private static final String STRUCTURES_FOLDER = "structures";

    private static final Argument<ResourceId> STRUCTURE = Argument.reading("structure", StructureCommands::readStructure)
            .describe("The Iris structure to inspect")
            .suggesting(StructureCommands::structureKeys);
    private static final Argument<Long> RADIUS = Argument.integer("radius", 1, MAX_RADIUS_CHUNKS)
            .orElse(DEFAULT_RADIUS_CHUNKS)
            .describe("Search radius in chunks around the world origin (larger is much slower)");

    private StructureCommands() {
    }

    public static Command root() {
        return Command.named(ROOT_NAME).alias("struct", "str")
                .describe("Iris structure tools")
                .child(Command.named("list").alias("ls")
                        .describe("Write the index of every vanilla, datapack and Iris structure of the pack")
                        .runs(StructureCommands::list))
                .child(Command.named("info")
                        .describe("Resolve an Iris structure's jigsaw graph and report its piece count and footprint")
                        .argument(STRUCTURE)
                        .runs(StructureCommands::info))
                .child(Command.named("verify").alias("locateall")
                        .describe("Verify native structure eligibility and locate Iris-placed structures in the world you stand in")
                        .argument(RADIUS)
                        .runs(StructureCommands::verify));
    }

    private static void list(CommandContext context) throws IOException {
        Optional<PlatformStructures> platform = PlatformStructuresAccess.require(context);
        if (platform.isEmpty()) {
            return;
        }
        File index = StructureIndexService.write(context.pack(), platform.get());
        context.sender().success("Wrote the structure index: " + index.getPath());
    }

    private static void info(CommandContext context) {
        ResourceId id = context.get(STRUCTURE);
        Assembly assembly = sampleAssembly(context, id).orElseThrow();
        if (!assembly.hasOutput()) {
            context.sender().error("Structure " + id + " assembled 0 pieces" + (assembly.detail().isBlank() ? "" : " (" + assembly.detail() + ")"));
            return;
        }
        List<AssembledPiece> pieces = assembly.pieces();
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (AssembledPiece piece : pieces) {
            minX = Math.min(minX, piece.minX());
            minZ = Math.min(minZ, piece.minZ());
            maxX = Math.max(maxX, piece.maxX());
            maxZ = Math.max(maxZ, piece.maxZ());
        }
        context.sender().success("Structure " + id + ": " + pieces.size() + " pieces, footprint " + (maxX - minX + 1) + "x"
                + (maxZ - minZ + 1) + " blocks (sample seed " + INFO_SAMPLE_SEED + ")");
    }

    private static Optional<Assembly> sampleAssembly(CommandContext context, ResourceId id) {
        PackResources resources = context.pack().resources();
        BlockPosition origin = new BlockPosition(0, INFO_ORIGIN_Y, 0);
        for (StructureAssembly provider : context.pack().services(StructureAssembly.class)) {
            Optional<Assembly> assembly = provider.assemble(resources, id, origin, new RNG(INFO_SAMPLE_SEED));
            if (assembly.isPresent()) {
                return assembly;
            }
        }
        return Optional.empty();
    }

    private static void verify(CommandContext context) {
        Optional<StructureVerifier> verifier = context.world().flatMap(world -> world.extension(StructureVerifier.class));
        if (verifier.isEmpty()) {
            context.sender().error("Stand in a world generated with this pack: verify reads the running world");
            return;
        }
        verifier.get().verify(context, Math.toIntExact(context.get(RADIUS)));
    }

    private static ResourceId readStructure(String written, CommandContext context) {
        PackResources resources = context.pack().resources();
        ResourceId id = written.indexOf(NAMESPACE_SEPARATOR) < 0 ? resources.idOf(written) : ResourceId.parse(written);
        boolean known = context.pack().services(StructureAssembly.class).stream().anyMatch(provider -> provider.knows(resources, id));
        if (!known) {
            throw new IllegalArgumentException("No " + STRUCTURES_FOLDER + " resource '" + written + "' in this pack");
        }
        return id;
    }

    private static List<String> structureKeys(CommandContext context) {
        PackResources resources = context.pack().resources();
        Set<String> keys = new LinkedHashSet<>();
        for (StructureAssembly provider : context.pack().services(StructureAssembly.class)) {
            provider.structures(resources).forEach(id -> keys.add(resources.idOf(id.path()).equals(id) ? id.path() : id.toString()));
        }
        return List.copyOf(keys);
    }
}
