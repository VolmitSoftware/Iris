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

package art.arcane.iris.core.commands;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.structure.BulkStructureImporter;
import art.arcane.iris.core.structure.StructureCaptureImporter;
import art.arcane.iris.core.structure.StructureImporter;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.util.common.director.DirectorExecutor;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;

import art.arcane.iris.core.localization.IrisLanguage;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.iris.core.localization.BukkitCommandMessagesExtended;
@Director(name = "structure", aliases = {"struct", "str"}, description = "Iris structure tools (index, import, info)", descriptionKey = "iris.director.commandstructure.director.iris_structure_tools_index_import_info")
public class CommandStructure implements DirectorExecutor {
    @Director(name = "import", description = "Import EVERY structure - vanilla AND ingested datapacks - into this pack as editable Iris resources, always overwriting. Rebuilds jigsaw structures (villages, outposts, datapack jigsaws) as editable pool/piece graphs, imports every structure template NBT as an object, and assembles the multi-template structures (shipwrecks, ruined portals, ocean ruins, nether fossils). Run after ingesting a datapack and restarting. Regenerate chunks or use a fresh world for the imported copies to place.", descriptionKey = "iris.director.commandstructure.director.import_every_structure_vanilla_ingested_datapacks_into_this_pack_as_editable_iris", aliases = {"import-all", "reimport", "imp", "all"}, origin = DirectorOrigin.BOTH)
    public void importAll(
            @Param(description = "The dimension whose pack to import into", descriptionKey = "iris.director.commandstructure.param.dimension_whose_pack_import_into", aliases = "dim")
            IrisDimension dimension
    ) {
        IrisData data = dimension.getLoader();
        if (data == null) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STRUCTURE_COULD_NOT_RESOLVE_PACK_DIMENSION_2, MessageArgument.untrusted("value", dimension.getLoadKey())));
            return;
        }
        sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STRUCTURE_IMPORTING_ALL_VANILLA_DATAPACK_STRUCTURES_INTO_OVERWRITE, MessageArgument.untrusted("value", dimension.getLoadKey())));
        BulkStructureImporter.Report jigsaws = BulkStructureImporter.importAllVanilla(data, StructureImporter.Mode.OVERWRITE, true, sender());
        BulkStructureImporter.Report templates = BulkStructureImporter.importAllTemplates(data, StructureImporter.Mode.OVERWRITE, sender());
        BulkStructureImporter.Report groups = BulkStructureImporter.importTemplateGroups(data, StructureImporter.Mode.OVERWRITE, sender());
        StructureCaptureImporter.Report captured = StructureCaptureImporter.importStructures(
                data, StructureImporter.Mode.OVERWRITE, sender(), jigsaws.captureCandidates());
        int imported = jigsaws.imported() + templates.imported() + groups.imported() + captured.imported();
        int failed = jigsaws.failed() + templates.failed() + groups.failed() + captured.failed();
        sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STRUCTURE_IMPORT_COMPLETE_STRUCTURES_OBJECTS_WRITTEN_FAILED, MessageArgument.untrusted("imported", imported), MessageArgument.untrusted("failed", failed)));
        sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STRUCTURE_REFERENCE_THEM_FROM_BIOME_REGION_DIMENSION_STRUCTURES_LIST_RUN_IRIS, MessageArgument.untrusted("value", dimension.getLoadKey())));
    }

    @Director(name = "capture", description = "Capture live registered structures into editable Iris objects by generating each one in a throwaway scratch world and reading back its blocks. This standalone command overwrites its owned outputs; structures wider/taller than the capture cap and anything that will not generate in a flat overworld are skipped. Each captured structure becomes a single-piece Iris structure you can place from a biome/region/dimension 'structures' list. /iris structure import runs a restricted final capture pass only for non-jigsaw structures that have no loadable NBT template.", descriptionKey = "iris.director.commandstructure.director.capture_code_generated_structures_that_have_no_nbt_template_swamp_huts_igloos", aliases = {"cap"}, origin = DirectorOrigin.BOTH)
    public void capture(
            @Param(description = "The dimension whose pack to capture into", descriptionKey = "iris.director.commandstructure.param.dimension_whose_pack_capture_into", aliases = "dim")
            IrisDimension dimension
    ) {
        IrisData data = dimension.getLoader();
        if (data == null) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STRUCTURE_COULD_NOT_RESOLVE_PACK_DIMENSION_3, MessageArgument.untrusted("value", dimension.getLoadKey())));
            return;
        }
        StructureCaptureImporter.Report report = StructureCaptureImporter.importAllStructures(data, StructureImporter.Mode.OVERWRITE, sender());
        sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STRUCTURE_CAPTURED_STRUCTURES_PLACE_THEM_FROM_STRUCTURES_LIST_REGENERATE_CHUNKS_DELETE, MessageArgument.untrusted("value", report.imported())));
    }
}
