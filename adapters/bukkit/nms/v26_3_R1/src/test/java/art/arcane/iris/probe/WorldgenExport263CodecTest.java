package art.arcane.iris.probe;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.AtomicDatapackPublisher;
import art.arcane.iris.pack.datapack.export.DatapackExportFormat;
import art.arcane.iris.pack.datapack.export.ExportIrFixtures;
import art.arcane.iris.pack.datapack.export.GenerationCost;
import art.arcane.iris.pack.datapack.export.WorldgenDatapackExporter;
import art.arcane.iris.pack.datapack.export.WorldgenExportMode;
import art.arcane.iris.pack.datapack.export.WorldgenExportProgress;
import art.arcane.iris.pack.datapack.export.WorldgenExportRequest;
import art.arcane.iris.pack.datapack.export.WorldgenExportResult;
import art.arcane.iris.pack.datapack.export.compile.PackCompiler;
import art.arcane.iris.pack.datapack.export.compile.surface.MaterialProgramCompiler;
import art.arcane.iris.pack.datapack.export.compile.terrain.FieldMacros;
import art.arcane.iris.pack.datapack.export.compile.terrain.HeightCompiler;
import art.arcane.iris.pack.datapack.export.emit.NestingSplitter;
import art.arcane.iris.pack.datapack.export.emit.WorldgenJsonWriter;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportIr;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.FieldEvaluator;
import art.arcane.iris.pack.datapack.export.ir.NoiseDef;
import art.arcane.iris.pack.datapack.export.lower.GenerationBudget;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.WorldPresetTags;
import net.minecraft.util.Interval;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.op.BinaryFunction;
import net.minecraft.world.level.levelgen.material.rule.OreVeinRule;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Enumeration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class WorldgenExport263CodecTest {
    @ClassRule
    public static final PaperRegistryIsolation registryIsolation = new PaperRegistryIsolation();

    @ClassRule
    public static final TemporaryFolder overworldFolder = new TemporaryFolder();

    private static final long SEED = 1337L;
    private static final String DIMENSION_ID = ExportIrFixtures.NAMESPACE + ":" + ExportIrFixtures.DIMENSION;
    private static final String HEIGHT_ID = ExportIrFixtures.NAMESPACE + ":" + ExportIrFixtures.DIMENSION + "/height";
    private static final double SURFACE_TOLERANCE = 2.0;
    private static final String BIOME_PREFIX = "data/" + ExportIrFixtures.NAMESPACE + "/worldgen/biome/";
    private static final int[] SAMPLE_Y = {-64, 0, 64, 200};
    private static final int LATTICE = 16;
    private static final int LATTICE_STEP = 61;
    private static final int HEIGHT_POINTS = 8;
    private static final int HEIGHT_STEP = 97;
    private static final String PROBE_PACK = "iris.exportProbePack";
    private static final String OVERWORLD = "overworld";
    private static final String OVERWORLD_ID = WorldgenExportRequest.DEFAULT_NAMESPACE + ":" + OVERWORLD;
    private static final String OVERWORLD_BIOME_PREFIX = "data/" + WorldgenExportRequest.DEFAULT_NAMESPACE + "/worldgen/biome/";
    private static final String LOG_PREFIX = "[worldgen-export]";
    private static final int OVERWORLD_LATTICE = 4;
    private static final int OVERWORLD_STEP = 509;
    private static final int MAX_NESTING = 255;
    private static final String CAVE_FIELD = "cave/field";
    private static final String WALL_FALLBACK = "uses the nearest kept palette (minecraft:";
    private static final double WALL_SHELL_BAND_LIMIT = 0.25;
    private static final String DENSITY_FUNCTION_RESOURCE = "worldgen/density_function";
    private static final double MEGABYTE = 1024.0 * 1024.0;
    private static final int OVERWORLD_LAVA_Y = -248;
    private static final int LAVA_LINE = -54;
    private static final int DEEP_CHUNKS = 2;
    private static final int DEEP_STRIDE = 4;
    private static final int CHUNK_WIDTH = 16;
    private static final float OPEN_MARGIN = 1.0e-3f;
    private static final int LAVA_SEARCH_RADIUS = 1024;
    private static final int LAVA_SEARCH_STEP = 16;
    private static final int LAVA_CELLS = 4;
    private static final int AGREEMENT_POINTS = 16;
    private static final double AGREEMENT_TOLERANCE = 1.0;
    private static final int AGREEMENT_OUTLIERS = 2;
    private static final String SUBTERRAIN_STRUCTURES = "data/" + WorldgenExportRequest.DEFAULT_NAMESPACE + "/worldgen/structure/subterrain/";
    private static final String SUBTERRAIN_TEMPLATES = "data/" + WorldgenExportRequest.DEFAULT_NAMESPACE + "/structure/subterrain/";
    private static final int ROOM_GRID = 24;
    private static final int ROOM_STEP_CHUNKS = 8;
    private static final int ROOM_ATTEMPTS = 4;
    private static final int ROOM_REACH = 128;
    private static final Map<WorldgenExportMode, SortedMap<String, byte[]>> FILES = new EnumMap<>(WorldgenExportMode.class);
    private static final Map<WorldgenExportMode, WorldgenExportSampler> SAMPLERS = new EnumMap<>(WorldgenExportMode.class);
    private static SortedMap<String, byte[]> exportedOverworld;
    private static WorldgenExportResult exportedOverworldResult;
    private static ExportIr exportedOverworldIr;

    @BeforeClass
    public static void loadExports() throws IOException {
        for (WorldgenExportMode mode : WorldgenExportMode.values()) {
            SortedMap<String, byte[]> files = WorldgenJsonWriter.forTarget(DataVersion.V26_3).write(ExportIrFixtures.flatNoise(DataVersion.V26_3, mode));
            FILES.put(mode, files);
            SAMPLERS.put(mode, WorldgenExportSampler.open(files, mode, DIMENSION_ID, SEED));
        }
    }

    @AfterClass
    public static void closeExports() throws IOException {
        for (WorldgenExportSampler sampler : SAMPLERS.values()) {
            sampler.close();
        }
        SAMPLERS.clear();
        FILES.clear();
        exportedOverworld = null;
        exportedOverworldResult = null;
        exportedOverworldIr = null;
    }

    @Test
    public void fixtureLoadsInEveryMode() {
        for (WorldgenExportMode mode : WorldgenExportMode.values()) {
            WorldgenExportSampler sampler = SAMPLERS.get(mode);
            assertEquals(ExportIrFixtures.MIN_Y, sampler.minY());
            assertEquals(ExportIrFixtures.HEIGHT, sampler.height());
            assertEquals(ExportIrFixtures.SEA_LEVEL, sampler.seaLevel());
            assertFalse(sampler.densityFunctionIds().isEmpty());
            for (String id : sampler.densityFunctionIds()) {
                assertTrue(mode + " " + id, sampler.registries().lookupOrThrow(Registries.DENSITY_FUNCTION).containsKey(Identifier.parse(id)));
            }
            assertTrue(sampler.registries().lookupOrThrow(Registries.NOISE_SETTINGS).containsKey(Identifier.parse(DIMENSION_ID)));
            assertTrue(sampler.registries().lookupOrThrow(Registries.DIMENSION_TYPE).containsKey(Identifier.parse(DIMENSION_ID)));
            assertTrue(sampler.registries().lookupOrThrow(Registries.MATERIAL_RULE).containsKey(Identifier.parse(DIMENSION_ID)));
            boolean preset = sampler.registries().lookupOrThrow(Registries.WORLD_PRESET).containsKey(Identifier.parse(DIMENSION_ID));
            boolean dimension = sampler.registries().lookupOrThrow(Registries.LEVEL_STEM).containsKey(Identifier.parse(DIMENSION_ID));
            boolean overworld = sampler.registries().lookupOrThrow(Registries.LEVEL_STEM).containsKey(LevelStem.OVERWORLD.identifier());
            assertEquals(mode.toString(), mode == WorldgenExportMode.PRESET, preset);
            assertEquals(mode.toString(), mode == WorldgenExportMode.DIMENSION, dimension);
            assertEquals(mode.toString(), mode == WorldgenExportMode.OVERWORLD, overworld);
        }
        WorldgenExportSampler presetSampler = SAMPLERS.get(WorldgenExportMode.PRESET);
        Holder<WorldPreset> preset = presetSampler.registries().lookupOrThrow(Registries.WORLD_PRESET)
                .getOrThrow(ResourceKey.create(Registries.WORLD_PRESET, Identifier.parse(DIMENSION_ID)));
        assertTrue(preset.value().createWorldDimensions().get(LevelStem.NETHER).isPresent());
        assertTrue(preset.value().createWorldDimensions().get(LevelStem.END).isPresent());
        HolderSet.Named<WorldPreset> normal = presetSampler.registries().lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresetTags.NORMAL);
        assertTrue(normal.contains(preset));
    }

    @Test
    public void everyDensityFunctionEvaluatesFinite() {
        for (WorldgenExportMode mode : WorldgenExportMode.values()) {
            WorldgenExportSampler sampler = SAMPLERS.get(mode);
            for (String id : sampler.densityFunctionIds()) {
                for (int y : SAMPLE_Y) {
                    for (int xIndex = 0; xIndex < LATTICE; xIndex++) {
                        for (int zIndex = 0; zIndex < LATTICE; zIndex++) {
                            int x = (xIndex - LATTICE / 2) * LATTICE_STEP;
                            int z = (zIndex - LATTICE / 2) * LATTICE_STEP;
                            float value = sampler.density(id, x, y, z);
                            assertTrue(mode + " " + id + " at " + x + "," + y + "," + z + " = " + value, Float.isFinite(value));
                        }
                    }
                }
            }
        }
    }

    @Test
    public void baseHeightWithinDimension() {
        for (WorldgenExportMode mode : WorldgenExportMode.values()) {
            WorldgenExportSampler sampler = SAMPLERS.get(mode);
            Set<Integer> heights = new HashSet<>();
            int land = 0;
            for (int xIndex = 0; xIndex < HEIGHT_POINTS; xIndex++) {
                for (int zIndex = 0; zIndex < HEIGHT_POINTS; zIndex++) {
                    int x = (xIndex - HEIGHT_POINTS / 2) * HEIGHT_STEP;
                    int z = (zIndex - HEIGHT_POINTS / 2) * HEIGHT_STEP;
                    int height = sampler.baseHeight(x, z);
                    assertTrue(mode + " height " + height + " at " + x + "," + z,
                            height >= sampler.minY() && height <= sampler.minY() + sampler.height());
                    float surface = sampler.density(HEIGHT_ID, x, 0, z);
                    int floor = sampler.floorHeight(x, z);
                    assertTrue(mode + " height " + height + " below floor " + floor, height >= floor);
                    if (surface > sampler.seaLevel() + 1) {
                        assertTrue(mode + " floor " + floor + " against surface " + surface, Math.abs(floor - surface) <= SURFACE_TOLERANCE);
                        land++;
                    }
                    heights.add(height);
                }
            }
            assertTrue(mode + " surface never varies", heights.size() > 1);
            assertTrue(mode + " has no land column above sea level", land > 0);
        }
    }

    @Test
    public void resolverReturnsOnlyExportedBiomes() {
        for (WorldgenExportMode mode : WorldgenExportMode.values()) {
            WorldgenExportSampler sampler = SAMPLERS.get(mode);
            Set<String> exported = exportedBiomes(FILES.get(mode), BIOME_PREFIX, ExportIrFixtures.NAMESPACE);
            assertFalse(exported.isEmpty());
            assertTrue(mode + " " + sampler.possibleBiomes(), exported.containsAll(sampler.possibleBiomes()));
            for (int y : SAMPLE_Y) {
                for (int xIndex = 0; xIndex < LATTICE; xIndex++) {
                    for (int zIndex = 0; zIndex < LATTICE; zIndex++) {
                        String biome = sampler.biome((xIndex - LATTICE / 2) * LATTICE_STEP, y, (zIndex - LATTICE / 2) * LATTICE_STEP);
                        assertTrue(mode + " " + biome, exported.contains(biome));
                    }
                }
            }
        }
    }

    @Test
    public void overworldPackLoads() throws Exception {
        SortedMap<String, byte[]> files = overworldFiles();
        assertGenerationBudget(exportedOverworldResult);
        assertWallPalettesFallBack(exportedOverworldResult);
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            if (file.getKey().endsWith(".json")) {
                int depth = NestingSplitter.depth(JsonParser.parseString(new String(file.getValue(), StandardCharsets.UTF_8)));
                assertTrue(file.getKey() + " nests " + depth, depth <= MAX_NESTING);
            }
            if (file.getKey().endsWith(".nbt")) {
                assertCompleteGzip(file.getKey(), file.getValue());
            }
        }
        try (WorldgenExportSampler sampler = WorldgenExportSampler.open(files, WorldgenExportMode.OVERWORLD, OVERWORLD_ID, SEED)) {
            Set<String> roots = new TreeSet<>();
            for (String id : sampler.densityFunctionIds()) {
                if (id.startsWith(OVERWORLD_ID + "/")) {
                    roots.add(id);
                }
            }
            assertTrue(roots.toString(), roots.contains(OVERWORLD_ID + "/final_density"));
            for (String id : roots) {
                for (int y : SAMPLE_Y) {
                    for (int xIndex = 0; xIndex < OVERWORLD_LATTICE; xIndex++) {
                        for (int zIndex = 0; zIndex < OVERWORLD_LATTICE; zIndex++) {
                            int x = (xIndex - OVERWORLD_LATTICE / 2) * OVERWORLD_STEP;
                            int z = (zIndex - OVERWORLD_LATTICE / 2) * OVERWORLD_STEP;
                            float value = sampler.density(id, x, y, z);
                            assertTrue(id + " at " + x + "," + y + "," + z + " = " + value, Float.isFinite(value));
                        }
                    }
                }
            }
            assertNaturalHeightMatchesTheEvaluator(exportedOverworldIr, sampler);
            Set<String> exported = exportedBiomes(files, OVERWORLD_BIOME_PREFIX, WorldgenExportRequest.DEFAULT_NAMESPACE);
            assertFalse(exported.isEmpty());
            assertTrue(sampler.possibleBiomes().toString(), exported.containsAll(sampler.possibleBiomes()));
            for (int xIndex = 0; xIndex < HEIGHT_POINTS; xIndex++) {
                for (int zIndex = 0; zIndex < HEIGHT_POINTS; zIndex++) {
                    int x = (xIndex - HEIGHT_POINTS / 2) * HEIGHT_STEP;
                    int z = (zIndex - HEIGHT_POINTS / 2) * HEIGHT_STEP;
                    int height = sampler.baseHeight(x, z);
                    assertTrue("height " + height + " at " + x + "," + z, height >= sampler.minY() && height <= sampler.minY() + sampler.height());
                    for (int y : SAMPLE_Y) {
                        String biome = sampler.biome(x, y, z);
                        assertTrue(biome + " at " + x + "," + y + "," + z, exported.contains(biome));
                    }
                }
            }
        }
    }

    @Test
    public void overworldHasNoDisjointMinMax() throws Exception {
        SortedMap<String, byte[]> files = overworldFiles();
        try (WorldgenExportSampler sampler = WorldgenExportSampler.open(files, WorldgenExportMode.OVERWORLD, OVERWORLD_ID, SEED)) {
            MinMaxAudit audit = new MinMaxAudit();
            for (String id : sampler.densityFunctionIds()) {
                audit.rewrite(sampler.registries().lookupOrThrow(Registries.DENSITY_FUNCTION)
                        .getOrThrow(ResourceKey.create(Registries.DENSITY_FUNCTION, Identifier.parse(id))).value());
            }
            System.out.println(LOG_PREFIX + " min/max nodes " + audit.checked() + ", disjoint " + audit.disjoint().size());
            assertTrue(audit.checked() > 0);
            assertTrue(audit.disjoint().size() + " disjoint min/max nodes: " + audit.disjoint(), audit.disjoint().isEmpty());
        }
    }

    @Test
    public void overworldDeepCavesOpenWithoutLava() throws Exception {
        SortedMap<String, byte[]> files = overworldFiles();
        String field = WorldgenExportRequest.DEFAULT_NAMESPACE + ":" + CAVE_FIELD;
        long open = 0L;
        long lava = 0L;
        try (WorldgenExportSampler sampler = WorldgenExportSampler.open(files, WorldgenExportMode.OVERWORLD, OVERWORLD_ID, SEED)) {
            assertTrue(field, sampler.densityFunctionIds().contains(field));
            BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
            for (int chunkX = -DEEP_CHUNKS; chunkX < DEEP_CHUNKS; chunkX++) {
                for (int chunkZ = -DEEP_CHUNKS; chunkZ < DEEP_CHUNKS; chunkZ++) {
                    ProtoChunk chunk = sampler.terrainChunk(chunkX, chunkZ);
                    for (int x = 0; x < CHUNK_WIDTH; x++) {
                        for (int z = 0; z < CHUNK_WIDTH; z++) {
                            for (int y = OVERWORLD_LAVA_Y + 1; y < LAVA_LINE; y++) {
                                BlockState state = chunk.getBlockState(position.set(x, y, z));
                                assertFalse(state + " at chunk " + chunkX + "," + chunkZ + " " + x + "," + y + "," + z, state.is(Blocks.LAVA));
                            }
                        }
                    }
                    for (int x = 0; x < CHUNK_WIDTH; x += DEEP_STRIDE) {
                        for (int z = 0; z < CHUNK_WIDTH; z += DEEP_STRIDE) {
                            int blockX = (chunkX << 4) + x;
                            int blockZ = (chunkZ << 4) + z;
                            for (int y = OVERWORLD_LAVA_Y + 1; y < LAVA_LINE; y++) {
                                if (sampler.density(field, blockX, y, blockZ) > -OPEN_MARGIN) {
                                    continue;
                                }
                                BlockState state = chunk.getBlockState(position.set(x, y, z));
                                assertTrue(state + " at " + blockX + "," + y + "," + blockZ, state.isAir());
                                open++;
                            }
                        }
                    }
                }
            }
            for (BlockPos cell : openLavaBandCells(sampler, field)) {
                ProtoChunk chunk = sampler.terrainChunk(cell.getX() >> 4, cell.getZ() >> 4);
                BlockState state = chunk.getBlockState(position.set(cell.getX() & (CHUNK_WIDTH - 1), cell.getY(), cell.getZ() & (CHUNK_WIDTH - 1)));
                assertTrue(state + " at " + cell, state.is(Blocks.LAVA));
                lava++;
            }
        }
        System.out.println(LOG_PREFIX + " deep cave cells open " + open + ", lava band cells " + lava);
        assertTrue("no deep cave cell opened", open > 0L);
        assertTrue("no lava band cell opened", lava > 0L);
    }

    @Test
    public void overworldMaterialPassComputesTheCaveFieldOnce() throws Exception {
        SortedMap<String, byte[]> files = overworldFiles();
        try (WorldgenExportSampler sampler = WorldgenExportSampler.open(files, WorldgenExportMode.OVERWORLD, OVERWORLD_ID, SEED)) {
            CompiledCaches caches = CompiledCaches.open(sampler.registries(), SEED);
            int field = caches.rootId(caches.function(WorldgenExportRequest.DEFAULT_NAMESPACE + ":" + CAVE_FIELD));
            List<OreVeinRule> veins = caches.oreVeins();
            DensityFunction opening = veins.getFirst().density();
            assertTrue(caches.ids(opening).contains(field));
            assertTrue(caches.ids(caches.finalDensity()).contains(field));
            Set<Integer> caveCaches = caches.ids(caches.function(WorldgenExportRequest.DEFAULT_NAMESPACE + ":" + CAVE_FIELD));
            int shells = 0;
            for (OreVeinRule vein : veins) {
                if (!vein.oreBlock().isAir()) {
                    assertTrue("wall shell " + shells + " " + vein.oreBlock(), caches.ids(vein.density()).contains(field));
                    assertTrue("wall shell " + shells + " " + vein.oreBlock(), caveCaches.containsAll(caches.ids(vein.density())));
                    shells++;
                }
            }
            JsonObject shell = JsonParser.parseString(new String(files.get("data/" + WorldgenExportRequest.DEFAULT_NAMESPACE + "/" + DENSITY_FUNCTION_RESOURCE
                    + "/" + MaterialProgramCompiler.WALL_SHELL + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
            double band = shell.get("max_exclusive").getAsDouble();
            System.out.println(LOG_PREFIX + " material pass: " + veins.size() + " ore vein rules, " + shells + " wall shells read cave field cache " + field
                    + " within band " + band);
            assertTrue(String.valueOf(shells), shells > 0);
            assertTrue(String.valueOf(band), band > 0.0 && band < WALL_SHELL_BAND_LIMIT);
        }
    }

    @Test
    public void overworldSubterrainRoomsPlace() throws Exception {
        SortedMap<String, byte[]> files = overworldFiles();
        SortedMap<String, JsonObject> structures = new TreeMap<>();
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            if (file.getKey().startsWith(SUBTERRAIN_STRUCTURES) && file.getKey().endsWith(".json")) {
                String path = file.getKey().substring(SUBTERRAIN_STRUCTURES.length(), file.getKey().length() - ".json".length());
                structures.put(WorldgenExportRequest.DEFAULT_NAMESPACE + ":subterrain/" + path,
                        JsonParser.parseString(new String(file.getValue(), StandardCharsets.UTF_8)).getAsJsonObject());
            }
        }
        long templateBytes = 0L;
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            if (file.getKey().startsWith(SUBTERRAIN_TEMPLATES)) {
                templateBytes += file.getValue().length;
            }
        }
        System.out.println(LOG_PREFIX + " subterrain structures " + structures.size() + ", structure sets "
                + count(files, "/worldgen/structure_set/subterrain/", ".json") + ", templates " + count(files, "/structure/subterrain/", ".nbt")
                + ", template bytes " + templateBytes);
        assertFalse(structures.isEmpty());
        try (WorldgenExportSampler sampler = WorldgenExportSampler.open(files, WorldgenExportMode.OVERWORLD, OVERWORLD_ID, SEED)) {
            for (Map.Entry<String, JsonObject> entry : structures.entrySet()) {
                Structure structure = sampler.registries().lookupOrThrow(Registries.STRUCTURE)
                        .getOrThrow(ResourceKey.create(Registries.STRUCTURE, Identifier.parse(entry.getKey()))).value();
                JsonObject height = entry.getValue().getAsJsonObject("start_height");
                int y = (height.getAsJsonObject("min_inclusive").get("absolute").getAsInt()
                        + height.getAsJsonObject("max_inclusive").get("absolute").getAsInt()) / 2;
                List<ChunkPos> matches = new ArrayList<>();
                for (int xIndex = 0; xIndex < ROOM_GRID; xIndex++) {
                    for (int zIndex = 0; zIndex < ROOM_GRID; zIndex++) {
                        ChunkPos chunk = new ChunkPos((xIndex - ROOM_GRID / 2) * ROOM_STEP_CHUNKS, (zIndex - ROOM_GRID / 2) * ROOM_STEP_CHUNKS);
                        String biome = sampler.biome(chunk.getMinBlockX(), y, chunk.getMinBlockZ());
                        Holder<Biome> holder = sampler.registries().lookupOrThrow(Registries.BIOME)
                                .getOrThrow(ResourceKey.create(Registries.BIOME, Identifier.parse(biome)));
                        if (structure.biomes().contains(holder)) {
                            matches.add(chunk);
                        }
                    }
                }
                System.out.println(String.format(Locale.ROOT, "%s subterrain %s biome match %d/%d at y %d", LOG_PREFIX, entry.getKey(),
                        matches.size(), ROOM_GRID * ROOM_GRID, y));
                assertFalse(entry.getKey() + " finds no biome at y " + y, matches.isEmpty());
                StructureStart start = null;
                for (ChunkPos chunk : matches.subList(0, Math.min(ROOM_ATTEMPTS, matches.size()))) {
                    StructureStart candidate = sampler.structureStart(entry.getKey(), chunk.x(), chunk.z());
                    if (candidate.isValid()) {
                        start = candidate;
                        BoundingBox box = start.getBoundingBox();
                        assertTrue(entry.getKey() + " " + box, chunk.getMinBlockX() - box.minX() <= ROOM_REACH && box.maxX() - chunk.getMinBlockX() <= ROOM_REACH);
                        assertTrue(entry.getKey() + " " + box, chunk.getMinBlockZ() - box.minZ() <= ROOM_REACH && box.maxZ() - chunk.getMinBlockZ() <= ROOM_REACH);
                        assertTrue(entry.getKey() + " " + box, box.minY() >= sampler.minY() && box.maxY() < sampler.minY() + sampler.height());
                        break;
                    }
                }
                assertTrue(entry.getKey() + " never starts", start != null);
            }
        }
    }

    private static List<BlockPos> openLavaBandCells(WorldgenExportSampler sampler, String field) {
        List<BlockPos> cells = new ArrayList<>(LAVA_CELLS);
        for (int x = -LAVA_SEARCH_RADIUS; x < LAVA_SEARCH_RADIUS; x += LAVA_SEARCH_STEP) {
            for (int z = -LAVA_SEARCH_RADIUS; z < LAVA_SEARCH_RADIUS; z += LAVA_SEARCH_STEP) {
                for (int y = sampler.minY(); y <= OVERWORLD_LAVA_Y; y++) {
                    if (sampler.density(field, x, y, z) > -OPEN_MARGIN) {
                        continue;
                    }
                    cells.add(new BlockPos(x, y, z));
                    if (cells.size() == LAVA_CELLS) {
                        return cells;
                    }
                }
            }
        }
        return cells;
    }

    private static synchronized SortedMap<String, byte[]> overworldFiles() throws Exception {
        String probePack = System.getProperty(PROBE_PACK);
        Assume.assumeTrue("Set -P" + PROBE_PACK + " to export a real pack", probePack != null && !probePack.isBlank());
        if (exportedOverworld != null) {
            return exportedOverworld;
        }
        Path root = overworldFolder.newFolder("exports").toPath();
        SortedMap<String, byte[]> untrimmed = new TreeMap<>();
        AtomicReference<ExportIr> compiled = new AtomicReference<>();
        WorldgenExportResult result;
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(new RealPackProbeSupport.WorkspaceOptions(
                new File(probePack), OVERWORLD, LOG_PREFIX, new HeadlessNativeRuntime(), overworldFolder.newFolder("workspace").toPath()))) {
            WorldgenExportRequest request = WorldgenExportRequest.builder()
                    .pack(workspace.pack().toPath())
                    .dimension(OVERWORLD)
                    .exportRoot(root)
                    .objects(true)
                    .build();
            result = new WorldgenDatapackExporter(target -> ir -> {
                compiled.set(ir);
                SortedMap<String, byte[]> written = WorldgenJsonWriter.forTarget(target).write(ir);
                untrimmed.putAll(written);
                return written;
            }, PackCompiler.create()).export(request, WorldgenExportProgress.NONE);
        }
        assertTrue(result.message() + " " + result.diagnostics(), result.isSuccess());
        SortedMap<String, byte[]> files = zipEntries(result.output());
        Path untrimmedZip = root.resolve("untrimmed.zip");
        new AtomicDatapackPublisher().publish(untrimmed, untrimmedZip, DatapackExportFormat.ZIP, false, ".untrimmed-");
        report(result, untrimmed, Files.size(untrimmedZip), files);
        exportedOverworld = files;
        exportedOverworldResult = result;
        exportedOverworldIr = compiled.get();
        return files;
    }

    private static void assertGenerationBudget(WorldgenExportResult result) {
        GenerationCost cost = result.cost();
        Set<String> flagged = new TreeSet<>();
        for (ExportDiagnostic diagnostic : result.diagnostics()) {
            if (diagnostic.limit() == ExportLimit.GENERATION_COST) {
                flagged.add(diagnostic.resource());
            }
        }
        for (GenerationCost.Root root : GenerationCost.Root.values()) {
            GenerationCost.RootCost rootCost = cost.root(root).orElseThrow();
            System.out.println(String.format(Locale.ROOT, "%s cost %s worst case %.3e expanded %.3e distinct %d", LOG_PREFIX, root.id(),
                    rootCost.worstCaseNodes(), rootCost.expandedNodes(), rootCost.distinctNodes()));
        }
        for (GenerationCost.Root root : GenerationCost.Root.values()) {
            if (!root.climate()) {
                continue;
            }
            double limit = GenerationBudget.worstCaseLimit(root).orElseThrow();
            double nodes = cost.root(root).orElseThrow().worstCaseNodes();
            String resource = "noise_settings/" + OVERWORLD + "/" + root.id();
            assertTrue(root.id() + " evaluates " + nodes + " nodes, budget " + limit, nodes <= limit);
            assertFalse(resource + " " + flagged, flagged.contains(resource));
        }
        long finalDensity = cost.root(GenerationCost.Root.FINAL_DENSITY).orElseThrow().distinctNodes();
        String finalResource = "noise_settings/" + OVERWORLD + "/" + GenerationCost.Root.FINAL_DENSITY.id();
        System.out.println(String.format(Locale.ROOT, "%s final_density distinct %d of %d, density function JSON %.1f of %.1f MB", LOG_PREFIX, finalDensity,
                GenerationBudget.FINAL_DENSITY_DISTINCT_NODES, cost.densityFunctionBytes() / MEGABYTE, GenerationBudget.DENSITY_FUNCTION_JSON_BYTES / MEGABYTE));
        assertEquals(finalResource + " " + flagged, finalDensity > GenerationBudget.FINAL_DENSITY_DISTINCT_NODES, flagged.contains(finalResource));
        assertTrue(cost.densityFunctionBytes() + " bytes", cost.densityFunctionBytes() > 0L);
        assertTrue(cost.densityFunctionBytes() + " bytes", cost.densityFunctionBytes() <= GenerationBudget.DENSITY_FUNCTION_JSON_BYTES);
        assertFalse(flagged.toString(), flagged.contains(DENSITY_FUNCTION_RESOURCE));
    }

    private static void assertWallPalettesFallBack(WorldgenExportResult result) {
        int fallbacks = 0;
        for (ExportDiagnostic diagnostic : result.diagnostics()) {
            if (diagnostic.limit() == ExportLimit.WALL_PALETTE && diagnostic.severity() == ExportDiagnostic.Severity.WARNING) {
                assertTrue(diagnostic.resource() + " " + diagnostic.message(), diagnostic.message().contains(WALL_FALLBACK));
                fallbacks++;
            }
        }
        System.out.println(LOG_PREFIX + " wall palettes over the ore vein budget " + fallbacks);
    }

    private static void assertNaturalHeightMatchesTheEvaluator(ExportIr ir, WorldgenExportSampler sampler) {
        Field.Shared height = shared(ir.router().chunkSurfaceLevel(), HeightCompiler.HEIGHT);
        Map<String, NoiseDef> noises = new HashMap<>();
        for (NoiseDef noise : ir.noises()) {
            noises.put(ir.namespace() + ":" + noise.id(), noise);
        }
        String heightId = ir.namespace() + ":" + HeightCompiler.HEIGHT;
        int outliers = 0;
        StringBuilder detail = new StringBuilder();
        for (int xIndex = 0; xIndex < AGREEMENT_POINTS; xIndex++) {
            for (int zIndex = 0; zIndex < AGREEMENT_POINTS; zIndex++) {
                int x = (xIndex - AGREEMENT_POINTS / 2) * OVERWORLD_STEP;
                int z = (zIndex - AGREEMENT_POINTS / 2) * OVERWORLD_STEP;
                double evaluated = FieldEvaluator.sample(height.expr(), noises, SEED, DataVersion.V26_3, x, 0, z);
                double exported = sampler.density(heightId, x, 0, z);
                if (Math.abs(evaluated - exported) > AGREEMENT_TOLERANCE) {
                    outliers++;
                    detail.append(' ').append(x).append(',').append(z).append('=').append(evaluated).append('/').append(exported);
                }
            }
        }
        assertTrue(outliers + " columns differ:" + detail, outliers <= AGREEMENT_OUTLIERS);
    }

    private static Field.Shared shared(Field root, String name) {
        Set<Field> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Field> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            Field field = pending.pop();
            if (!visited.add(field)) {
                continue;
            }
            if (field instanceof Field.Shared candidate && candidate.name().equals(name)) {
                return candidate;
            }
            for (Field child : FieldMacros.children(field)) {
                pending.push(child);
            }
        }
        throw new AssertionError("Router has no shared field " + name);
    }

    private static void assertCompleteGzip(String path, byte[] bytes) throws IOException {
        try (InputStream input = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            input.readAllBytes();
            assertEquals(path, -1, input.read());
        }
    }

    private static void report(WorldgenExportResult result, SortedMap<String, byte[]> untrimmed, long untrimmedZipBytes,
                               SortedMap<String, byte[]> files) throws IOException {
        SortedMap<ExportLimit, Integer> limits = new TreeMap<>();
        for (ExportDiagnostic diagnostic : result.diagnostics()) {
            limits.merge(diagnostic.limit(), 1, Integer::sum);
        }
        System.out.println(LOG_PREFIX + " untrimmed zip bytes " + untrimmedZipBytes + ", files " + untrimmed.size()
                + ", trimmed zip bytes " + Files.size(result.output()) + ", files " + files.size() + ", trim " + result.trim());
        System.out.println(LOG_PREFIX + " diagnostics " + result.diagnostics().size() + " " + limits);
        reportCounts("untrimmed", untrimmed);
        reportCounts("trimmed", files);
    }

    private static void reportCounts(String label, SortedMap<String, byte[]> files) {
        System.out.println(LOG_PREFIX + " " + label + " templates " + count(files, "/structure/", ".nbt")
                + ", structures " + count(files, "/worldgen/structure/", ".json")
                + ", structure sets " + count(files, "/worldgen/structure_set/", ".json")
                + ", placed features " + count(files, "/worldgen/placed_feature/", ".json")
                + ", features " + count(files, "/worldgen/feature/", ".json")
                + ", density functions " + count(files, "/worldgen/density_function/", ".json")
                + ", noises " + count(files, "/worldgen/noise/", ".json")
                + ", biomes " + count(files, "/worldgen/biome/", ".json"));
    }

    private static int count(SortedMap<String, byte[]> files, String folder, String suffix) {
        int count = 0;
        for (String path : files.keySet()) {
            if (path.contains(folder) && path.endsWith(suffix)) {
                count++;
            }
        }
        return count;
    }

    private static SortedMap<String, byte[]> zipEntries(Path zip) throws IOException {
        SortedMap<String, byte[]> files = new TreeMap<>();
        try (ZipFile archive = new ZipFile(zip.toFile())) {
            Enumeration<? extends ZipEntry> entries = archive.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory()) {
                    files.put(entry.getName(), archive.getInputStream(entry).readAllBytes());
                }
            }
        }
        return files;
    }

    private static Set<String> exportedBiomes(SortedMap<String, byte[]> files, String prefix, String namespace) {
        Set<String> biomes = new TreeSet<>();
        for (String path : files.keySet()) {
            if (path.startsWith(prefix)) {
                biomes.add(namespace + ":" + path.substring(prefix.length(), path.length() - ".json".length()));
            }
        }
        return biomes;
    }

    private static final class MinMaxAudit implements DfRewriteRule {
        private final Set<DensityFunction> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        private final List<String> disjoint = new ArrayList<>();
        private int checked;

        @Override
        public DensityFunction rewrite(DensityFunction function) {
            if (!visited.add(function)) {
                return function;
            }
            if (function instanceof BinaryFunction binary && (binary.type() == BinaryFunction.Type.MIN || binary.type() == BinaryFunction.Type.MAX)) {
                checked++;
                Interval left = binary.left().range();
                Interval right = binary.right().range();
                if (left.max() < right.min() || right.max() < left.min()) {
                    disjoint.add(binary.type() + " " + left + " " + right);
                }
            }
            function.rewriteChildren(this);
            return function;
        }

        private int checked() {
            return checked;
        }

        private List<String> disjoint() {
            return disjoint;
        }
    }
}
