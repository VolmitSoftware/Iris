package art.arcane.iris.structure.object;

import art.arcane.iris.generation.block.BlockDataMergeSupport;
import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.generation.cave.CarvingMode;
import art.arcane.iris.generation.decoration.IrisStiltSettings;
import art.arcane.iris.generation.decoration.IrisVacuumSettings;
import art.arcane.iris.generation.decoration.tree.TreeBlockCanvas;
import art.arcane.iris.generation.decoration.tree.TreeGrounding;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.pack.validation.ContentGate;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.iris.world.entity.IrisMarker;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.math.BlockPosition;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.MatterMarker;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class ObjectPlacementGoldenTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    private static final int OBJECTS = 200;
    private static final int SEEDS = 5;
    private static final long GOLDEN = 0x408f9d4113015897L;
    private static final long MARKER_GOLDEN = 0x60b4bbf1144b239cL;

    private static final List<String> PALETTE = List.of(
            "minecraft:stone",
            "minecraft:dirt",
            "minecraft:grass_block[snowy=false]",
            "minecraft:oak_log[axis=y]",
            "minecraft:oak_log[axis=x]",
            "minecraft:oak_leaves[distance=3,persistent=false,waterlogged=false]",
            "minecraft:oak_leaves[distance=1,persistent=true,waterlogged=false]",
            "minecraft:oak_leaves[distance=7,persistent=false,waterlogged=true]",
            "minecraft:oak_stairs[facing=north,half=bottom,waterlogged=false]",
            "minecraft:oak_stairs[facing=east,half=top,waterlogged=true]",
            "minecraft:vine[east=false,north=true,south=false,up=false,west=false]",
            "minecraft:air",
            "minecraft:cave_air",
            "minecraft:chest[facing=south,waterlogged=false]",
            "minecraft:lantern[hanging=false,waterlogged=false]",
            "minecraft:cobblestone",
            "minecraft:glass",
            "minecraft:oak_slab[type=bottom,waterlogged=false]");

    private final Map<String, GoldenState> states = new ConcurrentHashMap<>();
    private IrisData data;
    private IrisData objectData;
    private Engine engine;
    private IrisObjectRotation.StateRotator previousRotator;
    private BlockDataMergeSupport.StateMerger previousMerger;

    @Before
    public void bindPlatform() {
        IrisPlatforms.unbind();
        PlatformRegistries registries = mock(PlatformRegistries.class, withSettings().stubOnly());
        when(registries.block(anyString())).thenAnswer(invocation -> state(invocation.getArgument(0)));
        when(registries.blockOrNull(anyString())).thenAnswer(invocation -> state(invocation.getArgument(0)));
        when(registries.blockOrNull(anyString(), anyBoolean())).thenAnswer(invocation -> state(invocation.getArgument(0)));
        when(registries.air()).thenAnswer(invocation -> state("minecraft:air"));
        IrisPlatform platform = mock(IrisPlatform.class, withSettings().stubOnly());
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);
        previousRotator = IrisObjectRotation.bindPlatformRotator(ObjectPlacementGoldenTest::rotateState);
        previousMerger = BlockDataMergeSupport.bindPlatformMerger(this::mergeStates);

        ProceduralStream<NativeBlockState> rock = mock(ProceduralStream.class, withSettings().stubOnly());
        when(rock.get(anyDouble(), anyDouble())).thenAnswer(invocation -> state("minecraft:stone"));
        IrisComplex complex = mock(IrisComplex.class, withSettings().stubOnly());
        when(complex.getRockStream()).thenReturn(rock);
        IrisDimension dimension = mock(IrisDimension.class, withSettings().stubOnly());
        engine = mock(Engine.class, withSettings().stubOnly());
        when(engine.getHeight()).thenReturn(256);
        when(engine.getMinHeight()).thenReturn(0);
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getDimension()).thenReturn(dimension);
        ContentGate gate = mock(ContentGate.class, withSettings().stubOnly());
        when(gate.ready()).thenReturn(false);
        data = mock(IrisData.class, withSettings().stubOnly());
        when(data.getEngine()).thenReturn(engine);
        when(data.getContentGate()).thenReturn(gate);
        objectData = mock(IrisData.class, withSettings().stubOnly());
        when(objectData.getContentGate()).thenReturn(gate);
    }

    @After
    public void unbindPlatform() {
        IrisObjectRotation.restorePlatformRotator(previousRotator);
        BlockDataMergeSupport.restorePlatformMerger(previousMerger);
        IrisPlatforms.unbind();
    }

    @Test
    public void placementsMatchTheRecordedBlockStream() throws IOException {
        Random random = new Random(20261021L);
        List<IrisObjectRotation> rotations = rotations();
        long combined = 17L;
        StringBuilder dump = new StringBuilder();
        for (int index = 0; index < OBJECTS; index++) {
            IrisObject object = randomObject(random, index);
            object.setLoader(objectData);
            IrisObjectPlacement base = randomPlacement(random);
            for (int rotationIndex = 0; rotationIndex < rotations.size(); rotationIndex++) {
                IrisObjectPlacement placement = base.toPlacement("golden");
                placement.setRotation(rotations.get(rotationIndex));
                boolean explicitY = (index + rotationIndex) % 3 == 0;
                for (int seed = 0; seed < SEEDS; seed++) {
                    RNG rng = new RNG(index * 1_000_003L + rotationIndex * 7919L + seed);
                    GoldenPlacer placer = new GoldenPlacer(engine, (index & 1) == 0);
                    List<String> listened = new ArrayList<>();
                    int x = 400 + index * 3 + seed;
                    int z = -900 + rotationIndex * 5 - seed;
                    int y = explicitY ? 90 + seed : -1;
                    int result;
                    if (placement.getMode() == ObjectPlaceMode.ORGANIC_STILT && explicitY && seed % 2 == 0) {
                        result = object.placeOnFloor(x, 70, z, placer, placement, rng,
                                (position, state) -> listened.add(position.getX() + "," + position.getY() + "," + position.getZ() + "=" + state.key()),
                                data);
                    } else {
                        result = object.place(x, y, z, placer, placement, rng,
                                (position, state) -> listened.add(position.getX() + "," + position.getY() + "," + position.getZ() + "=" + state.key()),
                                null, data);
                    }
                    long hash = placer.hash;
                    hash = mix(hash, result);
                    hash = mix(hash, rng.nextLong());
                    for (String entry : listened) {
                        hash = mix(hash, entry.hashCode());
                    }
                    hash = mix(hash, listened.size());
                    combined = mix(combined, hash);
                    dump.append(index).append(' ').append(rotationIndex).append(' ').append(seed).append(' ')
                            .append(Long.toHexString(hash)).append(' ').append(placer.writes).append('\n');
                }
            }
        }
        String dumpPath = System.getenv("IRIS_OBJECT_GOLDEN_DUMP");
        if (dumpPath != null && !dumpPath.isBlank()) {
            Files.writeString(Path.of(dumpPath), dump.toString());
        }
        assertEquals(Long.toHexString(GOLDEN), Long.toHexString(combined));
    }

    @Test
    public void markedPlacementsMatchTheRecordedBlockStream() {
        ResourceLoader<IrisMarker> markers = mock(ResourceLoader.class, withSettings().stubOnly());
        IrisMarker anywhere = new IrisMarker();
        anywhere.setEmptyAbove(false);
        when(markers.load("anywhere")).thenReturn(anywhere);
        when(markers.load("open")).thenReturn(new IrisMarker());
        when(objectData.getMarkerLoader()).thenReturn(markers);
        Random random = new Random(77L);
        List<IrisObjectRotation> rotations = rotations();
        long combined = 23L;
        int markerWrites = 0;
        for (int index = 0; index < 40; index++) {
            IrisObject object = randomObject(random, index);
            object.setLoader(objectData);
            IrisObjectPlacement base = new IrisObjectPlacement();
            base.setMode(ObjectPlaceMode.STRUCTURE_PIECE);
            base.setRequireSurfaceSupport(false);
            IrisObjectMarker loose = new IrisObjectMarker();
            loose.setMarker("anywhere");
            loose.setMaximumMarkers(1 + random.nextInt(6));
            loose.setMark(new KList<>(new IrisBlockData("minecraft:stone"), new IrisBlockData("minecraft:oak_leaves")));
            IrisObjectMarker exact = new IrisObjectMarker();
            exact.setMarker("open");
            exact.setExact(true);
            exact.setMark(new KList<>(new IrisBlockData("minecraft:oak_log[axis=y]")));
            base.setMarkers(new KList<>(loose, exact));
            for (int rotationIndex = 0; rotationIndex < rotations.size(); rotationIndex++) {
                IrisObjectPlacement placement = base.toPlacement("marked");
                placement.setRotation(rotations.get(rotationIndex));
                for (int seed = 0; seed < 3; seed++) {
                    RNG rng = new RNG(index * 31L + rotationIndex * 7L + seed);
                    GoldenPlacer placer = new GoldenPlacer(engine, false);
                    int result = object.place(100 + index, 80 + seed, 200 - index, placer, placement, rng, null, null, data);
                    long hash = mix(placer.hash, result);
                    hash = mix(hash, rng.nextLong());
                    combined = mix(combined, hash);
                    markerWrites += placer.markerWrites;
                }
            }
        }
        assertTrue(markerWrites > 100);
        assertEquals(Long.toHexString(MARKER_GOLDEN), Long.toHexString(combined));
    }

    private static long mix(long hash, long value) {
        long mixed = (hash ^ value) * 0x9E3779B97F4A7C15L;
        return mixed ^ (mixed >>> 29);
    }

    private List<IrisObjectRotation> rotations() {
        IrisObjectRotation defaultY = new IrisObjectRotation();
        IrisObjectRotation lockedQuarter = IrisObjectRotation.of(0D, 90D, 0D);
        IrisObjectRotation flipped = rotation(new IrisAxisRotationClamp(true, true, 180D, 180D, 0D),
                new IrisAxisRotationClamp(true, false, 0D, 0D, 90D), new IrisAxisRotationClamp());
        IrisObjectRotation angled = rotation(new IrisAxisRotationClamp(true, false, 0D, 0D, 90D),
                new IrisAxisRotationClamp(true, false, 0D, 0D, 45D), new IrisAxisRotationClamp(true, false, 0D, 0D, 90D));
        return List.of(defaultY, lockedQuarter, flipped, angled);
    }

    private IrisObjectPlacement randomPlacement(Random random) {
        IrisObjectPlacement placement = new IrisObjectPlacement();
        List<ObjectPlaceMode> modes = List.of(ObjectPlaceMode.CENTER_HEIGHT, ObjectPlaceMode.CENTER_HEIGHT,
                ObjectPlaceMode.CENTER_HEIGHT, ObjectPlaceMode.STILT, ObjectPlaceMode.FAST_STILT,
                ObjectPlaceMode.CENTER_STILT, ObjectPlaceMode.ERODE_STILT, ObjectPlaceMode.ORGANIC_STILT,
                ObjectPlaceMode.CEILING_HANG, ObjectPlaceMode.MAX_HEIGHT, ObjectPlaceMode.FAST_MAX_HEIGHT,
                ObjectPlaceMode.MIN_HEIGHT, ObjectPlaceMode.PAINT, ObjectPlaceMode.VACUUM, ObjectPlaceMode.VACUUM_ORGANIC,
                ObjectPlaceMode.STRUCTURE_PIECE, ObjectPlaceMode.FLOATING, ObjectPlaceMode.FAST_MIN_STILT);
        placement.setMode(modes.get(random.nextInt(modes.size())));
        if (random.nextInt(4) == 0) {
            placement.setTranslate(new IrisObjectTranslate()
                    .setX(random.nextInt(7) - 3)
                    .setY(random.nextInt(5) - 2)
                    .setZ(random.nextInt(7) - 3)
                    .setYRandom(random.nextInt(3) - 1));
        }
        if (random.nextInt(6) == 0) {
            placement.setWarp(new IrisGeneratorStyle(NoiseStyle.SIMPLEX).setMultiplier(1D + random.nextInt(4)));
        }
        if (random.nextInt(3) == 0) {
            placement.setEdit(randomEdits(random));
        }
        if (random.nextInt(4) == 0) {
            placement.setStiltSettings(new IrisStiltSettings());
        }
        if (random.nextInt(5) == 0) {
            placement.setVacuumSettings(new IrisVacuumSettings().setRadius(1 + random.nextInt(4)));
        }
        placement.setWaterloggable(random.nextInt(4) == 0);
        placement.setUnderwater(random.nextInt(8) == 0);
        placement.setOnwater(random.nextInt(10) == 0);
        placement.setMeld(random.nextInt(8) == 0);
        placement.setBore(random.nextInt(10) == 0);
        placement.setForcePlace(random.nextInt(3) == 0);
        placement.setBottom(random.nextInt(6) == 0);
        placement.setSnow(random.nextInt(5) == 0 ? 0.7D : 0D);
        placement.setRequireSurfaceSupport(random.nextInt(3) != 0);
        placement.setSurfaceSupportBuffer(random.nextInt(3));
        placement.setSurfaceSupportDepth(1 + random.nextInt(2));
        placement.setCarvingSupport(switch (random.nextInt(5)) {
            case 0 -> CarvingMode.ANYWHERE;
            case 1 -> CarvingMode.CARVING_ONLY;
            default -> CarvingMode.SURFACE_ONLY;
        });
        return placement;
    }

    private KList<IrisObjectReplace> randomEdits(Random random) {
        KList<IrisObjectReplace> edits = new KList<>();
        int count = 1 + random.nextInt(3);
        for (int index = 0; index < count; index++) {
            IrisObjectReplace replace = new IrisObjectReplace();
            switch (random.nextInt(4)) {
                case 0 -> {
                    replace.setFind(new KList<>(new IrisBlockData("minecraft:oak_leaves")));
                    replace.setReplace(palette("minecraft:cobblestone"));
                }
                case 1 -> {
                    replace.setFind(new KList<>(new IrisBlockData("minecraft:oak_stairs")));
                    replace.setReplace(palette("minecraft:oak_stairs[half=top]"));
                }
                case 2 -> {
                    replace.setExact(true);
                    replace.setFind(new KList<>(new IrisBlockData("minecraft:stone")));
                    replace.setReplace(palette("minecraft:glass"));
                }
                default -> {
                    replace.setFind(new KList<>(new IrisBlockData("minecraft:grass_block"), new IrisBlockData("minecraft:dirt")));
                    replace.setReplace(palette("minecraft:oak_leaves[distance=2,persistent=false,waterlogged=false]"));
                }
            }
            replace.setChance(switch (random.nextInt(3)) {
                case 0 -> 1F;
                case 1 -> 0.5F;
                default -> 0.15F;
            });
            edits.add(replace);
        }
        return edits;
    }

    private static IrisMaterialPalette palette(String key) {
        IrisMaterialPalette palette = new IrisMaterialPalette();
        palette.setPalette(new KList<>(new IrisBlockData(key)));
        return palette;
    }

    private IrisObject randomObject(Random random, int index) {
        int width = 1 + random.nextInt(9);
        int height = 1 + random.nextInt(9);
        int depth = 1 + random.nextInt(9);
        IrisObject object = new IrisObject(width, height, depth);
        int count = 1 + random.nextInt(Math.max(1, (width * height * depth) / 2));
        List<IrisBlockVector> placed = new ArrayList<>();
        for (int block = 0; block < count; block++) {
            int x = random.nextInt(width) - (width / 2);
            int y = random.nextInt(height) - (height / 2);
            int z = random.nextInt(depth) - (depth / 2);
            IrisBlockVector position = new IrisBlockVector(x, y, z);
            String key = PALETTE.get(random.nextInt(PALETTE.size()));
            object.getBlocks().put(position, state(key));
            placed.add(position);
            if (key.startsWith("minecraft:chest") && random.nextBoolean()) {
                object.getStates().put(position, new TileData("minecraft:chest", new KMap<>()));
            }
        }
        if (index % 4 == 1 && !placed.isEmpty()) {
            List<TreeGrounding.Root> roots = new ArrayList<>();
            Set<TreeBlockCanvas.Vec> canopy = new HashSet<>();
            for (IrisBlockVector position : placed) {
                if (random.nextInt(3) == 0) {
                    canopy.add(new TreeBlockCanvas.Vec(position.getBlockX(), position.getBlockY(), position.getBlockZ()));
                }
            }
            IrisBlockVector root = placed.get(0);
            roots.add(new TreeGrounding.Root(root.getBlockX(), -(height / 2), root.getBlockZ(), state("minecraft:oak_log[axis=y]")));
            object.setTreeGrounding(TreeGrounding.create(roots, 4, canopy));
        }
        return object;
    }

    private static IrisObjectRotation rotation(IrisAxisRotationClamp x, IrisAxisRotationClamp y, IrisAxisRotationClamp z) {
        IrisObjectRotation rotation = new IrisObjectRotation();
        rotation.setEnabled(true);
        rotation.setXAxis(x);
        rotation.setYAxis(y);
        rotation.setZAxis(z);
        return rotation;
    }

    private GoldenState state(String raw) {
        String key = raw.toLowerCase();
        if (key.indexOf(':') < 0) {
            key = "minecraft:" + key;
        }
        return states.computeIfAbsent(key, GoldenState::new);
    }

    private NativeBlockState mergeStates(NativeBlockState base, NativeBlockState update) {
        GoldenState left = (GoldenState) base;
        GoldenState right = (GoldenState) update;
        if (!left.material.equals(right.material)) {
            return update;
        }
        LinkedHashMap<String, String> merged = new LinkedHashMap<>(left.properties);
        merged.putAll(right.properties);
        return state(GoldenState.compose(left.material, merged));
    }

    private static NativeBlockState rotateState(IrisObjectRotation rotation, NativeBlockState state, int spinX, int spinY, int spinZ) {
        if (state == null || !rotation.canRotate()) {
            return state;
        }
        GoldenState golden = (GoldenState) state;
        String facing = golden.properties.get("facing");
        if (facing != null) {
            IrisBlockVector vector = switch (facing) {
                case "north" -> new IrisBlockVector(0, 0, -1);
                case "south" -> new IrisBlockVector(0, 0, 1);
                case "east" -> new IrisBlockVector(1, 0, 0);
                case "west" -> new IrisBlockVector(-1, 0, 0);
                case "up" -> new IrisBlockVector(0, 1, 0);
                default -> new IrisBlockVector(0, -1, 0);
            };
            IrisBlockVector rotated = rotation.rotate(vector, spinX, spinY, spinZ);
            String face = faceName(rotated);
            if (face.equals("up") || face.equals("down")) {
                return golden.material.endsWith("_stairs") ? null : state;
            }
            return face.equals(facing) ? state : golden.with("facing", face);
        }
        String axis = golden.properties.get("axis");
        if (axis != null) {
            IrisBlockVector vector = switch (axis) {
                case "x" -> new IrisBlockVector(1, 0, 0);
                case "y" -> new IrisBlockVector(0, 1, 0);
                default -> new IrisBlockVector(0, 0, 1);
            };
            IrisBlockVector rotated = rotation.rotate(vector, spinX, spinY, spinZ);
            double ax = Math.abs(rotated.getX());
            double ay = Math.abs(rotated.getY());
            double az = Math.abs(rotated.getZ());
            String result = ax >= ay && ax >= az ? "x" : ay >= az ? "y" : "z";
            return result.equals(axis) ? state : golden.with("axis", result);
        }
        return state;
    }

    private static String faceName(IrisBlockVector vector) {
        double ax = Math.abs(vector.getX());
        double ay = Math.abs(vector.getY());
        double az = Math.abs(vector.getZ());
        if (ay >= ax && ay >= az) {
            return vector.getY() > 0 ? "up" : "down";
        }
        if (ax >= az) {
            return vector.getX() > 0 ? "east" : "west";
        }
        return vector.getZ() > 0 ? "south" : "north";
    }

    private final class GoldenState implements NativeBlockState {
        private final String key;
        private final String material;
        private final LinkedHashMap<String, String> properties;

        private GoldenState(String key) {
            this.key = key;
            int bracket = key.indexOf('[');
            this.material = bracket < 0 ? key : key.substring(0, bracket);
            this.properties = new LinkedHashMap<>();
            if (bracket >= 0) {
                for (String part : key.substring(bracket + 1, key.length() - 1).split(",")) {
                    int split = part.indexOf('=');
                    properties.put(part.substring(0, split), part.substring(split + 1));
                }
            }
        }

        private static String compose(String material, Map<String, String> properties) {
            if (properties.isEmpty()) {
                return material;
            }
            StringBuilder builder = new StringBuilder(material).append('[');
            boolean first = true;
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                if (!first) {
                    builder.append(',');
                }
                builder.append(entry.getKey()).append('=').append(entry.getValue());
                first = false;
            }
            return builder.append(']').toString();
        }

        private GoldenState with(String name, String value) {
            LinkedHashMap<String, String> updated = new LinkedHashMap<>(properties);
            updated.put(name, value);
            return state(compose(material, updated));
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public String namespace() {
            return "minecraft";
        }

        @Override
        public boolean isAir() {
            return material.endsWith("air");
        }

        @Override
        public boolean isSolid() {
            return !isAir() && !isFluid() && !isVineBlock() && !material.equals("minecraft:lantern");
        }

        @Override
        public boolean isOccluding() {
            return material.equals("minecraft:stone") || material.equals("minecraft:dirt")
                    || material.equals("minecraft:grass_block") || material.equals("minecraft:oak_log")
                    || material.equals("minecraft:cobblestone") || material.equals("minecraft:oak_slab");
        }

        @Override
        public boolean isCustom() {
            return false;
        }

        @Override
        public boolean isFluid() {
            return material.equals("minecraft:water");
        }

        @Override
        public boolean isWater() {
            return isFluid();
        }

        @Override
        public boolean isWaterLogged() {
            return "true".equals(properties.get("waterlogged"));
        }

        @Override
        public boolean isLit() {
            return false;
        }

        @Override
        public boolean isUpdatable() {
            return material.endsWith("_stairs");
        }

        @Override
        public boolean isFoliage() {
            return false;
        }

        @Override
        public boolean isTreeBlock() {
            return material.endsWith("_log") || material.endsWith("_leaves");
        }

        @Override
        public boolean isFoliagePlantable() {
            return material.equals("minecraft:grass_block") || material.equals("minecraft:dirt");
        }

        @Override
        public boolean isDecorant() {
            return false;
        }

        @Override
        public boolean isStorage() {
            return material.equals("minecraft:chest");
        }

        @Override
        public boolean isStorageChest() {
            return isStorage();
        }

        @Override
        public boolean isOre() {
            return false;
        }

        @Override
        public boolean isDeepSlate() {
            return false;
        }

        @Override
        public boolean isVineBlock() {
            return material.equals("minecraft:vine");
        }

        @Override
        public boolean canPlaceOnto(NativeBlockState onto) {
            return onto != null && onto.isSolid();
        }

        @Override
        public boolean matches(NativeBlockState state) {
            if (!(state instanceof GoldenState other) || !other.material.equals(material)) {
                return false;
            }
            for (Map.Entry<String, String> entry : other.properties.entrySet()) {
                if (!entry.getValue().equals(properties.get(entry.getKey()))) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean hasTileEntity() {
            return isStorage();
        }

        @Override
        public NativeBlockState withProperty(String name, String value) {
            return with(name, value);
        }

        @Override
        public Object nativeHandle() {
            return key;
        }

        @Override
        public String toString() {
            return key;
        }
    }

    private final class GoldenPlacer implements IObjectPlacer {
        private final Engine engine;
        private final boolean preventDecay;
        private final Map<Long, NativeBlockState> blocks = new HashMap<>();
        private final Map<Long, Map<Class<?>, Object>> extra = new HashMap<>();
        private long hash = 31L;
        private int writes;
        private int markerWrites;

        private GoldenPlacer(Engine engine, boolean preventDecay) {
            this.engine = engine;
            this.preventDecay = preventDecay;
        }

        private static long key(int x, int y, int z) {
            return (((long) x & 0x3FFFFFL) << 42) | (((long) y & 0xFFFFFL) << 22) | ((long) z & 0x3FFFFFL);
        }

        private int surface(int x, int z) {
            return 64 + Math.floorMod(x * 31 + z * 17, 9) - 4;
        }

        private void record(String kind, int x, int y, int z, String value) {
            writes++;
            hash = mix(hash, kind.hashCode());
            hash = mix(hash, x);
            hash = mix(hash, y);
            hash = mix(hash, z);
            hash = mix(hash, value.hashCode());
        }

        @Override
        public int getHighest(int x, int z, IrisData data) {
            return surface(x, z);
        }

        @Override
        public int getHighest(int x, int z, IrisData data, boolean ignoreFluid) {
            return ignoreFluid ? surface(x, z) : Math.max(surface(x, z), 62);
        }

        @Override
        public void set(int x, int y, int z, NativeBlockState d) {
            record("set", x, y, z, d == null ? "null" : d.key());
            blocks.put(key(x, y, z), d);
        }

        @Override
        public NativeBlockState get(int x, int y, int z) {
            NativeBlockState written = blocks.get(key(x, y, z));
            if (written != null) {
                return written;
            }
            int surface = surface(x, z);
            if (y <= surface) {
                return state(y == surface ? "minecraft:grass_block[snowy=false]" : "minecraft:stone");
            }
            if (y <= 62) {
                return state("minecraft:water");
            }
            return Math.floorMod(x + y + z, 13) == 0 ? state("minecraft:oak_leaves[distance=1,persistent=true,waterlogged=true]") : null;
        }

        @Override
        public boolean isPreventingDecay() {
            return preventDecay;
        }

        @Override
        public boolean isCarved(int x, int y, int z) {
            return Math.floorMod(x * 7 + y * 3 + z * 11, 29) == 0;
        }

        @Override
        public boolean isSolid(int x, int y, int z) {
            NativeBlockState state = get(x, y, z);
            return state != null && state.isSolid();
        }

        @Override
        public boolean isUnderwater(int x, int z) {
            return surface(x, z) < 62;
        }

        @Override
        public int getFluidHeight() {
            return 62;
        }

        @Override
        public int getFluidHeight(int x, int z) {
            return 62;
        }

        @Override
        public boolean isDebugSmartBore() {
            return false;
        }

        @Override
        public void setTile(int xx, int yy, int zz, TileData tile) {
            record("tile", xx, yy, zz, tile == null ? "null" : "tile");
        }

        @Override
        public <T> void setData(int xx, int yy, int zz, T value) {
            record("data", xx, yy, zz, value == null ? "null" : value.getClass().getSimpleName() + ":" + value);
            if (value instanceof MatterMarker) {
                markerWrites++;
            }
            extra.computeIfAbsent(key(xx, yy, zz), ignored -> new HashMap<>()).put(value == null ? Object.class : value.getClass(), value);
        }

        @Override
        public <T> T getData(int xx, int yy, int zz, Class<T> type) {
            Map<Class<?>, Object> values = extra.get(key(xx, yy, zz));
            Object value = values == null ? null : values.get(type);
            return value == null ? null : type.cast(value);
        }

        @Override
        public boolean beginTreeCanopy(BlockPosition position) {
            boolean open = IObjectPlacer.super.beginTreeCanopy(position);
            record("canopy", position.getX(), position.getY(), position.getZ(), Boolean.toString(open));
            return open;
        }

        @Override
        public Engine getEngine() {
            return engine;
        }
    }
}
