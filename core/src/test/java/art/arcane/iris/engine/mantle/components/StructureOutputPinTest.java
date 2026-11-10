package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.builtin.structure.StructureAnchorMode;
import art.arcane.iris.builtin.structure.StructureCarveShape;
import art.arcane.iris.builtin.structure.StructureDistribution;
import art.arcane.iris.builtin.structure.StructurePlacement;
import art.arcane.iris.builtin.structure.StructureStilts;
import art.arcane.iris.builtin.structure.StructureTerrain;
import art.arcane.iris.builtin.structure.StructureTerrainMode;
import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.BuiltInPack;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineGeneration;
import art.arcane.iris.engine.framework.IrisStructureLocator;
import art.arcane.iris.engine.framework.structure.assembly.AssembledPiece;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.framework.StructurePlacementScope;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.engine.mantle.MantleWriter;
import art.arcane.iris.engine.object.TestSettings;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.structure.IrisStructure;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawMode;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPieceEntry;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPieceRules;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPool;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawThemeSet;
import art.arcane.iris.engine.object.structure.jigsaw.JigsawJoint;
import art.arcane.iris.engine.object.structure.jigsaw.JigsawDirection;
import com.volmit.geometry.BlockPosition;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import com.google.gson.Gson;
import com.volmit.host.LoadedPack;
import com.volmit.host.ModuleLogs;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.internal.stubbing.defaultanswers.ReturnsEmptyValues;
import org.mockito.invocation.InvocationOnMock;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.when;

/**
 * Pins what the structure placements of a pack generate, so the code that places them can move without changing a world.
 * One pack of jigsaw structures (a spatial one, a planar one, a themed one and one with caps) is placed by placements of
 * every distribution and anchor the engine knows, with stilts, borings and carves, over a grid of chunks on a fixed uneven
 * terrain with a cave. Per scenario two hashes are pinned: the pieces every resolved placement assembled (object key, origin,
 * turn and bounds, in order), and every block, marker and carve the placing wrote to the mantle, in order. A mismatch prints
 * every scenario's hashes. These hashes are never re-recorded.
 */
public class StructureOutputPinTest {
    private static final long WORLD_SEED = 20261110L;
    private static final int MIN_HEIGHT = -64;
    private static final int WORLD_HEIGHT = 320;
    private static final int FLUID_HEIGHT = 63;
    private static final int GRID_RADIUS_CHUNKS = 40;
    private static final int CAVE_FLOOR_MANTLE_Y = 50;
    private static final int CAVE_CEILING_MANTLE_Y = 78;

    /** What a scenario must write besides blocks and markers: a boring or carving, or foundation columns below stilts. */
    private static final Map<String, List<String>> EXPECTED_WRITE_KINDS = Map.of(
            "themed-bore", List.of("S ", "C "),
            "caps-box-carve", List.of("S ", "C "),
            "eroded-carve", List.of("S ", "C "),
            "underground", List.of("S ", "C "));

    private static final Map<String, String> PINNED = pinned();

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final Map<String, PlatformBlockState> registryStates = new HashMap<>();
    private final Map<String, IrisObject> objects = new HashMap<>();
    private IrisData data;
    private LoadedPack pack;

    private IrisSettings previousSettings;

