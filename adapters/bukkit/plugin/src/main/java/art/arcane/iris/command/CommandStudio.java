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
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.studio.view.ImageMapStudioGUI;
import art.arcane.iris.studio.view.NoiseExplorerGUI;
import art.arcane.iris.studio.view.VisionGUI;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.studio.workspace.IrisProject;
import art.arcane.iris.studio.workspace.IrisCodeWorkspace;
import art.arcane.iris.localization.RuntimeUiMessages;
import art.arcane.iris.platform.bukkit.BoardSVC;
import art.arcane.iris.studio.StudioSVC;
import art.arcane.iris.structure.BulkStructureImporter;
import art.arcane.iris.structure.FeatureImporter;
import art.arcane.iris.structure.StructureImporter;
import art.arcane.iris.world.IrisToolbelt;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.world.loot.InventorySlotType;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomePaletteLayer;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.world.entity.IrisEntity;
import art.arcane.iris.generation.noise.IrisGenerator;
import art.arcane.iris.generation.noise.IrisInterpolator;
import art.arcane.iris.world.loot.IrisLootTable;
import art.arcane.iris.generation.noise.IrisNoiseGenerator;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.platform.generation.EngineBukkitOps;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.collection.KSet;
import art.arcane.iris.command.handlers.DimensionHandler;
import art.arcane.iris.command.specialhandlers.NullableDimensionHandler;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.iris.localization.C;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.function.NoiseProvider;
import art.arcane.volmlib.util.interpolation.InterpolationMethod;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.math.Spiraler;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.iris.generation.concurrent.MultiBurst;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.scheduling.O;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.localization.BukkitCommandMessages;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.iris.localization.BukkitCommandMessagesExtended;
@Director(name = "studio", aliases = {"std", "s"}, description = "Studio Commands", descriptionKey = "iris.director.commandstudio.director.studio_commands")
public class CommandStudio implements DirectorExecutor {
    private static final long CHUNK_SCAN_TIMEOUT_MS = 3_000;
    private CommandEdit edit;
    //private CommandDeepSearch deepSearch;

    public static String hrf(Duration duration) {
        return duration.toString().substring(2).replaceAll("(\\d[HMS])(?!$)", "$1 ").toLowerCase();
    }

