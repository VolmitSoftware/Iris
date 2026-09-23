package art.arcane.iris.probe;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeBiomeSourceImpl;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.bukkit.craftbukkit.generator.CustomChunkGenerator;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public class HeadlessChunkGeneratorTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void nativeStructureStateAndOriginMatchThePaperGeneratorDefaults() throws Exception {
        int checked = 0;
        for (Method method : CustomChunkGenerator.class.getMethods()) {
            if (!method.getName().equals("getOrigin") && !method.getName().equals("createState")) {
                continue;
            }
            assertEquals(ChunkGenerator.class, method.getDeclaringClass());
            assertEquals(method, HeadlessChunkGenerator.class.getMethod(method.getName(), method.getParameterTypes()));
            checked++;
        }
        assertEquals(2, checked);
    }

    @Test
    public void nativeSwampHutQueriesRealPackHeightsAndBiomesWithoutAServer() throws Exception {
        long seed = 69420L;
        HeadlessNativeRuntime runtime = new HeadlessNativeRuntime();
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(new RealPackProbeSupport.WorkspaceOptions(
                pack().toFile(), "main", "[native-column-test]", runtime, temporary.getRoot().toPath()));
             RealPackProbeSupport.EngineSession session = workspace.openEngine(seed, false, "columns");
             MultiPackResourceManager resources = new MultiPackResourceManager(PackType.SERVER_DATA,
                     List.of(ServerPacksSource.createVanillaPackSource().fullResources()));
             LevelStorageSource.LevelStorageAccess storage = LevelStorageSource.createDefault(
                     temporary.newFolder().toPath()).createAccess("native")) {
            Engine engine = session.engine();
            HeadlessChunkGenerator generator = spy(new HeadlessChunkGenerator(
                    new HeadlessChunkGenerator.Options(engine, runtime.registries())));
            assertTrue(generator.getBiomeSource() instanceof NativeBiomeSourceImpl);
            assertEquals(engine.getMinHeight(), generator.getMinY());
            assertEquals(engine.getHeight(), generator.getGenDepth());
            assertEquals(engine.getMinHeight() + engine.getDimension().getFluidHeight(), generator.getSeaLevel());
            Holder<NoiseGeneratorSettings> settings = runtime.registries().lookupOrThrow(Registries.NOISE_SETTINGS)
                    .getOrThrow(NoiseGeneratorSettings.OVERWORLD);
            RandomState random = RandomState.create(runtime.registries().lookupOrThrow(Registries.NOISE), seed, settings.value());
            LevelHeightAccessor height = LevelHeightAccessor.create(generator.getMinY(), generator.getGenDepth());
            Holder<Biome> swamp = runtime.registries().lookupOrThrow(Registries.BIOME).getOrThrow(
                    ResourceKey.create(Registries.BIOME, Identifier.parse("minecraft:swamp")));
            assertTrue(generator.getBiomeSource().possibleBiomes().contains(swamp));
            for (int x : new int[]{-33, 0, 49}) {
                int z = -x - 7;
                int expected = engine.getMinHeight() + engine.getHeight(x, z, true) + 1;
                assertTrue(expected > generator.getSeaLevel());
                assertEquals(expected, generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, height, random));
                NoiseColumn column = generator.getBaseColumn(x, z, height, random);
                assertFalse(column.getBlock(expected - 1).isAir());
                assertTrue(column.getBlock(expected).isAir());
                assertEquals(swamp, generator.getBiomeSource().createResolver(
                        random.createClimateSampler(SamplerContext.builder().enableCaches().build()))
                        .getNoiseBiome(x >> 2, expected >> 2, z >> 2));
            }
            Holder.Reference<Structure> hut = runtime.registries().lookupOrThrow(Registries.STRUCTURE).getOrThrow(
                    ResourceKey.create(Registries.STRUCTURE, Identifier.parse("minecraft:swamp_hut")));
            StructureTemplateManager templates = new StructureTemplateManager(resources, storage,
                    DataFixers.getDataFixer(), BuiltInRegistries.BLOCK);
            ChunkPos position = new ChunkPos(-3, 2);
            ProtoChunk chunk = new ProtoChunk(position, UpgradeData.EMPTY, height,
                    PalettedContainerFactory.create(runtime.registries()), null);
            clearInvocations(generator);
            StructureStart start = hut.value().generate(hut, Level.OVERWORLD, runtime.registries(), generator,
                    generator.getBiomeSource(), random.createClimateSampler(SamplerContext.builder().enableCaches().build()),
                    random, templates, seed, position, 0, chunk, hut.value().biomes()::contains);
            assertTrue(start.isValid());
            assertFalse(start.getPieces().isEmpty());
            verify(generator, atLeastOnce()).getBaseHeight(anyInt(), anyInt(), any(Heightmap.Types.class),
                    any(LevelHeightAccessor.class), any(RandomState.class));
            StructureStart excluded = hut.value().generate(hut, Level.OVERWORLD, runtime.registries(), generator,
                    generator.getBiomeSource(), random.createClimateSampler(SamplerContext.builder().enableCaches().build()),
                    random, templates, seed, position, 0, chunk, selected -> !selected.equals(swamp));
            assertFalse(excluded.isValid());
            assertTrue(RealPackProbeSupport.drainReported().isEmpty());
        }
    }

    private Path pack() throws Exception {
        Path pack = temporary.newFolder().toPath();
        for (String directory : List.of("dimensions", "regions", "biomes", "generators")) {
            Files.createDirectories(pack.resolve(directory));
        }
        Files.writeString(pack.resolve("dimensions/main.json"), """
                {"name":"Native columns","focus":"swamp","regions":["owned"],"fluidHeight":40,"logicalHeight":256,
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
