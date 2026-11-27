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

package art.arcane.iris.command;

import art.arcane.iris.command.specialhandlers.IrisStructureHandler;
import art.arcane.iris.Iris;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.structure.BulkStructureImporter;
import art.arcane.iris.structure.StructureCaptureImporter;
import art.arcane.iris.structure.StructureImporter;
import art.arcane.iris.structure.StructureIndexService;
import art.arcane.iris.studio.object.ObjectSVC;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.structure.placement.IrisStructureLocator;
import art.arcane.iris.structure.nativegen.NativeStructureGenerationPolicy;
import art.arcane.iris.structure.placement.PlacedStructurePiece;
import art.arcane.iris.structure.placement.StructureAssembler;
import art.arcane.iris.structure.placement.StructureReachability;
import art.arcane.iris.structure.graph.StructureAssemblyResult;
import art.arcane.iris.structure.object.IObjectPlacer;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.structure.nativegen.IrisNativeStructureDecision;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.structure.placement.IrisStructure;
import art.arcane.iris.structure.nativegen.NativeStructureGenerationStatus;
import art.arcane.iris.structure.object.ObjectPlaceMode;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.iris.localization.C;
import art.arcane.iris.localization.ChatReport;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.iris.world.IrisToolbelt;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.iris.world.task.J;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.generator.structure.Structure;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.localization.BukkitCommandMessages;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.iris.localization.BukkitCommandMessagesExtended;
@Director(name = "structure", aliases = {"struct", "str"}, description = "Iris structure tools (index, import, info)", descriptionKey = "iris.director.commandstructure.director.iris_structure_tools_index_import_info")
public class CommandStructure implements DirectorExecutor {
    private static final int VERIFY_ITEM_LIMIT = 15;
    private static final int UNREACHABLE_BIOMES_SHOWN = 3;
    private static final String VANILLA_NAMESPACE = "minecraft:";
    @Director(description = "Regenerate structure-index.json listing all vanilla, datapack & iris structures", descriptionKey = "iris.director.commandstructure.director.regenerate_structure_index_json_listing_all_vanilla_datapack_iris_structures", aliases = {"ls"}, origin = DirectorOrigin.BOTH)
    public void list(
            @Param(description = "The dimension whose pack to index", descriptionKey = "iris.director.commandstructure.param.dimension_whose_pack_index", aliases = "dim")
            IrisDimension dimension
    ) {
        IrisData data = dimension.getLoader();
        if (data == null) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STRUCTURE_COULD_NOT_RESOLVE_PACK_DIMENSION, MessageArgument.untrusted("value", dimension.getLoadKey())));
            return;
        }
        File file = StructureIndexService.write(data);
        sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STRUCTURE_WROTE_STRUCTURE_INDEX, MessageArgument.untrusted("value", file.getPath())));
    }

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

    @Director(description = "Verify native structure eligibility and locate Iris-placed structures without running blocking native searches.", descriptionKey = "iris.director.commandstructure.director.verify_native_structure_eligibility_locate_iris_placed_structures_without_running_blocking_native", aliases = {"locateall"}, origin = DirectorOrigin.BOTH, sync = true)
    public void verify(
            @Param(description = "The dimension to verify", descriptionKey = "iris.director.commandstructure.param.dimension_verify", aliases = "dim")
            IrisDimension dimension,
            @Param(description = "Search radius in chunks around the origin (larger is much slower)", descriptionKey = "iris.director.commandstructure.param.search_radius_chunks_around_origin_larger_is_much_slower", defaultValue = "48")
            int radius
    ) {
        World world = resolveIrisWorld(dimension);
        if (world == null) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STRUCTURE_NO_LOADED_IRIS_WORLD_FOUND_JOIN_CREATE_ONE_FIRST_SEARCH, MessageArgument.untrusted("value", dimension.getLoadKey())));
            return;
        }
        boolean senderIsPlayer = sender() != null && sender().isPlayer();
        Location center = senderIsPlayer && player().getWorld() == world
                ? player().getLocation()
                : world.getSpawnLocation();
        int searchRadius = Math.max(1, Math.min(radius, 1000));
        PlatformChunkGenerator access = IrisToolbelt.access(world);
        Engine engine = access == null ? null : access.getEngine();
        if (engine == null) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STRUCTURE_SELECTED_IRIS_WORLD_HAS_NO_ACTIVE_GENERATOR_ENGINE));
            return;
        }
        KList<String> structureKeys = new KList<>();
        Registry<Structure> structureRegistry = Bukkit.getRegistry(Structure.class);
        if (structureRegistry != null) {
            for (Structure structure : structureRegistry) {
                NamespacedKey key = structureRegistry.getKey(structure);
                if (key != null) {
                    structureKeys.add(key.toString());
                }
            }
        }
        for (String placedKey : IrisStructureLocator.placedKeys(engine)) {
            if (!structureKeys.contains(placedKey)) {
                structureKeys.add(placedKey);
            }
        }
        VolmitSender commandSender = sender();
        Player target = senderIsPlayer ? player() : null;
        commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessages.COMMAND_STRUCTURE_VERIFYING_STRUCTURES_FROM_WITHIN_CHUNKS, MessageArgument.untrusted("value", world.getName()), MessageArgument.untrusted("value2", center.getBlockX()), MessageArgument.untrusted("value3", center.getBlockZ()), MessageArgument.untrusted("searchRadius", searchRadius)));
        int centerX = center.getBlockX();
        int centerZ = center.getBlockZ();
        J.a(() -> runVerification(engine, structureKeys, centerX, centerZ, searchRadius, commandSender, target));
    }

    private void runVerification(Engine engine, KList<String> structureKeys, int centerX, int centerZ,
                                 int searchRadius, VolmitSender commandSender, Player target) {
        Map<String, IrisNativeStructureDecision> decisions = new HashMap<>(structureKeys.size());
        boolean requiresNativeReachability = false;
        for (String keyName : structureKeys) {
            IrisNativeStructureDecision decision = NativeStructureGenerationPolicy.resolve(engine, keyName, false);
            decisions.put(keyName, decision);
            requiresNativeReachability |= !IrisStructureLocator.isPlaced(engine, keyName)
                    && decision.generate();
        }
        Set<String> reachable = Set.of();
        if (requiresNativeReachability) {
            try {
                reachable = StructureReachability.reachableKeys(engine);
            } catch (Throwable error) {
                sendVerificationReport(commandSender, target, ChatReport.of(ChatReport.Tone.ERROR,
                        "Structure verification could not resolve native biome reachability").build());
                Iris.reportError("Could not resolve native structure biome reachability for verification.", error);
                return;
            }
        }
        int located = 0;
        int nativeEligible = 0;
        int disabled = 0;
        int unreachable = 0;
        int searchLimited = 0;
        int errors = 0;
        List<String> problems = new ArrayList<>();
        List<String> planned = new ArrayList<>();
        List<String> unreachableLines = new ArrayList<>();
        for (String keyName : structureKeys) {
            IrisNativeStructureDecision decision = decisions.get(keyName);
            if (IrisStructureLocator.isPlaced(engine, keyName)) {
                try {
                    IrisStructureLocator.LocateResult result =
                            IrisStructureLocator.locate(engine, keyName, centerX, centerZ, searchRadius);
                    if (result.status() == IrisStructureLocator.LocateStatus.SEARCH_LIMIT_REACHED) {
                        searchLimited++;
                        problems.add(verifyLine(C.GOLD, keyName, "density search hit its safety limit"));
                        continue;
                    }
                    if (!result.found()) {
                        problems.add(verifyLine(C.GOLD, keyName, "not found in range"));
                        continue;
                    }
                    located++;
                    planned.add(verifyLine(C.IRIS, keyName,
                            result.originX() + ", " + result.baseY() + ", " + result.originZ()));
                } catch (Throwable error) {
                    errors++;
                    problems.add(verifyLine(C.RED, keyName, error.getClass().getSimpleName()));
                    Iris.reportError("Could not verify Iris-placed structure '" + keyName + "'.", error);
                }
                continue;
            }
            if (!decision.generate()) {
                disabled++;
                continue;
            }
            if (!reachable.contains(keyName.toLowerCase(Locale.ROOT))) {
                unreachable++;
                unreachableLines.add(unreachableLine(keyName, StructureReachability.missingBiomeKeys(engine, keyName)));
                continue;
            }
            nativeEligible++;
        }
        ChatReport.Tone tone = errors > 0 ? ChatReport.Tone.ERROR
                : problems.isEmpty() ? ChatReport.Tone.SUCCESS : ChatReport.Tone.WARNING;
        ChatReport.Builder report = ChatReport.of(tone, "Structure verify").limit(VERIFY_ITEM_LIMIT);
        report.styledItem(String.join(C.DARK_GRAY + " · ", List.of(
                        verifyCount(located, "planned", C.IRIS),
                        verifyCount(nativeEligible, "native", C.AQUA),
                        verifyCount(problems.size() - errors - searchLimited, "not found", C.GOLD),
                        verifyCount(searchLimited, "search-limited", C.GOLD),
                        verifyCount(errors, "errors", C.RED),
                        verifyCount(unreachable, "unreachable", C.GRAY),
                        verifyCount(disabled, "disabled", C.GRAY))
                .stream().filter(count -> !count.isEmpty()).toList()));
        problems.forEach(report::styledItem);
        planned.forEach(report::styledItem);
        unreachableLines.forEach(report::styledItem);
        report.hint("Unreachable: no biome in this dimension can place it. Native eligibility is checked without blocking locates.", null);
        sendVerificationReport(commandSender, target, report.build());
    }

    private static String verifyLine(C accent, String keyName, String detail) {
        return C.WHITE + structureName(keyName) + C.DARK_GRAY + "  " + accent + ChatReport.clean(detail);
    }

    private static String verifyCount(int count, String label, C color) {
        return count == 0 ? "" : color.toString() + count + C.GRAY + " " + label;
    }

    private static String unreachableLine(String keyName, List<String> missingBiomes) {
        String name = C.GRAY + structureName(keyName) + C.DARK_GRAY + "  ";
        if (missingBiomes.isEmpty()) {
            return name + "no biome can place it";
        }
        int shown = Math.min(UNREACHABLE_BIOMES_SHOWN, missingBiomes.size());
        String biomes = missingBiomes.subList(0, shown).stream()
                .map(CommandStructure::structureName)
                .collect(Collectors.joining(C.DARK_GRAY + ", " + C.GRAY, C.GRAY.toString(), ""));
        int hidden = missingBiomes.size() - shown;
        return name + "needs " + biomes + (hidden > 0 ? C.DARK_GRAY + " +" + hidden + " more" : "");
    }

    private static String structureName(String key) {
        String cleaned = ChatReport.clean(key);
        return cleaned.startsWith(VANILLA_NAMESPACE) ? cleaned.substring(VANILLA_NAMESPACE.length()) : cleaned;
    }

    private void sendVerificationReport(VolmitSender commandSender, Player target, ChatReport report) {
        Runnable send = () -> commandSender.sendReport(report);
        if (target != null) {
            J.runEntity(target, send);
        } else {
            J.s(send);
        }
    }

    private World resolveIrisWorld(IrisDimension dimension) {
        if (sender() != null && sender().isPlayer() && IrisToolbelt.isIrisWorld(player().getWorld())) {
            PlatformChunkGenerator playerGenerator = IrisToolbelt.access(player().getWorld());
            if (matchesDimension(playerGenerator, dimension.getLoadKey())) {
                return player().getWorld();
            }
        }
        for (World w : Bukkit.getWorlds()) {
            if (!IrisToolbelt.isIrisWorld(w)) {
                continue;
            }
            PlatformChunkGenerator gen = IrisToolbelt.access(w);
            if (matchesDimension(gen, dimension.getLoadKey())) {
                return w;
            }
        }
        return null;
    }

    static boolean matchesDimension(PlatformChunkGenerator generator, String dimensionKey) {
        return dimensionKey != null
                && generator != null
                && generator.getEngine() != null
                && generator.getEngine().getDimension() != null
                && dimensionKey.equals(generator.getEngine().getDimension().getLoadKey());
    }

    @Director(description = "Resolve an iris structure's jigsaw graph and report piece count & bounds", descriptionKey = "iris.director.commandstructure.director.resolve_iris_structure_s_jigsaw_graph_report_piece_count_bounds", origin = DirectorOrigin.BOTH)
    public void info(
            @Param(description = "The dimension whose pack holds the structure", descriptionKey = "iris.director.commandstructure.param.dimension_whose_pack_holds_structure", aliases = "dim")
            IrisDimension dimension,
            @Param(description = "The iris structure key to inspect", descriptionKey = "iris.director.commandstructure.param.iris_structure_key_inspect", customHandler = IrisStructureHandler.class)
            String structure
    ) {
        IrisData data = dimension.getLoader();
        if (data == null) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STRUCTURE_COULD_NOT_RESOLVE_PACK_DIMENSION_4, MessageArgument.untrusted("value", dimension.getLoadKey())));
            return;
        }
        IrisStructure s = data.load(IrisStructure.class, structure, false);
        if (s == null) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STRUCTURE_NO_IRIS_STRUCTURE_THIS_PACK, MessageArgument.untrusted("structure", structure)));
            return;
        }
        StructureAssembler assembler = StructureAssembler.forData(
                data, s, new IrisPosition(0, 64, 0));
        StructureAssemblyResult assembly = assembler.assemble(new RNG(1234));
        List<PlacedStructurePiece> pieces = assembly.pieces();
        if (!assembly.hasOutput()) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STRUCTURE_STRUCTURE_ASSEMBLED_0_PIECES_CHECK_STARTPOOL, MessageArgument.untrusted("structure", structure), MessageArgument.untrusted("value", s.getStartPool())));
            return;
        }
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (PlacedStructurePiece p : pieces) {
            minX = Math.min(minX, p.getMinX());
            minZ = Math.min(minZ, p.getMinZ());
            maxX = Math.max(maxX, p.getMaxX());
            maxZ = Math.max(maxZ, p.getMaxZ());
        }
        sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STRUCTURE_STRUCTURE_PIECES_FOOTPRINT_X_BLOCKS_SAMPLE_SEED_1234, MessageArgument.untrusted("structure", structure), MessageArgument.untrusted("value", pieces.size()), MessageArgument.untrusted("value2", (maxX - minX + 1)), MessageArgument.untrusted("value3", (maxZ - minZ + 1))));
    }

    @Director(description = "Assemble and place an iris structure at your location (studio testing)", descriptionKey = "iris.director.commandstructure.director.assemble_place_iris_structure_at_your_location_studio_testing", aliases = {"p"}, origin = DirectorOrigin.PLAYER, sync = true)
    public void place(
            @Param(description = "The dimension whose pack holds the structure", descriptionKey = "iris.director.commandstructure.param.dimension_whose_pack_holds_structure_2", aliases = "dim")
            IrisDimension dimension,
            @Param(description = "The iris structure key to place", descriptionKey = "iris.director.commandstructure.param.iris_structure_key_place", customHandler = IrisStructureHandler.class)
            String structure
    ) {
        IrisData data = dimension.getLoader();
        if (data == null) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STRUCTURE_COULD_NOT_RESOLVE_PACK_DIMENSION_5, MessageArgument.untrusted("value", dimension.getLoadKey())));
            return;
        }
        IrisStructure s = data.load(IrisStructure.class, structure, false);
        if (s == null) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STRUCTURE_NO_IRIS_STRUCTURE_THIS_PACK_2, MessageArgument.untrusted("structure", structure)));
            return;
        }
        Location loc = player().getLocation();
        StructureAssembler assembler = StructureAssembler.forData(
                data, s, new IrisPosition(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
        RNG rng = new RNG((long) loc.getBlockX() * 341873128712L + loc.getBlockZ());
        StructureAssemblyResult assembly = assembler.assemble(rng);
        List<PlacedStructurePiece> pieces = assembly.pieces();
        if (!assembly.hasOutput()) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STRUCTURE_STRUCTURE_ASSEMBLED_0_PIECES, MessageArgument.untrusted("structure", structure)));
            return;
        }
        Map<Block, BlockData> future = new HashMap<>();
        World targetWorld = player().getWorld();
        PlatformChunkGenerator targetGenerator = IrisToolbelt.access(targetWorld);
        Engine targetEngine = targetGenerator == null ? null : targetGenerator.getEngine();
        IObjectPlacer placer = CommandObject.createPlacer(targetWorld, future, targetEngine);
        for (PlacedStructurePiece p : pieces) {
            IrisObjectPlacement config = new IrisObjectPlacement();
            config.setMode(ObjectPlaceMode.STRUCTURE_PIECE);
            config.setRotation(p.getRotation());
            config.getPlace().add(p.getObject().getLoadKey());
            if (!s.getEdit().isEmpty()) {
                config.setEdit(s.getEdit());
            }
            p.getObject().place(p.getX(), p.getY(), p.getZ(), placer, config, rng, null, null, data);
        }
        J.runGlobal(() -> Iris.service(ObjectSVC.class).addChanges(future));
        int blockChanges = 0;
        for (Map.Entry<Block, BlockData> entry : future.entrySet()) {
            if (!entry.getKey().getBlockData().equals(entry.getValue())) {
                blockChanges++;
            }
        }
        if (blockChanges == 0) {
            sender().sendMessage(IrisLanguage.text(
                    BukkitCommandMessagesExtended.COMMAND_STRUCTURE_PLACEMENT_CHANGED_NO_BLOCKS,
                    MessageArgument.untrusted("structure", structure),
                    MessageArgument.untrusted("value", pieces.size())));
            return;
        }
        sender().sendMessage(IrisLanguage.text(
                BukkitCommandMessagesExtended.COMMAND_STRUCTURE_PLACED_PIECES_AT_YOUR_LOCATION,
                MessageArgument.untrusted("structure", structure),
                MessageArgument.untrusted("value", pieces.size()),
                MessageArgument.untrusted("value2", blockChanges)));
    }

}