    @Director(description = "Open a new studio world", descriptionKey = "iris.director.commandstudio.director.open_new_studio_world", aliases = "o", sync = true)
    public void open(
            @Param(description = "The dimension pack to open a studio for", descriptionKey = "iris.director.commandstudio.param.dimension_pack_open_studio", aliases = "dim", customHandler = DimensionHandler.class)
            IrisDimension dimension,
            @Param(defaultValue = "1337", description = "The seed to generate the studio with", descriptionKey = "iris.director.commandstudio.param.seed_generate_studio_with", aliases = "s")
            long seed,
            @Param(defaultValue = "false", description = "Attempt to open Studio before a required registry restart", aliases = "f")
            boolean force) {
        sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STUDIO_OPENING_STUDIO_PACK_SEED, MessageArgument.untrusted("value", dimension.getName()), MessageArgument.untrusted("seed", seed)));
        Iris.service(StudioSVC.class).open(sender(), seed, dimension.getLoadKey(), force);
    }

    @Director(description = "Import vanilla trees, mushrooms & objects (and structures/jigsaw) from the server into a pack's objects/vanilla folder", descriptionKey = "iris.director.commandstudio.director.import_vanilla_trees_mushrooms_objects_structures_jigsaw_from_server_into_pack_s", aliases = {"importv", "iv"}, origin = DirectorOrigin.BOTH)
    public void importvanilla(
            @Param(description = "The dimension pack to import vanilla content into", descriptionKey = "iris.director.commandstudio.param.dimension_pack_import_vanilla_content_into", aliases = {"pack", "dim"}, customHandler = DimensionHandler.class)
            IrisDimension dimension,
            @Param(description = "How many variants to capture per tree/object feature", descriptionKey = "iris.director.commandstudio.param.how_many_variants_capture_per_tree_object_feature", defaultValue = "3")
            int variants,
            @Param(description = "Also import vanilla & datapack structures/jigsaw into the pack", descriptionKey = "iris.director.commandstudio.param.also_import_vanilla_datapack_structures_jigsaw_into_pack", defaultValue = "true")
            boolean structures
    ) {
        if (dimension == null) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STUDIO_PROVIDE_DIMENSION_PACK_IRIS_STD_IMPORTVANILLA_PACK_DIMENSION));
            return;
        }
        IrisData data = dimension.getLoader();
        if (data == null) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STUDIO_COULD_NOT_RESOLVE_PACK_DIMENSION, MessageArgument.untrusted("value", dimension.getLoadKey())));
            return;
        }

        VolmitSender sender = sender();
        sender.sendMessage(IrisLanguage.text(BukkitCommandMessages.COMMAND_STUDIO_IMPORTING_VANILLA_CONTENT_INTO, MessageArgument.untrusted("value", dimension.getLoadKey())));

        FeatureImporter.Report features = FeatureImporter.importAllObjectFeatures(data, variants, sender);
        int imported = features.imported();
        int failed = features.failed();

        if (structures) {
            BulkStructureImporter.Report jigsaws = BulkStructureImporter.importAllVanilla(data, StructureImporter.Mode.OVERWRITE, true, sender);
            BulkStructureImporter.Report templates = BulkStructureImporter.importAllTemplates(data, StructureImporter.Mode.OVERWRITE, sender);
            BulkStructureImporter.Report groups = BulkStructureImporter.importTemplateGroups(data, StructureImporter.Mode.OVERWRITE, sender);
            imported += jigsaws.imported() + templates.imported() + groups.imported();
            failed += jigsaws.failed() + templates.failed() + groups.failed();
        }

        sender.sendMessage(IrisLanguage.text(BukkitCommandMessages.COMMAND_STUDIO_IMPORTVANILLA_COMPLETE_OBJECTS_STRUCTURES_WRITTEN_FAILED, MessageArgument.untrusted("imported", imported), MessageArgument.untrusted("failed", failed)));
        sender.sendMessage(IrisLanguage.text(BukkitCommandMessages.COMMAND_STUDIO_TREES_OBJECTS_ARE_UNDER_OBJECTS_VANILLA_REFERENCE_THEM_FROM_BIOME));
    }

    @Director(description = "Open VSCode for a dimension", descriptionKey = "iris.director.commandstudio.director.open_vscode_dimension", aliases = {"vsc"})
    public void vscode(
            @Param(defaultValue = "default", description = "The dimension to open VSCode for", descriptionKey = "iris.director.commandstudio.param.dimension_open_vscode", aliases = "dim", customHandler = DimensionHandler.class)
            IrisDimension dimension
    ) {
        sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STUDIO_OPENING_VSCODE_PACK, MessageArgument.untrusted("value", dimension.getName())));
        Iris.service(StudioSVC.class).openVSCode(sender(), dimension.getLoadKey());
    }

    @Director(description = "Close an open studio project", descriptionKey = "iris.director.commandstudio.director.close_open_studio_project", aliases = {"x"}, sync = true)
    public void close() {
        VolmitSender commandSender = sender();
        if (!Iris.service(StudioSVC.class).isProjectOpen()) {
            commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessages.COMMAND_STUDIO_NO_OPEN_STUDIO_PROJECTS));
            return;
        }

        commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessages.COMMAND_STUDIO_CLOSING_STUDIO));
        Iris.service(StudioSVC.class).close().whenComplete((result, throwable) -> J.s(() -> {
            if (throwable != null) {
                commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessages.COMMAND_STUDIO_STUDIO_CLOSE_FAILED, MessageArgument.untrusted("value", String.valueOf(throwable.getMessage()))));
                return;
            }

            if (result != null && result.failureCause() != null) {
                commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessages.COMMAND_STUDIO_STUDIO_CLOSE_FAILED_2, MessageArgument.untrusted("value", String.valueOf(result.failureCause().getMessage()))));
                return;
            }

            if (result != null && result.startupCleanupQueued()) {
                commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessages.COMMAND_STUDIO_STUDIO_CLOSED_REMAINING_WORLD_FAMILY_CLEANUP_WAS_QUEUED_STARTUP_FALLBACK));
                return;
            }

            commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessages.COMMAND_STUDIO_STUDIO_CLOSED));
        }));
    }

    @Director(description = "Toggle your Studio debug scoreboard", descriptionKey = "iris.director.commandstudio.director.toggle_your_studio_debug_scoreboard", aliases = {"board", "sidebar", "sb"}, origin = DirectorOrigin.PLAYER)
    public void scoreboard() {
        Player target = player();
        VolmitSender commandSender = sender();
        if (!J.runEntity(target, () -> {
            PlatformChunkGenerator generator = IrisToolbelt.access(target.getWorld());
            if (generator == null || !generator.isStudio()) {
                commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessages.COMMAND_STUDIO_YOU_MUST_BE_STUDIO_WORLD_TOGGLE_DEBUG_SCOREBOARD));
                return;
            }

            boolean visible = Iris.service(BoardSVC.class).toggle(target);
            commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessages.COMMAND_STUDIO_STUDIO_DEBUG_SCOREBOARD, MessageArgument.trusted("value", visible ? C.GREEN : C.YELLOW), MessageArgument.trusted("value2", IrisLanguage.text(visible ? RuntimeUiMessages.STATUS_ENABLED : RuntimeUiMessages.STATUS_DISABLED))));
        })) {
            commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessages.COMMAND_STUDIO_COULD_NOT_UPDATE_STUDIO_DEBUG_SCOREBOARD_RIGHT_NOW));
        }
    }

    @Director(description = "Create a new studio project", descriptionKey = "iris.director.commandstudio.director.create_new_studio_project", aliases = "+")
    public void create(
            @Param(description = "The name of this new Iris Project.", descriptionKey = "iris.director.commandstudio.param.name_this_new_iris_project", defaultValue = "studio")
            String name,
            @Param(
                    description = "Copy the contents of an existing project in your packs folder and use it as a template in this new project.", descriptionKey = "iris.director.commandstudio.param.copy_contents_existing_project_your_packs_folder_use_it_as_template_this",
                    defaultValue = "null",
                    contextual = true,
                    contextualOverride = true,
                    customHandler = NullableDimensionHandler.class
            )
            IrisDimension template) {
        if (template != null) {
            Iris.service(StudioSVC.class).create(sender(), name, template);
        } else {
            Iris.service(StudioSVC.class).create(sender(), name);
        }
    }

    @Director(description = "Get the version of a pack", descriptionKey = "iris.director.commandstudio.director.get_version_pack")
    public void version(
            @Param(defaultValue = "default", description = "The dimension get the version of", descriptionKey = "iris.director.commandstudio.param.dimension_get_version", aliases = "dim", contextual = true, contextualOverride = true, customHandler = DimensionHandler.class)
            IrisDimension dimension
    ) {
        sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STUDIO_PACK_HAS_VERSION, MessageArgument.untrusted("value", dimension.getName()), MessageArgument.untrusted("value2", dimension.getVersion())));
    }

    @Director(description = "Open the noise explorer (External GUI)", descriptionKey = "iris.director.commandstudio.director.open_noise_explorer_external_gui", aliases = {"nmap"})
    public void noise(
            @Param(description = "Optional pack generator to preview", descriptionKey = "iris.director.commandstudio.param.optional_pack_generator_preview", defaultValue = "null", contextual = true, contextualOverride = true)
            IrisGenerator generator,
            @Param(description = "The seed to preview the generator with", descriptionKey = "iris.director.commandstudio.param.seed_preview_generator_with", defaultValue = "12345")
            long seed
    ) {
        if (noGUI()) return;
        sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STUDIO_OPENING_NOISE_EXPLORER));

        if (generator == null) {
            NoiseExplorerGUI.launch();
            return;
        }

        String generatorKey = generator.getLoadKey();
        NoiseExplorerGUI.launchGeneratorKey(generatorKey, generator, seed);
    }

    @Director(description = "Open the image-map studio (External GUI)", descriptionKey = "iris.director.commandstudio.director.open_image_map_studio_external_gui", aliases = {"imap"})
    public void imagemap(
            @Param(name = "world", description = "The Iris world whose active pack receives image-map exports", descriptionKey = "iris.director.commandstudio.param.world_open_image_map_studio", contextual = true, contextualOverride = true)
            World world
    ) {
        if (noGUI()) {
            return;
        }
        if (!ImageMapStudioGUI.isAvailable()) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STUDIO_YOU_MUST_HAVE_SERVER_LAUNCHED_GUIS_ENABLED_SETTINGS));
            return;
        }
        if (!IrisToolbelt.isIrisWorld(world)) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STUDIO_YOU_NEED_BE_SPECIFY_IRIS_GENERATED_WORLD));
            return;
        }

        Engine activeEngine = IrisToolbelt.access(world).getEngine();
        ImageMapStudioGUI.launch(activeEngine);
        sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STUDIO_OPENING_IMAGE_MAP_STUDIO));
    }

    @Director(description = "Show loot if a chest were right here", descriptionKey = "iris.director.commandstudio.director.show_loot_if_chest_were_right_here", origin = DirectorOrigin.PLAYER, sync = true)
    public void loot(
            @Param(description = "Fast insertion of items in virtual inventory (may cause performance drop)", descriptionKey = "iris.director.commandstudio.param.fast_insertion_items_virtual_inventory_may_cause_performance_drop", defaultValue = "false")
            boolean fast,
            @Param(description = "Whether or not to append to the inventory currently open (if false, clears opened inventory)", descriptionKey = "iris.director.commandstudio.param.whether_not_append_inventory_currently_open_if_false_clears_opened_inventory", defaultValue = "true")
            boolean add
    ) {
        if (noStudio()) return;

        KList<IrisLootTable> tables = EngineBukkitOps.getLootTables(engine(), RNG.r, player().getLocation().getBlock());
        Inventory inv = Bukkit.createInventory(null, 27 * 2);

        try {
            EngineBukkitOps.addItems(engine(), true, inv, tables, InventorySlotType.STORAGE, player().getWorld(), player().getLocation().getBlockX(), player().getLocation().getBlockY(), player().getLocation().getBlockZ());
        } catch (Throwable e) {
            Iris.reportError(e);
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STUDIO_CANNOT_ADD_ITEMS_VIRTUAL_INVENTORY_BECAUSE, MessageArgument.untrusted("value", String.valueOf(e.getMessage()))));
            return;
        }


        O<Integer> ta = new O<>();
        ta.set(-1);

        var sender = sender();
        var player = player();
        var engine = engine();

        ta.set(J.sr(() ->
        {
            if (!player.getOpenInventory().getType().equals(InventoryType.CHEST)) {
                J.csr(ta.get());
                sender.sendMessage(IrisLanguage.text(BukkitCommandMessages.COMMAND_STUDIO_OPENED_INVENTORY));
                return;
            }

            if (!add) {
                inv.clear();
            }

            EngineBukkitOps.addItems(engine, true, inv, tables, InventorySlotType.STORAGE, player.getWorld(), player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ());
        }, fast ? 5 : 35));

        sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STUDIO_OPENING_INVENTORY_NOW));
        player().openInventory(inv);
    }

    @Director(description = "Calculate the chance for each region to generate", descriptionKey = "iris.director.commandstudio.director.calculate_chance_each_region_generate", origin = DirectorOrigin.PLAYER)
    public void regions(@Param(description = "The radius in chunks", descriptionKey = "iris.director.commandstudio.param.radius_chunks", defaultValue = "500") int radius) {
        var engine = engine();
        if (engine == null) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STUDIO_ONLY_WORKS_IRIS_WORLD));
            return;
        }
        if (radius <= 0 || radius > 2048) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STUDIO_REGIONS_RADIUS_OUT_OF_RANGE));
            return;
        }
        var sender = sender();
        var player = player();
        Thread.ofVirtual()
                .start(() -> {
                    int d = radius * 2;
                    KMap<String, AtomicInteger> data = new KMap<>();
                    // Everything acquired below is released in the finally: a throw mid-scan
                    // previously leaked the whole sampler ForkJoinPool, the self-rescheduling
                    // progress task and both HUD claims for the rest of the server's uptime.
                    MultiBurst multiBurst = null;
                    boolean progressShown = false;
                    int c = -1;
                    try {
                    engine.getDimension().getRegions().forEach(key -> data.put(key, new AtomicInteger(0)));
                    multiBurst = new MultiBurst("Region Sampler");
                    var executor = multiBurst.burst(Math.min(radius * radius, 1 << 20));
                    sender.sendMessage(IrisLanguage.text(BukkitCommandMessages.COMMAND_STUDIO_GENERATING_DATA));
                    var loc = player.getLocation();
                    int totalTasks = d * d;
                    AtomicInteger completedTasks = new AtomicInteger(0);
                    progressShown = true;
                    c = J.ar(() -> {
                        double jobProgress = (double) completedTasks.get() / totalTasks;
                        sender.sendProgress(jobProgress, IrisLanguage.text(RuntimeUiMessages.FINDING_REGIONS));
                        BukkitPlatform.showProgressLane(player, "iris:job", IrisLanguage.text(RuntimeUiMessages.FINDING_REGIONS) + " " + Form.pc(jobProgress, 0), jobProgress, 4000L);
                    }, 0);
                    new Spiraler(d, d, (x, z) -> executor.queue(() -> {
                        var region = engine.getRegion((x << 4) + 8, (z << 4) + 8);
                        data.computeIfAbsent(region.getLoadKey(), (k) -> new AtomicInteger(0))
                                .incrementAndGet();
                        completedTasks.incrementAndGet();
                    })).setOffset(loc.getBlockX(), loc.getBlockZ()).drain();
                    executor.complete();

                    sender.sendMessage(IrisLanguage.text(BukkitCommandMessages.COMMAND_STUDIO_DONE));
                    var loader = engine.getData().getRegionLoader();
                    data.forEach((k, v) -> sender.sendMessage(IrisLanguage.text(BukkitCommandMessages.COMMAND_STUDIO_MESSAGE, MessageArgument.untrusted("k", k), MessageArgument.untrusted("value", loader.load(k).getRarity()), MessageArgument.untrusted("value2", Form.f((double) v.get() / totalTasks * 100, 2)))));
                    } catch (Throwable e) {
                        Iris.reportError(e);
                        sender.sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STUDIO_REGIONS_SCAN_FAILED,
                                MessageArgument.untrusted("value", String.valueOf(e.getMessage()))));
                    } finally {
                        if (c != -1) {
                            J.car(c);
                        }
                        if (progressShown) {
                            sender.sendAction(" ");
                            BukkitPlatform.hudLanes().hide(player, "iris:job");
                        }
                        if (multiBurst != null) {
                            multiBurst.close();
                        }
                    }
                });
    }

    @Director(description = "Render a world map (External GUI)", descriptionKey = "iris.director.commandstudio.director.render_world_map_external_gui", aliases = "render")
    public void map(
            @Param(name = "world", description = "The world to open the generator for", descriptionKey = "iris.director.commandstudio.param.world_open_generator", contextual = true, contextualOverride = true)
            World world
    ) {
        if (noGUI()) return;

        if (!IrisToolbelt.isIrisWorld(world)) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STUDIO_YOU_NEED_BE_SPECIFY_IRIS_GENERATED_WORLD));
            return;
        }

        UUID openerId = sender().isPlayer() ? player().getUniqueId() : null;
        VisionGUI.launch(IrisToolbelt.access(world).getEngine(), openerId);
        sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STUDIO_OPENING_MAP));
    }

    @Director(description = "Profiles the performance of a dimension", descriptionKey = "iris.director.commandstudio.director.profiles_performance_dimension", origin = DirectorOrigin.PLAYER)
    public void profile(
            @Param(description = "The dimension to profile", descriptionKey = "iris.director.commandstudio.param.dimension_profile", contextual = true, contextualOverride = true, defaultValue = "default", customHandler = DimensionHandler.class)
            IrisDimension dimension
    ) {
        // Todo: Make this more accurate
        File pack = dimension.getLoadFile().getParentFile().getParentFile();
        File report = Iris.instance.getDataFile("profile.txt");
        IrisProject project = new IrisProject(pack);
        IrisData data = IrisData.get(pack);
        PlatformChunkGenerator activeGenerator = resolveProfileGenerator(dimension);
        Engine activeEngine = activeGenerator == null ? null : activeGenerator.getEngine();

        if (activeEngine != null) {
            IrisToolbelt.applyPregenPerformanceProfile(activeEngine);
        } else {
            IrisToolbelt.applyPregenPerformanceProfile();
        }

        KList<String> fileText = new KList<>();

        KMap<NoiseStyle, Double> styleTimings = new KMap<>();
        KMap<InterpolationMethod, Double> interpolatorTimings = new KMap<>();
        KMap<String, Double> generatorTimings = new KMap<>();
        KMap<String, Double> biomeTimings = new KMap<>();

        sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STUDIO_CALCULATING_PERFORMANCE_METRICS_NOISE_GENERATORS));

        for (NoiseStyle i : NoiseStyle.values()) {
            CNG c = i.create(new RNG(i.hashCode()));

            for (int j = 0; j < 3000; j++) {
                c.noise(j, j + 1000, j * j);
                c.noise(j, -j);
            }

            PrecisionStopwatch px = PrecisionStopwatch.start();

            for (int j = 0; j < 100000; j++) {
                c.noise(j, j + 1000, j * j);
                c.noise(j, -j);
            }

            styleTimings.put(i, px.getMilliseconds());
        }

        fileText.add("Noise Style Performance Impacts: ");

        for (NoiseStyle i : styleTimings.sortKNumber()) {
            fileText.add(i.name() + ": " + styleTimings.get(i));
        }

        fileText.add("");

        sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STUDIO_CALCULATING_INTERPOLATOR_TIMINGS));

        for (InterpolationMethod i : InterpolationMethod.values()) {
            IrisInterpolator in = new IrisInterpolator();
            in.setFunction(i);
            in.setHorizontalScale(8);

            NoiseProvider np = (x, z) -> Math.random();

            for (int j = 0; j < 3000; j++) {
                in.interpolate(j, -j, np);
            }

            PrecisionStopwatch px = PrecisionStopwatch.start();

            for (int j = 0; j < 100000; j++) {
                in.interpolate(j + 10000, -j - 100000, np);
            }

            interpolatorTimings.put(i, px.getMilliseconds());
        }

        fileText.add("Noise Interpolator Performance Impacts: ");

        for (InterpolationMethod i : interpolatorTimings.sortKNumber()) {
            fileText.add(i.name() + ": " + interpolatorTimings.get(i));
        }

        fileText.add("");

        sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STUDIO_PROCESSING_GENERATOR_SCORES));

        KMap<String, KList<String>> btx = new KMap<>();

        for (String i : data.getGeneratorLoader().getPossibleKeys()) {
            KList<String> vv = new KList<>();
            IrisGenerator g = data.getGeneratorLoader().load(i);
            KList<IrisNoiseGenerator> composites = g.getAllComposites();
            double score = 0;
            int m = 0;
            for (IrisNoiseGenerator j : composites) {
                m++;
                score += styleTimings.get(j.getStyle().getStyle());
                vv.add("Composite Noise Style " + m + " " + j.getStyle().getStyle().name() + ": " + styleTimings.get(j.getStyle().getStyle()));
            }

            score += interpolatorTimings.get(g.getInterpolator().getFunction());
            vv.add("Interpolator " + g.getInterpolator().getFunction().name() + ": " + interpolatorTimings.get(g.getInterpolator().getFunction()));
            generatorTimings.put(i, score);
            btx.put(i, vv);
        }

        fileText.add("Project Generator Performance Impacts: ");

        for (String i : generatorTimings.sortKNumber()) {
            fileText.add(i + ": " + generatorTimings.get(i));

            btx.get(i).forEach((ii) -> fileText.add("  " + ii));
        }

        fileText.add("");

        KMap<String, KList<String>> bt = new KMap<>();

        for (String i : data.getBiomeLoader().getPossibleKeys()) {
            KList<String> vv = new KList<>();
            IrisBiome b = data.getBiomeLoader().load(i);
            double score = 0;

            int m = 0;
            for (IrisBiomePaletteLayer j : b.getLayers()) {
                m++;
                score += styleTimings.get(j.getStyle().getStyle());
                vv.add("Palette Layer " + m + ": " + styleTimings.get(j.getStyle().getStyle()));
            }

            score += styleTimings.get(b.getBiomeStyle().getStyle());
            vv.add("Biome Style: " + styleTimings.get(b.getBiomeStyle().getStyle()));
            score += styleTimings.get(b.getChildStyle().getStyle());
            vv.add("Child Style: " + styleTimings.get(b.getChildStyle().getStyle()));
            biomeTimings.put(i, score);
            bt.put(i, vv);
        }

        fileText.add("Project Biome Performance Impacts: ");

        for (String i : biomeTimings.sortKNumber()) {
            fileText.add(i + ": " + biomeTimings.get(i));

            bt.get(i).forEach((ff) -> fileText.add("  " + ff));
        }

        fileText.add("");

        double m = 0;
        for (double i : biomeTimings.v()) {
            m += i;
        }
        m /= biomeTimings.size();
        double mm = 0;
        for (double i : generatorTimings.v()) {
            mm += i;
        }
        mm /= generatorTimings.size();
        m += mm;

        fileText.add("Average Score: " + m);
        sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STUDIO_SCORE, MessageArgument.untrusted("value", Form.duration(m, 0))));

        try {
            IO.writeAll(report, fileText.toString("\n"));
        } catch (IOException e) {
            Iris.reportError(e);
        }

        sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STUDIO_DONE, MessageArgument.untrusted("value", report.getPath())));
    }

    private PlatformChunkGenerator resolveProfileGenerator(IrisDimension dimension) {
        StudioSVC studioService = Iris.service(StudioSVC.class);
        if (studioService != null && studioService.isProjectOpen()) {
            IrisProject activeProject = studioService.getActiveProject();
            if (activeProject != null) {
                PlatformChunkGenerator activeProvider = activeProject.getActiveProvider();
                if (isGeneratorDimension(activeProvider, dimension)) {
                    return activeProvider;
                }
            }
        }

        if (!sender().isPlayer()) {
            return null;
        }

        Player player = sender().player();
        if (player == null) {
            return null;
        }

        PlatformChunkGenerator worldAccess = IrisToolbelt.access(player.getWorld());
        if (isGeneratorDimension(worldAccess, dimension)) {
            return worldAccess;
        }

        return null;
    }

    private boolean isGeneratorDimension(PlatformChunkGenerator generator, IrisDimension dimension) {
        if (generator == null || generator.getEngine() == null || dimension == null || dimension.getLoadKey() == null) {
            return false;
        }

        IrisDimension engineDimension = generator.getEngine().getDimension();
        if (engineDimension == null || engineDimension.getLoadKey() == null) {
            return false;
        }

        return engineDimension.getLoadKey().equalsIgnoreCase(dimension.getLoadKey());
    }

    @Director(description = "Spawn an Iris entity", descriptionKey = "iris.director.commandstudio.director.spawn_iris_entity", aliases = "summon", origin = DirectorOrigin.PLAYER)
    public void spawn(
            @Param(description = "The entity to spawn", descriptionKey = "iris.director.commandstudio.param.entity_spawn")
            IrisEntity entity,
            @Param(description = "The location to spawn the entity at", descriptionKey = "iris.director.commandstudio.param.location_spawn_entity_at", contextual = true, contextualOverride = true)
            Vector location
    ) {
        VolmitSender commandSender = sender();
        Engine spawnEngine = engine();

        if (!IrisToolbelt.isIrisWorld(player().getWorld())) {
            commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STUDIO_YOU_HAVE_BE_IRIS_WORLD_SPAWN_ENTITIES_PROPERLY_TRYING_SPAWN));
        }

        // Entity creation must run on the thread owning the destination chunk.
        Location at = new Location(world(), location.getX(), location.getY(), location.getZ());
        if (!J.runAt(at, () -> entity.spawn(spawnEngine, at))) {
            Iris.warn("Could not schedule the entity spawn at " + at.getBlockX() + ", " + at.getBlockY() + ", " + at.getBlockZ() + ".");
        }
    }

    @Director(description = "Teleport to the active studio world", descriptionKey = "iris.director.commandstudio.director.teleport_active_studio_world", aliases = "stp", origin = DirectorOrigin.PLAYER, sync = true)
    public void tpstudio() {
        StudioSVC studioService = Iris.service(StudioSVC.class);
        if (!studioService.isProjectOpen()) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STUDIO_NO_STUDIO_WORLD_IS_OPEN));
            return;
        }

        if (IrisToolbelt.isIrisWorld(world()) && engine().isStudio()) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STUDIO_YOU_ARE_ALREADY_STUDIO_WORLD));
            return;
        }

        sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STUDIO_SENDING_YOU_STUDIO_WORLD));
        Player player = player();
        studioService.teleportToActiveProject(player)
                .whenComplete((teleported, failure) -> {
                    if (failure != null) {
                        Iris.reportError("Studio teleport failed for player \"" + player.getName() + "\".", failure);
                        return;
                    }
                });
    }

    @Director(description = "Update your dimension projects VSCode workspace", descriptionKey = "iris.director.commandstudio.director.update_your_dimension_projects_vscode_workspace")
    public void update(
            @Param(description = "The dimension to update the workspace of", descriptionKey = "iris.director.commandstudio.param.dimension_update_workspace", contextual = true, contextualOverride = true, defaultValue = "default", customHandler = DimensionHandler.class)
            IrisDimension dimension
    ) {
        sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STUDIO_UPDATING_CODE_WORKSPACE, MessageArgument.untrusted("value", dimension.getName())));
        if (new IrisCodeWorkspace(new IrisProject(dimension.getLoader().getDataFolder())).updateWorkspace()) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STUDIO_UPDATED_CODE_WORKSPACE, MessageArgument.untrusted("value", dimension.getName())));
        } else {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STUDIO_INVALID_PROJECT_TRY_DELETING_CODE_WORKSPACE_FILE_TRY_AGAIN, MessageArgument.untrusted("value", dimension.getName())));
        }
    }

    @Director(aliases = "find-objects", description = "Capture an IGenData chunk report for nearby chunks", descriptionKey = "iris.director.commandstudio.director.capture_igendata_chunk_report_nearby_chunks", origin = DirectorOrigin.PLAYER)
    public void objects() {
        if (!IrisToolbelt.isIrisWorld(player().getWorld())) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STUDIO_YOU_MUST_BE_IRIS_WORLD));
            return;
        }

        World world = player().getWorld();

        if (!IrisToolbelt.isIrisWorld(world)) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STUDIO_YOU_MUST_BE_IRIS_WORLD_2));
            return;
        }
        KList<Chunk> chunks = new KList<>();
        Player reporter = player();
        CountDownLatch gathered = new CountDownLatch(1);

        // The raycast and the chunk loads need the thread owning the player; the report itself stays off it.
        boolean scheduled = J.runEntity(reporter, () -> {
            try {
                int bx = reporter.getLocation().getChunk().getX();
                int bz = reporter.getLocation().getChunk().getZ();

                try {
                    Location l = reporter.getTargetBlockExact(48, FluidCollisionMode.NEVER).getLocation();

                    int cx = l.getChunk().getX();
                    int cz = l.getChunk().getZ();
                    new Spiraler(3, 3, (x, z) -> chunks.addIfMissing(world.getChunkAt(x + cx, z + cz))).drain();
                } catch (Throwable e) {
                    Iris.reportError(e);
                }

                new Spiraler(3, 3, (x, z) -> chunks.addIfMissing(world.getChunkAt(x + bx, z + bz))).drain();
            } finally {
                gathered.countDown();
            }
        });

        if (!scheduled) {
            Iris.warn("Could not schedule the chunk report scan on the thread owning " + reporter.getName() + ".");
            return;
        }

        try {
            if (!gathered.await(CHUNK_SCAN_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Iris.warn("Timed out waiting for the chunk report scan of " + reporter.getName() + ".");
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STUDIO_CAPTURING_IGENDATA_FROM_NEARBY_CHUNKS, MessageArgument.untrusted("value", chunks.size())));
        File ff = Iris.instance.getDataFile("reports/" + M.ms() + ".txt");
        try (PrintWriter pw = new PrintWriter(ff)) {
            pw.println("=== Iris Chunk Report ===");
            pw.println("== General Info ==");
            pw.println("Iris Version: " + Iris.instance.getDescription().getVersion());
            pw.println("Bukkit Version: " + Bukkit.getBukkitVersion());
            pw.println("MC Version: " + Bukkit.getVersion());
            pw.println("PaperSpigot: " + (BukkitPlatform.isPaperServer() ? "Yup!" : "Nope!"));
            pw.println("Report Captured At: " + new Date());
            pw.println("Chunks: (" + chunks.size() + "): ");

            for (Chunk i : chunks) {
                pw.println("- [" + i.getX() + ", " + i.getZ() + "]");
            }

            int regions = 0;
            long size = 0;
            String age = "No idea...";

            try {
                for (File i : Objects.requireNonNull(new File(world.getWorldFolder(), "region").listFiles())) {
                    if (i.isFile()) {
                        size += i.length();
                    }
                }
            } catch (Throwable e) {
                Iris.reportError(e);
            }

            try {
                FileTime creationTime = (FileTime) Files.getAttribute(world.getWorldFolder().toPath(), "creationTime");
                age = hrf(Duration.of(M.ms() - creationTime.toMillis(), ChronoUnit.MILLIS));
            } catch (IOException e) {
                Iris.reportError(e);
            }

            KList<String> biomes = new KList<>();
            KList<String> caveBiomes = new KList<>();
            KMap<String, KMap<String, KList<String>>> objects = new KMap<>();

            for (Chunk i : chunks) {
                for (int j = 0; j < 16; j += 3) {

                    for (int k = 0; k < 16; k += 3) {

                        assert engine() != null;
                        IrisBiome bb = engine().getSurfaceBiome((i.getX() * 16) + j, (i.getZ() * 16) + k);
                        IrisBiome bxf = engine().getCaveBiome((i.getX() * 16) + j, (i.getZ() * 16) + k);
                        biomes.addIfMissing(bb.getName() + " [" + Form.capitalize(bb.getInferredType().name().toLowerCase()) + "] " + " (" + bb.getLoadFile().getName() + ")");
                        caveBiomes.addIfMissing(bxf.getName() + " (" + bxf.getLoadFile().getName() + ")");
                        exportObjects(bb, pw, engine(), objects);
                        exportObjects(bxf, pw, engine(), objects);
                    }
                }
            }

            String[] regionFiles = new File(world.getWorldFolder(), "region").list();
            regions = regionFiles == null ? 0 : regionFiles.length;

            pw.println();
            pw.println("== World Info ==");
            pw.println("World Name: " + world.getName());
            pw.println("Age: " + age);
            pw.println("Folder: " + world.getWorldFolder().getPath());
            pw.println("Regions: " + Form.f(regions));
            pw.println("Chunks: max. " + Form.f(regions * 32 * 32));
            pw.println("World Size: min. " + Form.fileSize(size));
            pw.println();
            pw.println("== Biome Info ==");
            pw.println("Found " + biomes.size() + " Biome(s): ");

            for (String i : biomes) {
                pw.println("- " + i);
            }
            pw.println();

            pw.println("== Object Info ==");

            for (String i : objects.k()) {
                pw.println("- " + i);

                for (String j : objects.get(i).k()) {
                    pw.println("  @ " + j);

                    for (String k : objects.get(i).get(j)) {
                        pw.println("    * " + k);
                    }
                }
            }

            pw.println();

            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STUDIO_REPORTED, MessageArgument.untrusted("value", ff.getPath())));
        } catch (Throwable e) {
            Iris.reportError(e);
        }
    }

    private void exportObjects(IrisBiome bb, PrintWriter pw, Engine g, KMap<String, KMap<String, KList<String>>> objects) {
        String n1 = bb.getName() + " [" + Form.capitalize(bb.getInferredType().name().toLowerCase()) + "] " + " (" + bb.getLoadFile().getName() + ")";
        int m = 0;
        KSet<String> stop = new KSet<>();
        for (IrisObjectPlacement f : bb.getObjects()) {
            m++;
            String n2 = "Placement #" + m + " (" + f.getPlace().size() + " possible objects)";

            for (String i : f.getPlace()) {
                String nn3 = i + ": [ERROR] Failed to find object!";

                try {
                    if (stop.contains(i)) {
                        continue;
                    }

                    File ff = g.getData().getObjectLoader().findFile(i);
                    art.arcane.iris.generation.geometry.IrisBlockVector sz = IrisObject.sampleSize(ff);
                    nn3 = i + ": size=[" + sz.getBlockX() + "," + sz.getBlockY() + "," + sz.getBlockZ() + "] location=[" + ff.getPath() + "]";
                    stop.add(i);
                } catch (Throwable e) {
                    Iris.reportError(e);
                }

                String n3 = nn3;
                objects.computeIfAbsent(n1, (k1) -> new KMap<>())
                        .computeIfAbsent(n2, (k) -> new KList<>()).addIfMissing(n3);
            }
        }
    }

    /**
     * @return true if server GUIs are not enabled
     */
    private boolean noGUI() {
        if (!IrisSettings.get().getGui().isUseServerLaunchedGuis()) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STUDIO_YOU_MUST_HAVE_SERVER_LAUNCHED_GUIS_ENABLED_SETTINGS));
            return true;
        }
        return false;
    }

    /**
     * @return true if no studio is open or the player is not in one
     */
    private boolean noStudio() {
        if (!sender().isPlayer()) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STUDIO_PLAYERS_ONLY));
            return true;
        }
        if (!Iris.service(StudioSVC.class).isProjectOpen()) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STUDIO_NO_STUDIO_WORLD_IS_OPEN_2));
            return true;
        }
        if (!IrisToolbelt.isStudio(world())) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STUDIO_YOU_MUST_BE_STUDIO_WORLD));
            return true;
        }
        return false;
    }


    public void files(File clean, KList<File> files) {
        if (clean.isDirectory()) {
            for (File i : clean.listFiles()) {
                files(i, files);
            }
        } else if (clean.getName().endsWith(".json")) {
            try {
                files.add(clean);
            } catch (Throwable e) {
                Iris.reportError(e);
                Iris.error("Failed to beautify " + clean.getAbsolutePath() + " You may have errors in your json!");
            }
        }
    }

    private void fixBlocks(JSONObject obj) {
        for (String i : obj.keySet()) {
            Object o = obj.get(i);

            if (i.equals("block") && o instanceof String && !o.toString().trim().isEmpty() && !o.toString().contains(":")) {
                obj.put(i, "minecraft:" + o);
            }

            if (o instanceof JSONObject) {
                fixBlocks((JSONObject) o);
            } else if (o instanceof JSONArray) {
                fixBlocks((JSONArray) o);
            }
        }
    }

    private void fixBlocks(JSONArray obj) {
        for (int i = 0; i < obj.length(); i++) {
            Object o = obj.get(i);

            if (o instanceof JSONObject) {
                fixBlocks((JSONObject) o);
            } else if (o instanceof JSONArray) {
                fixBlocks((JSONArray) o);
            }
        }
    }
}
