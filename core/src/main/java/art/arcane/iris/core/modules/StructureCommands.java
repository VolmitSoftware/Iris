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

import art.arcane.iris.core.structure.StructureIndexService;
import art.arcane.iris.core.tooling.IrisToolPack;
import art.arcane.iris.engine.framework.PlacedStructurePiece;
import art.arcane.iris.engine.framework.structure.StructureAssemblyResult;
import art.arcane.iris.engine.framework.structure.StructureResources;
import art.arcane.iris.engine.object.structure.IrisStructure;
import art.arcane.iris.engine.object.value.IrisPosition;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.resource.ResourceId;
import com.volmit.tooling.Argument;
import com.volmit.tooling.Command;
import com.volmit.tooling.CommandContext;

import java.io.File;
import java.io.IOException;
import java.util.List;

/** The {@code /iris structure} commands of {@link StructureModelModule}: the index, one structure's graph, and the verification of a world's structures. */
final class StructureCommands {
    private static final String ROOT_NAME = "structure";
    private static final long INFO_SAMPLE_SEED = 1234L;
    private static final int INFO_ORIGIN_Y = 64;

    private static final Argument<ResourceId> STRUCTURE = Argument.resource("structure", StructureResources.STRUCTURES)
            .describe("The Iris structure to inspect");

    private StructureCommands() {
    }

    static Command root() {
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
                        .argument(StructureVerification.RADIUS)
                        .runs(StructureVerification::run));
    }

    private static void list(CommandContext context) throws IOException {
        File index = context.pack().edit(packRoot -> StructureIndexService.write(IrisToolPack.dataOf(context.pack())));
        context.sender().success("Wrote the structure index: " + index.getPath());
    }

    private static void info(CommandContext context) {
        ResourceId id = context.get(STRUCTURE);
        IrisStructure structure = context.pack().resources().require(StructureResources.STRUCTURES, id);
        StructureAssemblyResult assembly = StructureAssemblies
                .forData(IrisToolPack.dataOf(context.pack()), structure, new IrisPosition(0, INFO_ORIGIN_Y, 0))
                .assemble(new RNG(INFO_SAMPLE_SEED));
        if (!assembly.hasOutput()) {
            context.sender().error("Structure " + id + " assembled 0 pieces (check startPool '" + structure.getStartPool() + "')");
            return;
        }
        List<PlacedStructurePiece> pieces = assembly.pieces();
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (PlacedStructurePiece piece : pieces) {
            minX = Math.min(minX, piece.getMinX());
            minZ = Math.min(minZ, piece.getMinZ());
            maxX = Math.max(maxX, piece.getMaxX());
            maxZ = Math.max(maxZ, piece.getMaxZ());
        }
        context.sender().success("Structure " + id + ": " + pieces.size() + " pieces, footprint " + (maxX - minX + 1) + "x"
                + (maxZ - minZ + 1) + " blocks (sample seed " + INFO_SAMPLE_SEED + ")");
    }
}