    @Before
    public void bindPlatform() throws IOException {
        previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        IrisPlatforms.unbind();
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.block(anyString())).thenAnswer(invocation -> registryState(invocation.getArgument(0)));
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);

        data = mock(IrisData.class);
        pack = BuiltInPack.serve(data, folder, List.of());
        writePack();
        when(data.getBlockLoader()).thenReturn(mock(art.arcane.iris.core.loader.ResourceLoader.class));
        art.arcane.iris.core.loader.ResourceLoader<IrisObject> objectLoader = mock(art.arcane.iris.core.loader.ResourceLoader.class);
        when(objectLoader.getPossibleKeys()).thenAnswer(invocation -> objects.keySet().toArray(String[]::new));
        when(objectLoader.findFile(anyString())).thenAnswer(invocation -> objectFile(invocation.getArgument(0)));
        when(data.getObjectLoader()).thenReturn(objectLoader);
        when(data.load(eq(IrisObject.class), anyString(), anyBoolean()))
                .thenAnswer(invocation -> objects.get((String) invocation.getArgument(1)));
    }

    /** The .iob file of the object {@code key}, written on first ask, which is where the graph reads an object's size from. */
    private File objectFile(String key) throws IOException {
        IrisObject object = objects.get(key);
        if (object == null) {
            return null;
        }
        File file = new File(folder.getRoot(), "objects/" + key + ".iob");
        if (!file.exists()) {
            Files.createDirectories(file.getParentFile().toPath());
            object.write(file);
        }
        return file;
    }

    @After
    public void unbindPlatform() {
        pack.close();
        IrisPlatforms.unbind();
        IrisSettings.settings = previousSettings;
    }

    @Test
    public void everyScenarioPlacesThePinnedStructures() {
        Map<String, String> actual = new LinkedHashMap<>();
        for (Map.Entry<String, StructurePlacement> scenario : scenarios().entrySet()) {
            Outcome outcome = run(scenario.getValue());
            assertTrue(scenario.getKey() + " assembled no structure", outcome.starts > 0);
            assertTrue(scenario.getKey() + " wrote nothing", !outcome.writes.isEmpty());
            for (String kind : EXPECTED_WRITE_KINDS.getOrDefault(scenario.getKey(), List.of("S ", "D "))) {
                assertTrue(scenario.getKey() + " wrote no '" + kind + "' line", outcome.writes.stream().anyMatch(line -> line.startsWith(kind)));
            }
            actual.put(scenario.getKey(), outcome.starts + "/" + hash(outcome.pieces) + "/" + hash(outcome.writes));
        }
        StringBuilder report = new StringBuilder();
        actual.forEach((name, hashes) -> report.append("                Map.entry(\"").append(name).append("\", \"")
                .append(hashes).append("\"),\n"));
        assertEquals("Pinned structure output changed:\n" + report, PINNED, actual);
    }

    private Map<String, StructurePlacement> scenarios() {
        Map<String, StructurePlacement> scenarios = new LinkedHashMap<>();
        scenarios.put("spatial", spread("pin-spatial", "pin/spatial", 10, 3));
        scenarios.put("planar", spread("pin-planar", "pin/planar", 12, 4));
        scenarios.put("themed-bore", withTerrain(spread("pin-themed", "pin/themed", 9, 2),
                terrain(StructureTerrainMode.BORE, StructureCarveShape.BOX, 2, 3, 1)));
        scenarios.put("caps-box-carve", withTerrain(spread("pin-caps", "pin/caps", 11, 3),
                terrain(StructureTerrainMode.FORCE_CARVE, StructureCarveShape.BOX, 3, 4, 2)));
        scenarios.put("rings", rings("pin-rings", "pin/spatial"));
        scenarios.put("stilts", withStilt(spread("pin-stilts", "pin/spatial", 10, 3)));
        scenarios.put("eroded-carve", withTerrain(spread("pin-eroded", "pin/spatial", 10, 3),
                terrain(StructureTerrainMode.FORCE_CARVE, StructureCarveShape.ERODED, 4, 5, 3)));
        scenarios.put("cave", cave("pin-cave", "pin/caps"));
        scenarios.put("underground", underground("pin-underground", "pin/spatial"));
        return scenarios;
    }

    private static StructurePlacement spread(String id, String structure, int spacing, int separation) {
        return placement("placementId", id, "structures", List.of(structure), "distribution", StructureDistribution.RANDOM_SPREAD,
                "spacing", spacing, "separation", separation);
    }

    private static StructurePlacement rings(String id, String structure) {
        return placement("placementId", id, "structures", List.of(structure), "distribution", StructureDistribution.CONCENTRIC_RINGS,
                "ringCount", 6, "ringDistance", 7, "ringSpread", 3);
    }

    private static StructurePlacement cave(String id, String structure) {
        return placement("placementId", id, "structures", List.of(structure), "distribution", StructureDistribution.RANDOM_SPREAD,
                "spacing", 6, "separation", 1, "underground", true, "anchor", StructureAnchorMode.CAVE_FLOOR,
                "minHeight", -20, "maxHeight", 0, "caveMinimumClearance", 5);
    }

    private static StructurePlacement underground(String id, String structure) {
        return withTerrain(placement("placementId", id, "structures", List.of(structure), "distribution", StructureDistribution.RANDOM_SPREAD,
                        "spacing", 10, "separation", 3, "underground", true, "anchor", StructureAnchorMode.HEIGHT_BAND,
                        "minHeight", -40, "maxHeight", 20),
                terrain(StructureTerrainMode.FORCE_CARVE, StructureCarveShape.ROUNDED, 2, 2, 1));
    }

    private static StructurePlacement withTerrain(StructurePlacement placement, StructureTerrain terrain) {
        return TestSettings.with(placement, "terrain", terrain);
    }

    private static StructurePlacement withStilt(StructurePlacement placement) {
        return TestSettings.with(placement, "stilt", TestSettings.with(new StructureStilts(), "maxDepth", 10));
    }

    private static StructureTerrain terrain(StructureTerrainMode mode, StructureCarveShape shape, int horizontal, int ceiling, int floor) {
        return TestSettings.with(new StructureTerrain(), "mode", mode, "shape", shape, "horizontalPadding", horizontal,
                "ceilingPadding", ceiling, "floorPadding", floor);
    }

    private static StructurePlacement placement(Object... namesAndValues) {
        return TestSettings.with(new StructurePlacement(), namesAndValues);
    }

    private Outcome run(StructurePlacement placement) {
        Engine engine = engineWith(placement);
        WriterLog log = new WriterLog();
        MantleWriter writer = recordingWriter(log, engine);
        Outcome outcome = new Outcome();
        StructurePlacing placing = new StructurePlacing(engine, ModuleLogs.discarding());
        for (int chunkX = -GRID_RADIUS_CHUNKS; chunkX < GRID_RADIUS_CHUNKS; chunkX++) {
            for (int chunkZ = -GRID_RADIUS_CHUNKS; chunkZ < GRID_RADIUS_CHUNKS; chunkZ++) {
                for (StructurePlacement scoped : StructurePlacementScope.placementsAt(engine, chunkX, chunkZ)) {
                    IrisStructureLocator.ResolvedPlacement resolved = IrisStructureLocator.resolvePlacement(engine, scoped, chunkX, chunkZ);
                    if (resolved == null) {
                        continue;
                    }
                    outcome.starts++;
                    outcome.pieces.add("start " + chunkX + "," + chunkZ + " " + resolved.structureKey() + " " + resolved.originX() + ","
                            + resolved.baseY() + "," + resolved.originZ() + " exactY=" + resolved.exactY());
                    for (AssembledPiece piece : resolved.pieces()) {
                        outcome.pieces.add(describe(piece));
                    }
                }
                placing.place(writer, chunkX, chunkZ, null);
            }
        }
        outcome.writes.addAll(log.lines);
        return outcome;
    }

    private String describe(AssembledPiece piece) {
        return data.getPackResources().keyOf(piece.object()) + " " + piece.x() + "," + piece.y() + "," + piece.z()
                + " turn=0.0," + (double) piece.turn().yDegrees() + ",0.0"
                + " box=" + piece.minX() + "," + piece.minY() + "," + piece.minZ() + ".."
                + piece.maxX() + "," + piece.maxY() + "," + piece.maxZ();
    }

    private Engine engineWith(StructurePlacement placement) {
        Engine engine = mock(Engine.class, withSettings().stubOnly());
        IrisDimension dimension = mock(IrisDimension.class, withSettings().stubOnly());
        EngineMantle engineMantle = mock(EngineMantle.class, withSettings().stubOnly());
        @SuppressWarnings("unchecked")
        Mantle<Matter> mantle = mock(Mantle.class, withSettings().stubOnly());
        when(mantle.get(anyInt(), anyInt(), anyInt(), eq(MatterCavern.class))).thenAnswer(invocation -> caveAt(invocation.getArgument(1)));
        when(engineMantle.getMantle()).thenReturn(mantle);
        when(engineMantle.trueHeight(anyInt(), anyInt())).thenAnswer(invocation -> terrainHeight(invocation.getArgument(0), invocation.getArgument(1)));
        when(engine.getMantle()).thenReturn(engineMantle);
        when(engine.getData()).thenReturn(data);
        when(data.getEngine()).thenReturn(engine);
        when(engine.getSeedManager()).thenReturn(new SeedManager(WORLD_SEED));
        when(engine.getMinHeight()).thenReturn(MIN_HEIGHT);
        when(engine.getHeight()).thenReturn(WORLD_HEIGHT);
        when(engine.getHeight(anyInt(), anyInt(), anyBoolean()))
                .thenAnswer(invocation -> terrainHeight(invocation.getArgument(0), invocation.getArgument(1)));
        when(engine.getHeight(anyInt(), anyInt()))
                .thenAnswer(invocation -> terrainHeight(invocation.getArgument(0), invocation.getArgument(1)));
        when(dimension.getFluidHeight()).thenReturn(FLUID_HEIGHT);
        when(dimension.getModuleSettings()).thenReturn(BuiltInPack.settings(
                TestSettings.with(new art.arcane.iris.builtin.structure.StructurePlacementSettings(), "placements", new ArrayList<>(List.of(placement)))));
        when(dimension.getAllRegions(any())).thenReturn(new KList<>());
        when(dimension.getReachableBiomes(any())).thenReturn(new KList<>());
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getGeneration()).thenReturn(new EngineGeneration(engine));
        return engine;
    }

    /** Uneven terrain, as a height above the dimension's bottom: it climbs and falls by up to nine blocks over a few columns. */
    private static int terrainHeight(int x, int z) {
        return 128 + Math.floorMod(x * 3 + z * 5, 9) + Math.floorMod(x / 7 - z / 5, 4);
    }

    private static MatterCavern caveAt(int mantleY) {
        return mantleY >= CAVE_FLOOR_MANTLE_Y && mantleY <= CAVE_CEILING_MANTLE_Y ? new MatterCavern(true, "", (byte) 3) : null;
    }

    private static MantleWriter recordingWriter(WriterLog log, Engine engine) {
        return mock(MantleWriter.class, withSettings().stubOnly().defaultAnswer(invocation -> log.answer(invocation, engine)));
    }

    private final class WriterLog {
        private final Map<String, PlatformBlockState> written = new HashMap<>();
        private final List<String> lines = new ArrayList<>();
        private final ReturnsEmptyValues empty = new ReturnsEmptyValues();

        Object answer(InvocationOnMock invocation, Engine engine) throws Throwable {
            Object[] arguments = invocation.getArguments();
            switch (invocation.getMethod().getName()) {
                case "getEngine":
                    return engine;
                case "getHighest":
                    return terrainHeight((Integer) arguments[0], (Integer) arguments[1]);
                case "get":
                    return stateAt((Integer) arguments[0], (Integer) arguments[1], (Integer) arguments[2]);
                case "set":
                    return set(arguments);
                case "setData":
                    lines.add("D " + position(arguments) + "=" + describeData(arguments[3]));
                    return null;
                case "setTile":
                    lines.add("T " + position(arguments));
                    return null;
                case "setForcedCarve":
                    lines.add("C " + position(arguments));
                    written.remove(position(arguments));
                    return null;
                case "clearBlock":
                    lines.add("X " + position(arguments));
                    written.remove(position(arguments));
                    return null;
                case "clearData":
                    lines.add("R " + position(arguments) + "=" + ((Class<?>) arguments[3]).getSimpleName());
                    return null;
                case "getDataIfPresent":
                case "getData":
                    return dataAt(arguments);
                case "isSolid":
                    return stateAt((Integer) arguments[0], (Integer) arguments[1], (Integer) arguments[2]).isSolid();
                case "isSurfaceSolid":
                    return true;
                case "getFluidHeight":
                    return FLUID_HEIGHT - MIN_HEIGHT;
                case "acquireChunk":
                    return null;
                default:
                    return empty.answer(invocation);
            }
        }

        private Object set(Object[] arguments) {
            if (arguments.length != 4 || !(arguments[3] instanceof PlatformBlockState state)) {
                return null;
            }
            lines.add("S " + position(arguments) + "=" + state.key());
            written.put(position(arguments), state);
            return null;
        }

        private Object dataAt(Object[] arguments) {
            Class<?> type = (Class<?>) arguments[3];
            if (type == PlatformBlockState.class) {
                return written.get(position(arguments));
            }
            return null;
        }

        private PlatformBlockState stateAt(int x, int y, int z) {
            PlatformBlockState state = written.get(x + "," + y + "," + z);
            if (state != null) {
                return state;
            }
            boolean ground = y <= terrainHeight(x, z) && (caveAt(y) == null);
            return registryState(ground ? "minecraft:stone" : "minecraft:air");
        }

        private static String position(Object[] arguments) {
            return arguments[0] + "," + arguments[1] + "," + arguments[2];
        }

        private static String describeData(Object value) {
            return value instanceof String text ? "String:" + text : value == null ? "null" : value.getClass().getSimpleName();
        }
    }

    private PlatformBlockState registryState(String key) {
        String normalized = key.toLowerCase();
        String namespaced = normalized.contains(":") ? normalized : "minecraft:" + normalized;
        return registryStates.computeIfAbsent(namespaced, value -> state(value));
    }

    private static PlatformBlockState state(String key) {
        boolean air = key.contains("air");
        boolean solid = !air && !key.contains("water") && !key.contains("snow") && !key.contains("glass");
        PlatformBlockState state = mock(PlatformBlockState.class, withSettings().stubOnly());
        when(state.key()).thenReturn(key);
        when(state.materialKey()).thenReturn(key);
        when(state.isSolid()).thenReturn(solid);
        when(state.isOccluding()).thenReturn(solid);
        when(state.isAir()).thenReturn(air);
        when(state.isStorageChest()).thenReturn(key.endsWith(":chest"));
        return state;
    }

    private void writePack() throws IOException {
        Gson gson = new Gson();
        Map<String, IrisStructure> structures = new LinkedHashMap<>();
        Map<String, IrisJigsawPool> pools = new LinkedHashMap<>();
        Map<String, IrisJigsawPiece> pieces = new LinkedHashMap<>();
        pools.put("terminal", new IrisJigsawPool());

        defineSpatial(structures, pools, pieces);
        definePlanar(structures, pools, pieces);
        defineThemed(structures, pools, pieces);
        defineCaps(structures, pools, pieces);

        write(gson, "structures", structures);
        write(gson, "jigsaw-pools", pools);
        write(gson, "jigsaw-pieces", pieces);
    }

    private void defineSpatial(Map<String, IrisStructure> structures, Map<String, IrisJigsawPool> pools, Map<String, IrisJigsawPiece> pieces) {
        pools.put("pin/spatial/start", pool("", "pin/spatial/hall"));
        pools.put("pin/spatial/arms", pool("", "pin/spatial/wing"));
        pools.put("pin/spatial/tails", pool("", "pin/spatial/tower"));
        pools.put("pin/spatial/ends", pool("", "pin/spatial/cap"));
        pieces.put("pin/spatial/hall", piece("pin/spatial/hall", true,
                connector(JigsawDirection.EAST_POSITIVE_X, "pin/spatial/arms", "hall-east", "door", 8, 1, 4),
                connector(JigsawDirection.WEST_NEGATIVE_X, "pin/spatial/tails", "hall-west", "tail", 0, 1, 4)));
        pieces.put("pin/spatial/wing", piece("pin/spatial/wing", true,
                connector(JigsawDirection.WEST_NEGATIVE_X, "terminal", "door", "unused", 0, 1, 2),
                connector(JigsawDirection.EAST_POSITIVE_X, "pin/spatial/ends", "wing-east", "door", 6, 1, 2)));
        pieces.put("pin/spatial/tower", piece("pin/spatial/tower", true,
                connector(JigsawDirection.EAST_POSITIVE_X, "terminal", "tail", "unused", 2, 1, 1)));
        pieces.put("pin/spatial/cap", piece("pin/spatial/cap", true,
                connector(JigsawDirection.WEST_NEGATIVE_X, "terminal", "door", "unused", 0, 1, 1)));
        objects.put("pin/spatial/hall", building("pin/spatial/hall", 9, 5, 9, "oak_planks", true));
        objects.put("pin/spatial/wing", building("pin/spatial/wing", 7, 4, 5, "spruce_planks", false));
        objects.put("pin/spatial/tower", building("pin/spatial/tower", 3, 8, 3, "stone_bricks", true));
        objects.put("pin/spatial/cap", building("pin/spatial/cap", 3, 3, 3, "cobblestone", false));
        structures.put("pin/spatial", structure("pin/spatial/start", 3, 6));
    }

    private void definePlanar(Map<String, IrisStructure> structures, Map<String, IrisJigsawPool> pools, Map<String, IrisJigsawPiece> pieces) {
        pools.put("pin/planar/start", pool("", "pin/planar/start"));
        pools.put("pin/planar/north", pool("", "pin/planar/north"));
        pools.put("pin/planar/east", pool("", "pin/planar/east"));
        pools.put("pin/planar/south", pool("", "pin/planar/south"));
        pools.put("pin/planar/west", pool("", "pin/planar/west"));
        pieces.put("pin/planar/start", piece("pin/planar/start", false,
                connector(JigsawDirection.NORTH_NEGATIVE_Z, "pin/planar/north", "start", "door", 8, 4, 0),
                connector(JigsawDirection.EAST_POSITIVE_X, "pin/planar/east", "start", "door", 15, 4, 8),
                connector(JigsawDirection.SOUTH_POSITIVE_Z, "pin/planar/south", "start", "door", 8, 4, 15),
                connector(JigsawDirection.WEST_NEGATIVE_X, "pin/planar/west", "start", "door", 0, 4, 8)));
        pieces.put("pin/planar/north", piece("pin/planar/north", false,
                connector(JigsawDirection.SOUTH_POSITIVE_Z, "terminal", "door", "", 8, 4, 15)));
        pieces.put("pin/planar/east", piece("pin/planar/east", false,
                connector(JigsawDirection.WEST_NEGATIVE_X, "terminal", "door", "", 0, 4, 8)));
        pieces.put("pin/planar/south", piece("pin/planar/south", false,
                connector(JigsawDirection.NORTH_NEGATIVE_Z, "terminal", "door", "", 8, 4, 0)));
        pieces.put("pin/planar/west", piece("pin/planar/west", false,
                connector(JigsawDirection.EAST_POSITIVE_X, "terminal", "door", "", 15, 4, 8)));
        objects.put("pin/planar/start", building("pin/planar/start", 16, 8, 16, "oak_planks", true));
        objects.put("pin/planar/north", building("pin/planar/north", 16, 8, 16, "spruce_planks", false));
        objects.put("pin/planar/east", building("pin/planar/east", 16, 8, 16, "birch_planks", false));
        objects.put("pin/planar/south", building("pin/planar/south", 16, 8, 16, "dark_oak_planks", true));
        objects.put("pin/planar/west", building("pin/planar/west", 16, 8, 16, "acacia_planks", false));
        structures.put("pin/planar", structure("pin/planar/start", 1, 8)
                .setMode(IrisJigsawMode.PLANAR_JIGSAW).setCellSize(new BlockPosition(16, 8, 16)));
    }

    private void defineThemed(Map<String, IrisStructure> structures, Map<String, IrisJigsawPool> pools, Map<String, IrisJigsawPiece> pieces) {
        pools.put("pin/themed/start", pool("", "pin/themed/first", "pin/themed/second"));
        IrisJigsawPiece first = piece("pin/themed/first", false);
        first.getThemes().add("variant-1");
        IrisJigsawPiece second = piece("pin/themed/second", true);
        second.getThemes().add("variant-2");
        pieces.put("pin/themed/first", first);
        pieces.put("pin/themed/second", second);
        objects.put("pin/themed/first", building("pin/themed/first", 7, 6, 7, "oak_planks", true));
        objects.put("pin/themed/second", building("pin/themed/second", 5, 9, 5, "stone_bricks", false));
        IrisStructure structure = structure("pin/themed/start", 1, 4);
        structure.getThemeSets().add(new IrisJigsawThemeSet("variant-1", 1));
        structure.getThemeSets().add(new IrisJigsawThemeSet("variant-2", 1));
        structures.put("pin/themed", structure);
    }

    private void defineCaps(Map<String, IrisStructure> structures, Map<String, IrisJigsawPool> pools, Map<String, IrisJigsawPiece> pieces) {
        pools.put("pin/caps/start", pool("", "pin/caps/start"));
        pools.put("pin/caps/branch", pool("pin/caps/caps", "pin/caps/incompatible"));
        pools.put("pin/caps/caps", pool("", "pin/caps/cap"));
        pieces.put("pin/caps/start", piece("pin/caps/start", false,
                connector(JigsawDirection.EAST_POSITIVE_X, "pin/caps/branch", "start", "door", 6, 1, 2),
                connector(JigsawDirection.WEST_NEGATIVE_X, "pin/caps/branch", "start", "door", 0, 1, 2)));
        pieces.put("pin/caps/incompatible", piece("pin/caps/incompatible", false,
                connector(JigsawDirection.WEST_NEGATIVE_X, "terminal", "wrong", "", 0, 1, 1)));
        IrisJigsawPiece cap = piece("pin/caps/cap", false,
                connector(JigsawDirection.WEST_NEGATIVE_X, "terminal", "door", "", 0, 1, 1),
                connector(JigsawDirection.EAST_POSITIVE_X, "terminal", "door", "", 2, 1, 1));
        cap.setRules(new IrisJigsawPieceRules().setTerminal(true));
        pieces.put("pin/caps/cap", cap);
        objects.put("pin/caps/start", building("pin/caps/start", 7, 5, 5, "oak_planks", true));
        objects.put("pin/caps/incompatible", building("pin/caps/incompatible", 3, 3, 3, "stone", false));
        objects.put("pin/caps/cap", building("pin/caps/cap", 3, 4, 3, "cobblestone", true));
        structures.put("pin/caps", structure("pin/caps/start", 2, 6).setRequireCaps(true));
    }

    private static IrisStructure structure(String startPool, int maxDepth, int maxSizeChunks) {
        return new IrisStructure().setStartPool(startPool).setMaxDepth(maxDepth).setMaxSizeChunks(maxSizeChunks);
    }

    private static IrisJigsawPool pool(String fallback, String... pieceKeys) {
        IrisJigsawPool pool = new IrisJigsawPool().setFallback(fallback);
        for (String pieceKey : pieceKeys) {
            pool.getPieces().add(new IrisJigsawPieceEntry().setPiece(pieceKey).setWeight(1));
        }
        return pool;
    }

    private static IrisJigsawPiece piece(String object, boolean rotatable, IrisJigsawConnector... connectors) {
        IrisJigsawPiece piece = new IrisJigsawPiece().setObject(object).setRotatable(rotatable);
        piece.getConnectors().addAll(List.of(connectors));
        return piece;
    }

    private static IrisJigsawConnector connector(JigsawDirection direction, String pool, String name, String targetName, int x, int y, int z) {
        return new IrisJigsawConnector()
                .setPosition(new BlockPosition(x, y, z))
                .setDirection(direction)
                .setPool(pool)
                .setName(name)
                .setTargetName(targetName)
                .setJoint(JigsawJoint.ROLLABLE);
    }

    /** A hollow building with a doorway in the middle of two opposite walls, a window row and, optionally, a chest by the door. */
    private IrisObject building(String key, int width, int height, int depth, String wall, boolean chest) {
        IrisObject object = new IrisObject(width, height, depth);
        object.setLoadKey(key);
        PlatformBlockState floor = registryState("minecraft:stone");
        PlatformBlockState walls = registryState("minecraft:" + wall);
        PlatformBlockState glass = registryState("minecraft:glass");
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                if ((x + z) % 5 != 4) {
                    object.setUnsigned(x, 0, z, floor);
                }
                for (int y = 1; y < height; y++) {
                    boolean shell = x == 0 || x == width - 1 || z == 0 || z == depth - 1 || y == height - 1;
                    boolean doorway = (x == 0 || x == width - 1) && z == depth / 2 && y < 3;
                    if (shell && !doorway) {
                        object.setUnsigned(x, y, z, y == 2 && (x + z) % 2 == 0 && y < height - 1 ? glass : walls);
                    }
                }
            }
        }
        if (chest && width > 3 && depth > 3) {
            object.setUnsigned(1, 1, 1, registryState("minecraft:chest"));
        }
        return object;
    }

    private void write(Gson gson, String folderName, Map<String, ?> resources) throws IOException {
        for (Map.Entry<String, ?> resource : resources.entrySet()) {
            Path file = folder.getRoot().toPath().resolve(folderName).resolve(resource.getKey() + ".json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, gson.toJson(resource.getValue()), StandardCharsets.UTF_8);
        }
    }

    private static String hash(List<String> lines) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String line : lines) {
                digest.update(line.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            }
            return lines.size() + ":" + HexFormat.of().formatHex(digest.digest()).substring(0, 24);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static final class Outcome {
        private int starts;
        private final List<String> pieces = new ArrayList<>();
        private final List<String> writes = new ArrayList<>();
    }

    private static Map<String, String> pinned() {
        return Map.ofEntries(
                Map.entry("spatial", "64/320:2332f4bcffe3b09749ed5af3/26944:38d50ba318e070eba6a3db62"),
                Map.entry("planar", "42/252:e15ff2ebb66f87363d66c207/171738:de11453c28c528afb56c68d1"),
                Map.entry("themed-bore", "78/156:282a92cb631ae1c15e65af1f/102483:1fb6c4122cf54a848ae89e8a"),
                Map.entry("caps-box-carve", "54/216:862f59ec9d164ed5feb37322/133812:b1023f34a227e6c5b8fcff9d"),
                Map.entry("rings", "6/30:e31d276ce85624af3ba14642/2526:bc0ff7eae5831894052e6343"),
                Map.entry("stilts", "64/320:1a39c3f3255c55b16e8188a1/40214:840373ed96bd4916b98fe6c5"),
                Map.entry("eroded-carve", "64/320:ad8a6ff5c913c07ac5bf245a/167070:f9106559688ccd4c4f39aa26"),
                Map.entry("cave", "175/700:09e748228682b3073b879ec8/31325:5cd287cc7c0a3e42319e7493"),
                Map.entry("underground", "64/320:131ff0564f8624534b2f660d/127534:9176f55f60410c117cdd9d5e"));
    }
}
