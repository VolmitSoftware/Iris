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

package art.arcane.iris.core.structure.tooling;

import art.arcane.iris.core.structure.BulkStructureImporter;
import art.arcane.iris.core.structure.StructureCaptureImporter;
import art.arcane.iris.core.structure.StructureImporter;
import com.volmit.tooling.Command;
import com.volmit.tooling.CommandContext;
import com.volmit.tooling.PlatformStructures;
import com.volmit.tooling.Sender;
import com.volmit.tooling.ToolPack;
import com.volmit.tooling.world.TextColor;
import com.volmit.tooling.world.ToolText;

import java.util.Optional;

/** {@code import} and {@code capture}: turn the platform's structures into editable jigsaw structures of the pack. */
final class ImportCommands {
    private ImportCommands() {
    }

    static Command importAll() {
        return Command.named("import").alias("import-all", "reimport", "imp", "all")
                .describe("Import EVERY structure - vanilla AND ingested datapacks - into this pack as editable Iris resources, always overwriting. Rebuilds jigsaw structures (villages, outposts, datapack jigsaws) as editable pool/piece graphs, imports every structure template NBT as an object, and assembles the multi-template structures (shipwrecks, ruined portals, ocean ruins, nether fossils). Run after ingesting a datapack and restarting. Regenerate chunks or use a fresh world for the imported copies to place.")
                .runs(ImportCommands::runImportAll);
    }

    static Command capture() {
        return Command.named("capture").alias("cap")
                .describe("Capture live registered structures into editable Iris objects by generating each one in a throwaway scratch world and reading back its blocks. This standalone command overwrites its owned outputs; structures wider/taller than the capture cap and anything that will not generate in a flat overworld are skipped. Each captured structure becomes a single-piece Iris structure you can place from a biome/region/dimension 'structures' list. /iris jigsaw import runs a restricted final capture pass only for non-jigsaw structures that have no loadable NBT template.")
                .runs(ImportCommands::runCapture);
    }

    private static void runImportAll(CommandContext context) {
        Optional<PlatformStructures> platform = PlatformStructuresAccess.require(context);
        if (platform.isEmpty()) {
            return;
        }
        ToolPack pack = context.pack();
        Sender sender = context.sender();
        SenderText.send(sender, ToolText.builder()
                .green("Importing all vanilla & datapack structures into ")
                .white(pack.resources().packId().namespace())
                .green(" (overwrite)...").build());
        BulkStructureImporter.Report jigsaws = BulkStructureImporter.importAllVanilla(
                pack, platform.get(), StructureImporter.Mode.OVERWRITE, true, sender);
        BulkStructureImporter.Report templates = BulkStructureImporter.importAllTemplates(
                pack, platform.get(), StructureImporter.Mode.OVERWRITE, sender);
        BulkStructureImporter.Report groups = BulkStructureImporter.importTemplateGroups(
                pack, platform.get(), StructureImporter.Mode.OVERWRITE, sender);
        StructureCaptureImporter.Report captured = StructureCaptureImporter.importStructures(
                pack, platform.get(), StructureImporter.Mode.OVERWRITE, sender, jigsaws.captureCandidates());
        int imported = jigsaws.imported() + templates.imported() + groups.imported() + captured.imported();
        int failed = jigsaws.failed() + templates.failed() + groups.failed() + captured.failed();
        SenderText.send(sender, ToolText.builder()
                .green("Import complete: ").white(String.valueOf(imported))
                .green(" structures/objects written, ").white(String.valueOf(failed))
                .green(" failed.").build());
        SenderText.send(sender, TextColor.GRAY,
                "Reference them from a biome/region/dimension 'structures' list, or run /iris module structure list to refresh the index. Regenerate chunks for changes to take effect.");
    }

    private static void runCapture(CommandContext context) {
        Optional<PlatformStructures> platform = PlatformStructuresAccess.require(context);
        if (platform.isEmpty()) {
            return;
        }
        StructureCaptureImporter.Report report = StructureCaptureImporter.importAllStructures(
                context.pack(), platform.get(), StructureImporter.Mode.OVERWRITE, context.sender());
        SenderText.send(context.sender(), TextColor.GRAY, "Captured " + report.imported()
                + " structures. Place them from a 'structures' list and regenerate chunks. Delete a structures/*.json to re-capture it.");
    }
}
