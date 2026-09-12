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

import art.arcane.iris.Iris;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.structure.BulkStructureImporter;
import art.arcane.iris.structure.StructureCaptureImporter;
import art.arcane.iris.structure.StructureImporter;
import art.arcane.iris.structure.StructureIndexService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.localization.BukkitCommandMessages;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.iris.localization.BukkitCommandMessagesExtended;
@Director(name = "structure", aliases = {"struct", "str"}, description = "Iris structure tools (index, import, info)", descriptionKey = "iris.director.commandstructure.director.iris_structure_tools_index_import_info")
public class CommandStructure implements DirectorExecutor {
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
        KList<String> messages = new KList<>();
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
                messages.add(C.RED + "Structure verification could not resolve native biome reachability.");
                sendVerificationMessages(commandSender, target, messages);
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
        for (String keyName : structureKeys) {
            IrisNativeStructureDecision decision = decisions.get(keyName);
            if (IrisStructureLocator.isPlaced(engine, keyName)) {
                try {
                    IrisStructureLocator.LocateResult result =
                            IrisStructureLocator.locate(engine, keyName, centerX, centerZ, searchRadius);
                    if (result.status() == IrisStructureLocator.LocateStatus.SEARCH_LIMIT_REACHED) {
                        searchLimited++;
                        messages.add(C.YELLOW + "[iris-search-limit] " + C.WHITE + keyName + C.YELLOW
                                + ": unable to complete the density search before its safety limit was reached");
                        continue;
                    }
                    if (!result.found()) {
                        messages.add(C.YELLOW + "[iris-not-found] " + C.WHITE + keyName);
                        continue;
                    }
                    located++;
                    messages.add(C.AQUA + "[iris-planned] " + C.WHITE + keyName + C.GREEN + " @ "
                            + result.originX() + "," + result.baseY() + "," + result.originZ());
                } catch (Throwable error) {
                    errors++;
                    messages.add(C.RED + "[error] " + C.WHITE + keyName + C.RED + ": "
                            + error.getClass().getSimpleName());
                    Iris.reportError("Could not verify Iris-placed structure '" + keyName + "'.", error);
                }
                continue;
            }
            if (!decision.generate()) {
                disabled++;
                messages.add(C.GRAY + "[disabled] " + C.WHITE + keyName);
                continue;
            }
            if (!reachable.contains(keyName.toLowerCase(Locale.ROOT))) {
                unreachable++;
                KList<String> missing = StructureReachability.missingBiomeKeys(engine, keyName);
                messages.add(C.YELLOW + "[unreachable] " + C.WHITE + keyName
                        + (missing.isEmpty() ? "" : C.YELLOW + " needs " + String.join("/", missing)));
                continue;
            }
            nativeEligible++;
            messages.add(C.GREEN + "[native-eligible] " + C.WHITE + keyName);
        }
        messages.add(C.GREEN + "Structure verify: " + C.WHITE + located + C.GREEN + " Iris placement plans found, "
                + C.WHITE + nativeEligible + C.GREEN + " native structures eligible, "
                + C.WHITE + disabled + C.GREEN + " disabled by policy, "
                + C.WHITE + unreachable + C.GREEN + " biome-unreachable, "
                + C.WHITE + searchLimited + C.GREEN + " density searches safety-limited, "
                + C.WHITE + errors + C.GREEN + " errors. Native eligibility is checked without running blocking live locates.");
        sendVerificationMessages(commandSender, target, messages);
    }

    private void sendVerificationMessages(VolmitSender commandSender, Player target,
                                          KList<String> messages) {
        Runnable send = () -> {
            for (String message : messages) {
                commandSender.sendMessage(message);
            }
        };
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
            @Param(description = "The iris structure key to inspect", descriptionKey = "iris.director.commandstructure.param.iris_structure_key_inspect")
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
            @Param(description = "The iris structure key to place", descriptionKey = "iris.director.commandstructure.param.iris_structure_key_place")
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
