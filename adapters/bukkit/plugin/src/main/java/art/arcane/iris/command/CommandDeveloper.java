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

import com.google.gson.JsonObject;
import art.arcane.iris.Iris;
import art.arcane.iris.pack.datapack.ServerConfigurator;
import art.arcane.iris.world.lifecycle.LifecycleOperationCoordinator;
import art.arcane.iris.platform.bukkit.nms.datapack.DataVersion;
import art.arcane.iris.world.runtime.ChunkClearer;
import art.arcane.iris.generation.validation.GoldenHashScanner;
import art.arcane.iris.world.runtime.InPlaceChunkRegenerator;
import art.arcane.iris.generation.runtime.IrisEngineSVC;
import art.arcane.iris.studio.StudioSVC;
import art.arcane.iris.pack.IrisPackBenchmarking;
import art.arcane.iris.world.IrisToolbelt;
import art.arcane.iris.generation.runtime.IrisEngineMantle;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import art.arcane.iris.world.storage.matter.IrisMatterContext;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.iris.localization.C;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.io.CountingDataInputStream;
import art.arcane.volmlib.util.mantle.runtime.TectonicPlate;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.iris.world.storage.region.MCAFile;
import art.arcane.iris.world.storage.region.MCAUtil;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.iris.world.task.J;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;

import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.localization.BukkitCommandMessages;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.iris.localization.BukkitCommandMessagesExtended;
import art.arcane.iris.localization.BukkitRuntimeMessages;
@Director(name = "Developer", origin = DirectorOrigin.BOTH, description = "Iris World Manager", descriptionKey = "iris.director.commanddeveloper.director.iris_world_manager", aliases = {"dev"})
public class CommandDeveloper implements DirectorExecutor {
    @Director(description = "Get Loaded TectonicPlates Count", descriptionKey = "iris.director.commanddeveloper.director.get_loaded_tectonicplates_count", origin = DirectorOrigin.BOTH, sync = true)
    public void EngineStatus() {
        Iris.service(IrisEngineSVC.class)
                .engineStatus(sender());
    }

