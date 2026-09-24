package art.arcane.iris.probe;

import art.arcane.iris.structure.nativegen.NativeStructureVolumeIndex;
import art.arcane.iris.generation.hydrology.HydrologyTileCache;
import art.arcane.volmlib.nativelib.terrain.structure.NativeStructureVolume;
import art.arcane.volmlib.util.collection.KList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.spigotmc.SpigotWorldConfig;

import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class HeadlessRegionTerrainTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void producesNativeTerrainAndFullCompletionHaloWithoutAServer() throws Exception {
        Path root = temporary.newFolder().toPath();
        Path configuration = root.resolve("spigot.yml");
        Files.writeString(configuration, "config-version: 13\nworld-settings:\n  default:\n    verbose: false\n");
        HeadlessNativeRuntime runtime = new HeadlessNativeRuntime();
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(new RealPackProbeSupport.WorkspaceOptions(
                pack().toFile(), "main", "[headless-native-grid-test]", runtime, temporary.getRoot().toPath()));
             RealPackProbeSupport.EngineSession session = workspace.openEngine(69420L, false, "native-grid")) {
            NativeStructureVolume volume = NativeStructureVolume.of("minecraft:swamp_hut", 0, 20, 0, 7, 30, 7);
            NativeStructureVolumeIndex.install(session.engine(), (engine, x, z) ->
                    x == 0 && z == 0 ? new KList<>(List.of(volume)) : NativeStructureVolume.NONE);
            assertEquals(List.of(volume), session.engine().getNativeStructureVolumes(0, 0, 15, 15));
            SpigotWorldConfig spigot = HeadlessStructureConfiguration.load(new HeadlessStructureConfiguration.Options(
                    configuration, "probe", Level.OVERWORLD, root.resolve("configuration")));
            try (HeadlessTerrainContext context = HeadlessTerrainContext.create(new HeadlessTerrainContext.Options(
                    session.engine(), runtime, root.resolve("templates"), Level.OVERWORLD,
                    NoiseGeneratorSettings.OVERWORLD, spigot));
                 RegionGenerationWindow workers = new RegionGenerationWindow(4, RegionGenerationWindow.Policy.EMBEDDED)) {
                HeadlessRegionTerrain.Session rolling = new HeadlessRegionTerrain.Session(workers);
                HeadlessRegionTerrain.Result result = rolling.generate(
                        new HeadlessRegionTerrain.Request(context, -3, 2, 1, 4), System.out::println);
                OfflineRegionGenerator.Configuration export = new OfflineRegionGenerator.Configuration(
                        workspace.pack(), "main", 69420L, -3, 2, 1, 2, 3, 4, configuration,
                        "probe", Level.OVERWORLD, NoiseGeneratorSettings.OVERWORLD, root.resolve("export"));
                HydrologyTileCache.PregenerationArea bounds = export.hydrologyArea();
                assertEquals(-80L, bounds.minimumBlockX());
                assertEquals(0L, bounds.minimumBlockZ());
                assertEquals(15L, bounds.maximumBlockX());
                assertEquals(111L, bounds.maximumBlockZ());
                assertEquals(1, result.targetChunks());
                assertEquals(529, result.chunks().size());
                long terrain = result.chunks().stream().filter(chunk -> chunk.getPersistedStatus() == ChunkStatus.TERRAIN).count();
                long biomes = result.chunks().stream().filter(chunk -> chunk.getPersistedStatus() == ChunkStatus.BIOMES).count();
                assertEquals(25L, terrain);
                assertEquals(24L, biomes);
                ProtoChunk target = result.chunks().stream().filter(chunk -> chunk.getPos().equals(new ChunkPos(-3, 2)))
                        .findFirst().orElseThrow();
                CompoundTag tag = HeadlessChunkSerialization.copyOf(target, context.serialization()).write();
                assertEquals("minecraft:terrain", tag.getStringOr("Status", ""));
                assertFalse(tag.getBooleanOr("isLightOn", false));
                assertTrue(tag.getCompoundOrEmpty("Heightmaps").contains("WORLD_SURFACE_WG"));
                assertTrue(tag.getCompoundOrEmpty("Heightmaps").contains("OCEAN_FLOOR_WG"));
                assertEquals(6, tag.getCompoundOrEmpty("Heightmaps").size());
                assertTrue(tag.getCompoundOrEmpty("ChunkBukkitValues").contains("iris:natural_terrain"));
                assertTrue(tag.getCompoundOrEmpty("ChunkBukkitValues").contains("iris:structure_activation"));
                assertFalse(tag.getListOrEmpty("sections").isEmpty());
                assertTrue(RealPackProbeSupport.drainReported().isEmpty());
                assertNull(Bukkit.getServer());
                HeadlessRegionTerrain.Result repeated = rolling.generate(
                        new HeadlessRegionTerrain.Request(context, -3, 2, 1, 4), System.out::println);
                assertEquals(0, repeated.updated().size());
                HeadlessRegionTerrain.Result adjacent = rolling.generate(
                        new HeadlessRegionTerrain.Request(context, -2, 2, 1, 4), System.out::println);
                assertEquals(529, adjacent.chunks().size());
                assertTrue(adjacent.updated().size() < result.updated().size());
                ProtoChunk retained = adjacent.chunks().stream().filter(chunk -> chunk.getPos().equals(target.getPos()))
                        .findFirst().orElseThrow();
                assertSame(target, retained);
                assertEquals(ChunkStatus.TERRAIN, retained.getPersistedStatus());
                Path regionDirectory = Files.createDirectory(root.resolve("region"));
                OfflineRegionWriter writer = new OfflineRegionWriter(new OfflineRegionWriter.Options(
                        context, regionDirectory, workers));
                OfflineRegionWriter.Result firstWrite = writer.write(result);
                assertEquals(result.updated().size(), firstWrite.chunks());
                assertEquals(tag, read(regionDirectory, target.getPos()));
                OfflineRegionWriter.Result duplicateWrite = writer.write(result);
                assertEquals(0, duplicateWrite.chunks());
                OfflineRegionWriter reopened = new OfflineRegionWriter(new OfflineRegionWriter.Options(
                        context, regionDirectory, workers));
                assertEquals(0, reopened.write(result).chunks());
                target.setPersistedStatus(ChunkStatus.STRUCTURE_STARTS);
                try {
                    assertEquals(0, writer.write(
                            new HeadlessRegionTerrain.Result(List.of(target), List.of(target), 1)).chunks());
                } finally {
                    target.setPersistedStatus(ChunkStatus.TERRAIN);
                }
                HeadlessRegionTerrain.Result nextRow = rolling.generate(
                        new HeadlessRegionTerrain.Request(context, -2, 3, 1, 4), System.out::println);
                assertEquals(529, nextRow.chunks().size());
                assertSame(target, nextRow.chunks().stream().filter(chunk -> chunk.getPos().equals(target.getPos()))
                        .findFirst().orElseThrow());
                assertTrue(nextRow.updated().size() < result.updated().size());
                HeadlessRegionTerrain.Result distant = rolling.generate(
                        new HeadlessRegionTerrain.Request(context, 20, 2, 1, 4), System.out::println);
                assertEquals(529, distant.chunks().size());
                ProtoChunk previousHalo = distant.chunks().getFirst();
                HeadlessRegionTerrain.Result back = rolling.generate(
                        new HeadlessRegionTerrain.Request(context, -3, 2, 1, 4), System.out::println);
                assertFalse(back.chunks().contains(previousHalo));
                ProtoChunk regenerated = back.chunks().stream().filter(chunk -> chunk.getPos().equals(target.getPos()))
                        .findFirst().orElseThrow();
                CompoundTag regeneratedTag = HeadlessChunkSerialization.copyOf(regenerated, context.serialization()).write();
                for (String key : List.of("sections", "Heightmaps", "structures", "ChunkBukkitValues")) {
                    assertEquals(key, tag.get(key), regeneratedTag.get(key));
                }
                assertEquals(0, writer.write(back).chunks());
                Path oversizedDirectory = Files.createDirectory(root.resolve("oversized"));
                OfflineRegionWriter oversizedWriter = new OfflineRegionWriter(new OfflineRegionWriter.Options(
                        context, oversizedDirectory, workers));
                NamespacedKey payloadKey = new NamespacedKey("iris", "serialization_test_payload");
                byte[] payload = new byte[2 * 1024 * 1024];
                new Random(69420L).nextBytes(payload);
                regenerated.persistentDataContainer.set(payloadKey, PersistentDataType.BYTE_ARRAY, payload);
                HeadlessRegionTerrain.Result singleton = new HeadlessRegionTerrain.Result(List.of(regenerated), List.of(regenerated), 1);
                assertEquals(1, oversizedWriter.write(singleton).chunks());
                assertTrue(Files.isRegularFile(oversizedDirectory.resolve("c.-3.2.mcc")));
                assertArrayEquals(payload, read(oversizedDirectory, regenerated.getPos())
                        .getCompoundOrEmpty("ChunkBukkitValues").getByteArray("iris:serialization_test_payload").orElseThrow());
                regenerated.persistentDataContainer.remove(payloadKey);
                Path failedDirectory = Files.createDirectory(root.resolve("failed"));
                OfflineRegionWriter failedWriter = new OfflineRegionWriter(new OfflineRegionWriter.Options(
                        context, failedDirectory, workers));
                regenerated.setPersistedStatus(ChunkStatus.FULL);
                try {
                    assertThrows(Exception.class, () -> failedWriter.write(singleton));
                } finally {
                    regenerated.setPersistedStatus(ChunkStatus.TERRAIN);
                }
                assertThrows(IllegalStateException.class, () -> failedWriter.write(singleton));
                try (RegionGenerationWindow replacement = new RegionGenerationWindow(4, RegionGenerationWindow.Policy.EMBEDDED)) {
                    OfflineRegionWriter retried = new OfflineRegionWriter(new OfflineRegionWriter.Options(
                            context, failedDirectory, replacement));
                    assertEquals(1, retried.write(singleton).chunks());
                }
                assertEquals("minecraft:terrain", read(failedDirectory, regenerated.getPos()).getStringOr("Status", ""));
            }
        }
    }

    private static CompoundTag read(Path directory, ChunkPos position) throws Exception {
        Path path = directory.resolve("r." + (position.x() >> 5) + "." + (position.z() >> 5) + ".mca");
        try (RegionFile region = new RegionFile(new RegionStorageInfo("test", Level.OVERWORLD, "chunk"), path, directory, false);
             DataInputStream input = region.getChunkDataInputStream(position)) {
            return NbtIo.read(input);
        }
    }

    private Path pack() throws Exception {
        Path pack = temporary.newFolder().toPath();
        for (String directory : List.of("dimensions", "regions", "biomes", "generators")) {
            Files.createDirectories(pack.resolve(directory));
        }
        Files.writeString(pack.resolve("dimensions/main.json"), """
                {"name":"Native region","focus":"swamp","regions":["owned"],"fluidHeight":40,"logicalHeight":256,
                 "landChance":1,"carvingEnabled":false,"hydrology":{"rivers":{"enabled":false}}}
                """);
        Files.writeString(pack.resolve("regions/owned.json"), "{\"landBiomes\":[\"swamp\"]}");
        Files.writeString(pack.resolve("generators/flat.json"), "{\"composite\":[]}");
        Files.writeString(pack.resolve("biomes/swamp.json"), """
                {"derivative":"minecraft:swamp","generators":[{"generator":"flat","min":35,"max":35}]}
                """);
        return pack;
    }
}
