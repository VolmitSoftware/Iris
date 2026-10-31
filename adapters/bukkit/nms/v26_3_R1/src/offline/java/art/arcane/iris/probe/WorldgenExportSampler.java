package art.arcane.iris.probe;

import art.arcane.iris.pack.datapack.export.WorldgenExportMode;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.LevelStorageSource;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class WorldgenExportSampler implements AutoCloseable {
    private static final String DENSITY_FUNCTIONS = "/worldgen/density_function/";
    private static final int CHUNK_WIDTH = 16;
    private static final Method CREATE_NOISE_CHUNK = generatorMethod("createNoiseChunk", ChunkAccess.class, StructureManager.class, Blender.class,
            RandomState.class, NoiseSettings.class);
    private static final Method DO_FILL = generatorMethod("doFill", NoiseChunk.class, ChunkAccess.class);

    private final HeadlessNativeRegistries loaded;
    private final NoiseBasedChunkGenerator generator;
    private final RandomState randomState;
    private final ThreadLocal<BiomeResolver> resolvers;
    private final LevelHeightAccessor heights;
    private final SortedSet<String> densityFunctionIds;
    private final ThreadLocal<SamplerContext> densityContexts = ThreadLocal.withInitial(() -> SamplerContext.builder().enableCaches().build());
    private final PalettedContainerFactory containers;
    private final long seed;
    private final long biomeZoomSeed;

    private WorldgenExportSampler(HeadlessNativeRegistries loaded, LevelStem stem, long seed, SortedSet<String> densityFunctionIds) {
        this.loaded = loaded;
        if (!(stem.generator() instanceof NoiseBasedChunkGenerator noiseGenerator)) {
            throw new IllegalStateException("Exported dimension uses " + stem.generator().getClass().getSimpleName() + ", not a noise generator");
        }
        this.generator = noiseGenerator;
        this.randomState = RandomState.create(loaded.registries().lookupOrThrow(Registries.NOISE), seed, noiseGenerator.generatorSettings().value());
        this.resolvers = ThreadLocal.withInitial(() -> noiseGenerator.getBiomeSource().createCachingResolver(randomState));
        DimensionType type = stem.type().value();
        this.heights = LevelHeightAccessor.create(type.minY(), type.height());
        this.densityFunctionIds = densityFunctionIds;
        this.containers = PalettedContainerFactory.create(loaded.registries());
        this.seed = seed;
        this.biomeZoomSeed = BiomeManager.obfuscateSeed(seed);
    }

    public static WorldgenExportSampler open(Map<String, byte[]> files, WorldgenExportMode mode, String dimensionId, long seed) throws IOException {
        Objects.requireNonNull(mode);
        Identifier dimension = Identifier.parse(dimensionId);
        HeadlessNativeBootstrap.initialize();
        Path directory = Files.createTempDirectory("iris-worldgen-export-");
        HeadlessNativeRegistries loaded;
        try {
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                Path target = directory.resolve(file.getKey()).normalize();
                if (!target.startsWith(directory)) {
                    throw new IllegalArgumentException("Export file escapes the datapack root: " + file.getKey());
                }
                Files.createDirectories(target.getParent());
                Files.write(target, file.getValue());
            }
            try (PaperRegistryScope scope = PaperRegistryScope.open()) {
                loaded = HeadlessNativeRegistries.load(directory);
            }
        } catch (IOException | RuntimeException failure) {
            HeadlessNativeRegistries.delete(directory);
            throw failure;
        }
        try {
            return new WorldgenExportSampler(loaded, stem(loaded.registries(), mode, dimension), seed, densityFunctionIds(files));
        } catch (RuntimeException failure) {
            loaded.close();
            throw failure;
        }
    }

    public static WorldgenExportSampler open(Path datapack, WorldgenExportMode mode, String dimensionId, long seed) throws IOException {
        return open(files(datapack), mode, dimensionId, seed);
    }

    public float density(String functionId, int x, int y, int z) {
        DensityFunction function = loaded.registries().lookupOrThrow(Registries.DENSITY_FUNCTION)
                .getOrThrow(ResourceKey.create(Registries.DENSITY_FUNCTION, Identifier.parse(functionId))).value();
        return randomState.getSampler(function).sampleValue(densityContexts.get(), x, y, z);
    }

    public float[] densityChunk(DensityFunction function, int chunkX, int chunkZ) {
        DensityVolume volume = new DensityVolume(CHUNK_WIDTH, heights.getHeight(), CHUNK_WIDTH, chunkX * CHUNK_WIDTH, heights.getMinY(), chunkZ * CHUNK_WIDTH);
        return sampleVolume(function, volume);
    }

    public DensityFunction finalDensity() {
        return generator.generatorSettings().value().noiseRouter().finalDensity();
    }

    public int baseHeight(int x, int z) {
        return generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, heights, randomState);
    }

    public int floorHeight(int x, int z) {
        return generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, heights, randomState);
    }

    public ProtoChunk terrainChunk(int chunkX, int chunkZ) {
        ProtoChunk chunk = new ProtoChunk(new ChunkPos(chunkX, chunkZ), UpgradeData.EMPTY, heights, containers, null);
        NoiseGeneratorSettings settings = generator.generatorSettings().value();
        NoiseSettings noise = settings.noiseSettings().clampToHeightAccessor(chunk.getHeightAccessorForGeneration());
        try (NoiseChunk noiseChunk = (NoiseChunk) invoke(CREATE_NOISE_CHUNK, chunk, NoStructures.INSTANCE, Blender.empty(), randomState, noise)) {
            invoke(DO_FILL, noiseChunk, chunk);
            randomState.surfaceSystem().buildSurface(randomState, new BiomeManager(resolvers.get(), biomeZoomSeed),
                    new WorldGenerationContext(generator, chunk.getHeightAccessorForGeneration()), chunk, noiseChunk, settings.materialRule().value(), null);
        }
        return chunk;
    }

    public StructureStart structureStart(String structureId, int chunkX, int chunkZ) throws IOException {
        Holder.Reference<Structure> structure = loaded.registries().lookupOrThrow(Registries.STRUCTURE)
                .getOrThrow(ResourceKey.create(Registries.STRUCTURE, Identifier.parse(structureId)));
        Path storageRoot = Files.createTempDirectory("iris-worldgen-export-structures-");
        try (LevelStorageSource.LevelStorageAccess storage = LevelStorageSource.createDefault(storageRoot).createAccess("export")) {
            StructureTemplateManager templates = new StructureTemplateManager(loaded.resources(), storage, DataFixers.getDataFixer(),
                    loaded.registries().lookupOrThrow(Registries.BLOCK));
            return structure.value().generate(structure, Level.OVERWORLD, loaded.registries(), generator, generator.getBiomeSource(),
                    randomState.createClimateSampler(SamplerContext.builder().enableCaches().build()), randomState, templates, seed,
                    new ChunkPos(chunkX, chunkZ), 0, heights, structure.value().biomes()::contains);
        } finally {
            HeadlessNativeRegistries.delete(storageRoot);
        }
    }

    public String biome(int x, int y, int z) {
        Holder<Biome> biome = resolvers.get().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
        return biome.unwrapKey().orElseThrow().identifier().toString();
    }

    public Set<String> possibleBiomes() {
        Set<String> biomes = new TreeSet<>();
        for (Holder<Biome> biome : generator.getBiomeSource().possibleBiomes()) {
            biomes.add(biome.unwrapKey().orElseThrow().identifier().toString());
        }
        return biomes;
    }

    public SortedSet<String> densityFunctionIds() {
        return densityFunctionIds;
    }

    public int minY() {
        return heights.getMinY();
    }

    public int height() {
        return heights.getHeight();
    }

    public int seaLevel() {
        return generator.getSeaLevel();
    }

    public RegistryAccess registries() {
        return loaded.registries();
    }

    @Override
    public void close() throws IOException {
        loaded.close();
    }

    private float[] sampleVolume(DensityFunction function, DensityVolume volume) {
        try (ScopedDensityBuffer buffer = randomState.getSampler(function).bind(densityContexts.get()).sampleVolume(volume)) {
            float[] values = new float[volume.size()];
            for (int index = 0; index < values.length; index++) {
                values[index] = buffer.get(index);
            }
            return values;
        }
    }

    private Object invoke(Method method, Object... arguments) {
        try {
            return method.invoke(generator, arguments);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (failure.getCause() instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Terrain stage " + method.getName() + " failed", failure.getCause());
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Terrain stage " + method.getName() + " is not accessible", failure);
        }
    }

    private static Method generatorMethod(String name, Class<?>... parameters) {
        try {
            Method method = NoiseBasedChunkGenerator.class.getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException failure) {
            throw new IllegalStateException("NoiseBasedChunkGenerator has no terrain stage " + name, failure);
        }
    }

    private static LevelStem stem(RegistryAccess registries, WorldgenExportMode mode, Identifier dimension) {
        return switch (mode) {
            case PRESET -> registries.lookupOrThrow(Registries.WORLD_PRESET)
                    .getOrThrow(ResourceKey.create(Registries.WORLD_PRESET, dimension)).value().overworld()
                    .orElseThrow(() -> new IllegalStateException("World preset " + dimension + " has no overworld"));
            case DIMENSION -> levelStem(registries, dimension);
            case OVERWORLD -> levelStem(registries, LevelStem.OVERWORLD.identifier());
        };
    }

    private static LevelStem levelStem(RegistryAccess registries, Identifier id) {
        return registries.lookupOrThrow(Registries.LEVEL_STEM).getOrThrow(ResourceKey.create(Registries.LEVEL_STEM, id)).value();
    }

    private static SortedMap<String, byte[]> files(Path datapack) throws IOException {
        if (!Files.isDirectory(datapack)) {
            throw new IOException("Exported datapack " + datapack + " is not a directory");
        }
        SortedMap<String, byte[]> files = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(datapack)) {
            List<Path> regular = paths.filter(Files::isRegularFile).toList();
            for (Path file : regular) {
                files.put(datapack.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file));
            }
        }
        return files;
    }

    private static SortedSet<String> densityFunctionIds(Map<String, byte[]> files) {
        SortedSet<String> ids = new TreeSet<>();
        for (String path : files.keySet()) {
            int marker = path.indexOf(DENSITY_FUNCTIONS);
            if (path.startsWith("data/") && marker > 0 && path.endsWith(".json")) {
                String namespace = path.substring("data/".length(), marker);
                ids.add(namespace + ":" + path.substring(marker + DENSITY_FUNCTIONS.length(), path.length() - ".json".length()));
            }
        }
        return ids;
    }

    private static final class NoStructures extends StructureManager {
        private static final NoStructures INSTANCE = new NoStructures();

        private NoStructures() {
            super(null, null, null);
        }

        @Override
        public List<StructureStart> startsForStructure(int chunkX, int chunkZ, Predicate<Structure> predicate) {
            return List.of();
        }
    }
}