    @Director(description = "Hash generated block output of a fixed area for determinism/identity testing", descriptionKey = "iris.director.commanddeveloper.director.hash_generated_block_output_fixed_area_determinism_identity_testing", origin = DirectorOrigin.BOTH)
    public void genhash(
            @Param(description = "The world to hash", descriptionKey = "iris.director.commanddeveloper.param.world_hash", contextual = true, contextualOverride = true)
            World world,
            @Param(description = "Radius in chunks around the center", descriptionKey = "iris.director.commanddeveloper.param.radius_chunks_around_center", defaultValue = "4")
            int radius,
            @Param(description = "Center chunk X", descriptionKey = "iris.director.commanddeveloper.param.center_chunk_x", defaultValue = "0")
            int centerX,
            @Param(description = "Center chunk Z", descriptionKey = "iris.director.commanddeveloper.param.center_chunk_z", defaultValue = "0")
            int centerZ) {
        if (world == null) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_DEVELOPER_WORLD_IS_NULL));
            return;
        }

        VolmitSender sender = sender();
        sender.sendMessage(IrisLanguage.text(BukkitCommandMessages.COMMAND_DEVELOPER_GENHASH_STARTED_CHUNKS, MessageArgument.untrusted("value", ((radius * 2 + 1) * (radius * 2 + 1)))));
        J.a(() -> runGenhash(sender, world, radius, centerX, centerZ));
    }

    private void runGenhash(VolmitSender sender, World world, int radius, int centerX, int centerZ) {
        long startMs = M.ms();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        long globalHash = 0L;
        long solidBlocks = 0L;
        Map<String, Long> histogram = new TreeMap<>();
        JsonObject chunkHashes = new JsonObject();

        for (int rx = centerX - radius; rx <= centerX + radius; rx++) {
            for (int rz = centerZ - radius; rz <= centerZ + radius; rz++) {
                org.bukkit.ChunkSnapshot snapshot;
                try {
                    org.bukkit.Chunk loaded = art.arcane.iris.platform.bukkit.BukkitPlatform.chunkAtAsync(world, rx, rz, true).get();
                    snapshot = loaded.getChunkSnapshot(false, false, false);
                } catch (Throwable e) {
                    Iris.reportError(e);
                    sender.sendMessage(IrisLanguage.text(BukkitCommandMessages.COMMAND_DEVELOPER_GENHASH_FAILED_AT_CHUNK, MessageArgument.untrusted("rx", rx), MessageArgument.untrusted("rz", rz), MessageArgument.untrusted("value", String.valueOf(e.getMessage()))));
                    return;
                }
                long chunkHash = 0L;
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        int worldX = (rx << 4) + x;
                        int worldZ = (rz << 4) + z;
                        for (int y = minY; y < maxY; y++) {
                            org.bukkit.Material material = snapshot.getBlockType(x, y, z);
                            long positionSeed = ((long) worldX * 0x9E3779B97F4A7C15L)
                                    ^ ((long) y * 0xC2B2AE3D27D4EB4FL)
                                    ^ ((long) worldZ * 0x165667B19E3779F9L);
                            long blockHash = genHashMix(positionSeed ^ ((long) (material.ordinal() + 1) * 0xD6E8FEB86659FD93L));
                            chunkHash ^= blockHash;
                            if (material != org.bukkit.Material.AIR
                                    && material != org.bukkit.Material.CAVE_AIR
                                    && material != org.bukkit.Material.VOID_AIR) {
                                histogram.merge(material.name(), 1L, Long::sum);
                                solidBlocks++;
                            }
                        }
                    }
                }
                globalHash ^= chunkHash;
                chunkHashes.addProperty(rx + "," + rz, Long.toHexString(chunkHash));
            }
        }

        int side = radius * 2 + 1;
        JsonObject result = new JsonObject();
        result.addProperty("global", Long.toHexString(globalHash));
        result.addProperty("chunks", side * side);
        result.addProperty("solidBlocks", solidBlocks);
        result.addProperty("minY", minY);
        result.addProperty("maxY", maxY);
        JsonObject hist = new JsonObject();
        for (Map.Entry<String, Long> entry : histogram.entrySet()) {
            hist.addProperty(entry.getKey(), entry.getValue());
        }
        result.add("histogram", hist);
        result.add("chunkHashes", chunkHashes);

        File out = new File(Iris.instance.getDataFolder(), "genhash.json");
        try {
            Files.writeString(out.toPath(), result.toString());
        } catch (IOException e) {
            Iris.reportError(e);
        }

        sender.sendMessage(IrisLanguage.text(BukkitCommandMessages.COMMAND_DEVELOPER_GENHASH_GLOBAL_CHUNKS_SOLID, MessageArgument.untrusted("value", Long.toHexString(globalHash)), MessageArgument.untrusted("value2", (side * side)), MessageArgument.untrusted("solidBlocks", solidBlocks), MessageArgument.untrusted("value3", Form.duration((long) (M.ms() - startMs), 1))));
        Iris.info("genhash world=" + world.getName() + " global=" + Long.toHexString(globalHash)
                + " chunks=" + (side * side) + " solidBlocks=" + solidBlocks + " -> " + out.getAbsolutePath());
    }

    private static long genHashMix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    @Director(description = "Stage a world generation update", descriptionKey = "iris.director.commanddeveloper.director.stage_world_generation_update", name = "update-world", aliases = "^world")
    public void updateWorld(
            @Param(description = "The world to update", descriptionKey = "iris.director.commanddeveloper.param.world_update", contextual = true, contextualOverride = true)
            World world,
            @Param(description = "The pack to install into the world", descriptionKey = "iris.director.commanddeveloper.param.pack_install_into_world", contextual = true, contextualOverride = true, aliases = "dimension")
            IrisDimension pack,
            @Param(description = "Make sure to make a backup & read the warnings first!", descriptionKey = "iris.director.commanddeveloper.param.make_sure_make_backup_read_warnings_first", defaultValue = "false", aliases = "c")
            boolean confirm
    ) {
        if (!confirm) {
            sender().sendMessage(IrisLanguage.text(
                    BukkitRuntimeMessages.COMMAND_DEVELOPER_UPDATE_WORLD_WARNING,
                    MessageArgument.untrusted("world", world.getName()),
                    MessageArgument.untrusted("pack", pack.getLoadKey())
            ));
            return;
        }

        File folder = world.getWorldFolder();
        folder.mkdirs();

        try (LifecycleOperationCoordinator.Lease lease = LifecycleOperationCoordinator.get().acquire(
                LifecycleOperationCoordinator.Domain.PACK_MUTATION,
                LifecycleOperationCoordinator.OperationKind.PACK_PUBLISH,
                pack.getLoadKey()
        )) {
            Iris.service(StudioSVC.class).replaceIntoWorld(sender(), pack, folder, world.getSeed());
        } catch (LifecycleOperationCoordinator.BusyException e) {
            sender().sendMessage(C.YELLOW + e.getMessage());
        }
    }

    @Director(description = "Test", descriptionKey = "iris.director.commanddeveloper.director.test")
    public void mantle(
            @Param(name = "plate", description = "Dump the whole tectonic plate instead of a single section", descriptionKey = "iris.director.commanddeveloper.param.dump_whole_tectonic_plate_instead_single_section", defaultValue = "false")
            boolean plate,
            @Param(name = "name", description = "The dump file id under plugins/Iris/dump (pv.<id>.*)", descriptionKey = "iris.director.commanddeveloper.param.dump_file_id_under_plugins_iris_dump_pv_id", defaultValue = "21474836474")
            String name
    ) throws Throwable {
        Engine activeEngine = engine();
        if (activeEngine == null) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_DEVELOPER_TARGET_IRIS_WORLD_BEFORE_READING_MANTLE_DUMP));
            return;
        }
        File base = Iris.instance.getDataFile("dump", "pv." + name + ".ttp.lz4b.bin");
        File section = Iris.instance.getDataFile("dump", "pv." + name + ".section.bin");

        try (IrisMatterContext.Scope scope = IrisMatterContext.open(activeEngine.getData())) {
            if (plate) {
                try (CountingDataInputStream in = CountingDataInputStream.wrap(new BufferedInputStream(new FileInputStream(base)))) {
                    TectonicPlate.read(1088, in, true, IrisEngineMantle.createRuntimeDataAdapter(activeEngine.getData()), IrisEngineMantle.createRuntimeHooks());
                } catch (Throwable e) {
                    Iris.reportError("Failed to inspect the Iris tectonic plate.", e);
                }
            } else {
                Matter.read(section);
            }
        }
        if (!TectonicPlate.hasError()) {
            Iris.info("Read " + (plate ? base : section).length() + " bytes from " + (plate ? base : section).getAbsolutePath());
        }
    }

    @Director(description = "Test", descriptionKey = "iris.director.commanddeveloper.director.test_2")
    public void packBenchmark(
            @Param(description = "The pack to bench", descriptionKey = "iris.director.commanddeveloper.param.pack_bench", aliases = {"pack"}, defaultValue = "overworld")
            IrisDimension dimension,
            @Param(description = "Radius in regions", descriptionKey = "iris.director.commanddeveloper.param.radius_regions", defaultValue = "2048")
            int radius,
            @Param(description = "Open GUI while benchmarking", descriptionKey = "iris.director.commanddeveloper.param.open_gui_while_benchmarking", defaultValue = "false")
            boolean gui
    ) {
        new IrisPackBenchmarking(dimension, radius, gui);
    }

    @Director(description = "Upgrade to another Minecraft version", descriptionKey = "iris.director.commanddeveloper.director.upgrade_another_minecraft_version")
    public void upgrade(
            @Param(description = "The version to upgrade to", descriptionKey = "iris.director.commanddeveloper.param.version_upgrade", defaultValue = "latest") DataVersion version) {
        sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_DEVELOPER_UPGRADING, MessageArgument.untrusted("value", version.getVersion())));
        ServerConfigurator.installDataPacks(version.get(), false);
        sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_DEVELOPER_DONE_UPGRADING_YOU_CAN_NOW_UPDATE_YOUR_SERVER_VERSION, MessageArgument.untrusted("value", version.getVersion())));
    }

    @Director(description = "test", descriptionKey = "iris.director.commanddeveloper.director.test_3")
    public void mca (
            @Param(description = "The world folder to scan for .mca region files", descriptionKey = "iris.director.commanddeveloper.param.world_folder_scan_mca_region_files") String world) {
        try {
            File[] McaFiles = new File(world, "region").listFiles((dir, name) -> name.endsWith(".mca"));
            for (File mca : McaFiles) {
                MCAFile MCARegion = MCAUtil.read(mca);
            }
        } catch (Exception e) {
            Iris.reportError("Failed to inspect Minecraft region files.", e);
        }

    }

    @Director(description = "Delete nearby chunk blocks for regen testing", descriptionKey = "iris.director.commanddeveloper.director.delete_nearby_chunk_blocks_regen_testing", name = "delete-chunk", aliases = {"dc"}, origin = DirectorOrigin.PLAYER)
    public void deleteChunk(
            @Param(description = "Radius in chunks around your current chunk", descriptionKey = "iris.director.commanddeveloper.param.radius_chunks_around_your_current_chunk", defaultValue = "0")
            int radius
    ) {
        if (radius < 0) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_DEVELOPER_RADIUS_MUST_BE_0_GREATER));
            return;
        }

        Player player = player();
        VolmitSender commandSender = sender();
        World world = player.getWorld();
        if (!IrisToolbelt.isIrisWorld(world)) {
            commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_DEVELOPER_THIS_IS_NOT_IRIS_WORLD));
            return;
        }

        PlatformChunkGenerator access = IrisToolbelt.access(world);
        if (access == null || access.getEngine() == null) {
            commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_DEVELOPER_ENGINE_ACCESS_THIS_WORLD_IS_NULL));
            return;
        }

        Engine engine = access.getEngine();
        int chunks = (radius * 2 + 1) * (radius * 2 + 1);

        // The player position must be read on the thread owning the player; ChunkClearer hops per chunk itself.
        if (!J.runEntity(player, () -> {
            int centerX = player.getLocation().getBlockX() >> 4;
            int centerZ = player.getLocation().getBlockZ() >> 4;

            commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_DEVELOPER_DELETE_STARTED_CHUNK_S_AROUND_CLEARING_BLOCKS_AIR, MessageArgument.untrusted("chunks", chunks), MessageArgument.untrusted("centerX", centerX), MessageArgument.untrusted("centerZ", centerZ)));

            new ChunkClearer(world, engine, commandSender, centerX, centerZ, radius).start();
        })) {
            Iris.warn("Could not schedule delete-chunk on the thread owning " + player.getName() + ".");
        }
    }

    @Director(description = "Test", descriptionKey = "iris.director.commanddeveloper.director.test_4", aliases = {"ip"})
    public void network() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : Collections.list(networkInterfaces)) {
                Iris.info("Display Name: %s", ni.getDisplayName());
                Enumeration<InetAddress> inetAddresses = ni.getInetAddresses();
                for (InetAddress ia : Collections.list(inetAddresses)) {
                    Iris.info("IP: %s", ia.getHostAddress());
                }
            }
        } catch (Exception e) {
            Iris.reportError("Failed to inspect server network interfaces.", e);
        }
    }

    // --- Regen ---

    @Director(name = "regen", aliases = {"rg"}, description = "Delete and regenerate nearby chunks in place using Iris generation", descriptionKey = "iris.director.commanddeveloper.director.delete_regenerate_nearby_chunks_place_using_iris_generation", origin = DirectorOrigin.PLAYER)
    public void regen(
            @Param(name = "radius", description = "The radius of nearby chunks", descriptionKey = "iris.director.commanddeveloper.param.radius_nearby_chunks", defaultValue = "5")
            int radius
    ) {
        if (radius < 0) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_DEVELOPER_RADIUS_MUST_BE_0_GREATER_2));
            return;
        }

        Player player = player();
        VolmitSender commandSender = sender();
        World world = player.getWorld();
        if (!IrisToolbelt.isIrisWorld(world)) {
            commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_DEVELOPER_YOU_MUST_BE_IRIS_WORLD_USE_REGEN));
            return;
        }

        Engine engine = IrisToolbelt.access(world).getEngine();
        if (engine == null) {
            commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_DEVELOPER_ENGINE_ACCESS_THIS_WORLD_IS_NULL_GENERATE_NEARBY_CHUNKS_FIRST));
            return;
        }

        int chunks = (radius * 2 + 1) * (radius * 2 + 1);

        // The player position must be read on the thread owning the player; the regenerator hops per chunk itself.
        if (!J.runEntity(player, () -> {
            int centerX = player.getLocation().getBlockX() >> 4;
            int centerZ = player.getLocation().getBlockZ() >> 4;

            commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_DEVELOPER_REGEN_STARTED_CHUNK_S_AROUND_DELETING_REGENERATING_PLACE, MessageArgument.untrusted("chunks", chunks), MessageArgument.untrusted("centerX", centerX), MessageArgument.untrusted("centerZ", centerZ)));
            Iris.info("Regen run start: world=" + world.getName()
                    + " center=" + centerX + "," + centerZ
                    + " radius=" + radius
                    + " chunks=" + chunks);

            new InPlaceChunkRegenerator(world, engine, commandSender, centerX, centerZ, radius).start();
        })) {
            Iris.warn("Could not schedule regen on the thread owning " + player.getName() + ".");
        }
    }

    @Director(name = "goldenhash", aliases = {"gold"}, description = "Generate chunks into buffers (no world writes) and hash blocks+biomes; captures a golden file or verifies against an existing one. Deletes the world's entire mantle - use on disposable test worlds.", descriptionKey = "iris.director.commanddeveloper.director.generate_chunks_into_buffers_no_world_writes_hash_blocks_biomes_captures_golden", origin = DirectorOrigin.BOTH)
    public void goldenhash(
            @Param(description = "The world to scan", descriptionKey = "iris.director.commanddeveloper.param.world_scan", contextual = true, contextualOverride = true)
            World world,
            @Param(name = "radius", description = "Radius in chunks around the center", descriptionKey = "iris.director.commanddeveloper.param.radius_chunks_around_center_2", defaultValue = "8")
            int radius,
            @Param(name = "center-x", description = "Center chunk X", descriptionKey = "iris.director.commanddeveloper.param.center_chunk_x_2", defaultValue = "0")
            int centerX,
            @Param(name = "center-z", description = "Center chunk Z", descriptionKey = "iris.director.commanddeveloper.param.center_chunk_z_2", defaultValue = "0")
            int centerZ,
            @Param(name = "reset-mantle", description = "Delete the world's entire mantle folder first for full regeneration from scratch", descriptionKey = "iris.director.commanddeveloper.param.delete_mantle_data_scan_area_first_full_regeneration_from_scratch", defaultValue = "true")
            boolean resetMantle,
            @Param(name = "threads", description = "Concurrent chunk generations; 1 = strictly serial for order-dependence testing", descriptionKey = "iris.director.commanddeveloper.param.concurrent_chunk_generations_1_strictly_serial_order_dependence_testing", defaultValue = "8")
            int threads,
            @Param(name = "deep", description = "Also dump full per-chunk non-air blockstates for offline diffing", descriptionKey = "iris.director.commanddeveloper.param.also_dump_full_per_chunk_non_air_blockstates_offline_diffing", defaultValue = "false")
            boolean deep
    ) {
        if (radius < 0) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_DEVELOPER_RADIUS_MUST_BE_0_GREATER_3));
            return;
        }

        if (world == null || !IrisToolbelt.isIrisWorld(world)) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_DEVELOPER_TARGET_MUST_BE_IRIS_WORLD));
            return;
        }

        PlatformChunkGenerator access = IrisToolbelt.access(world);
        if (access == null || access.getEngine() == null) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_DEVELOPER_ENGINE_ACCESS_THIS_WORLD_IS_NULL_2));
            return;
        }

        int chunks = (radius * 2 + 1) * (radius * 2 + 1);
        sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_DEVELOPER_GOLDENHASH_STARTED_CHUNK_S_AROUND_BUFFERS_WORLD_UNTOUCHED, MessageArgument.untrusted("chunks", chunks), MessageArgument.untrusted("centerX", centerX), MessageArgument.untrusted("centerZ", centerZ)));
        Iris.info("goldenhash start: world=" + world.getName()
                + " center=" + centerX + "," + centerZ
                + " radius=" + radius
                + " chunks=" + chunks);

        new GoldenHashScanner(world, access.getEngine(), sender(), centerX, centerZ, radius, resetMantle, threads, deep).start();
    }

}
