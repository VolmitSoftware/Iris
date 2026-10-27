package art.arcane.iris.engine.object.placement;

import art.arcane.iris.builtin.anchor.ObjectPlaceMode;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.BuiltInPack;
import art.arcane.iris.core.modules.ModuleObjectPlacement;
import art.arcane.iris.core.modules.PlatformBlockStates;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.builtin.prepare.ObjectTranslate;
import art.arcane.iris.engine.object.prefab.TileData;
import art.arcane.iris.engine.object.terrain.carving.CarveResult;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.host.LoadedPack;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the blocks every object placement mode writes for one object on a fixed, uneven terrain with a cave ceiling
 * above it, so the modes can move into other code without changing what a world generates. Each mode places the same
 * object at the same column with the same seed; the writes, in order, are hashed. A mismatch prints every mode's hash.
 */
public class ObjectPlaceModePinTest {
    private static final int OBJECT_X = 41;
    private static final int OBJECT_Z = -24;
    private static final int CEILING_Y = 110;
    private static final int CAVE_FLOOR_Y = 70;
    private static final long PLACEMENT_SEED = 1337L;
    private static final long WORLD_SEED = 7L;
    private static final int WORLD_HEIGHT = 256;

    private static final Map<ObjectPlaceMode, String> PINNED = pinned();

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final Map<String, PlatformBlockState> registryStates = new HashMap<>();
    private IrisData data;
    private Engine engine;
    private LoadedPack pack;

    @Before
    public void bindPlatform() throws IOException {
        IrisPlatforms.unbind();
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.block(anyString())).thenAnswer(invocation -> registryState(invocation.getArgument(0)));
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);

        engine = mock(Engine.class);
        when(engine.getHeight()).thenReturn(WORLD_HEIGHT);
        BuiltInPack.seedWorld(engine, WORLD_SEED);
        data = mock(IrisData.class);
        when(data.getEngine()).thenReturn(engine);
        when(engine.getData()).thenReturn(data);
        BuiltInPack.runBareWorld(engine, data);
        pack = BuiltInPack.serve(data, folder, List.of());
    }

    @After
    public void unbindPlatform() {
        pack.close();
        IrisPlatforms.unbind();
    }

    @Test
    public void everyModeWritesThePinnedBlocks() {
        Map<ObjectPlaceMode, String> actual = new LinkedHashMap<>();
        for (ObjectPlaceMode mode : ObjectPlaceMode.values()) {
            actual.put(mode, hash(place(mode)));
        }
        StringBuilder report = new StringBuilder();
        actual.forEach((mode, hash) -> report.append("            Map.entry(ObjectPlaceMode.").append(mode)
                .append(", \"").append(hash).append("\"),\n"));
        assertEquals("Pinned placement hashes changed:\n" + report, PINNED, actual);
    }

    private List<String> place(ObjectPlaceMode mode) {
        TerrainPlacer placer = new TerrainPlacer(engine);
        ObjectPlacement config = ObjectPlacement.builder()
                .mode(mode)
                .requireSurfaceSupport(false)
                .translate(ObjectTranslate.of(0, mode == ObjectPlaceMode.FLOATING ? 140 : 2, 0, 0))
                .build();
        CarveResult cave = isCaveMode(mode) ? new CarveResult(CAVE_FLOOR_Y, CEILING_Y) : null;
        int requestedY = requestedY(mode);
        ModuleObjectPlacement.place(pinnedObject(), OBJECT_X, requestedY, OBJECT_Z, placer, config,
                new RNG(PLACEMENT_SEED), null, cave, data);
        return placer.writes();
    }

    private static boolean isCaveMode(ObjectPlaceMode mode) {
        return mode == ObjectPlaceMode.ORGANIC_STILT || mode == ObjectPlaceMode.CEILING_HANG;
    }

    /** Cave modes and structure pieces are placed at a height, the way their callers place them; the rest sample. */
    private static int requestedY(ObjectPlaceMode mode) {
        return switch (mode) {
            case CEILING_HANG -> CEILING_Y - 9;
            case ORGANIC_STILT -> CAVE_FLOOR_Y + 12;
            case STRUCTURE_PIECE -> 90;
            default -> -1;
        };
    }

    /** A 5x4x5 object with a stepped, holed bottom, a mix of materials and an air pocket. */
    private IrisObject pinnedObject() {
        IrisObject object = new IrisObject(5, 4, 5);
        PlatformBlockState stone = registryState("minecraft:stone");
        PlatformBlockState planks = registryState("minecraft:oak_planks");
        PlatformBlockState glass = registryState("minecraft:glass");
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                if ((x + z) % 4 != 3) {
                    object.setUnsigned(x, 0, z, (x * z) % 3 == 0 ? stone : planks);
                }
                for (int y = 1; y < 4; y++) {
                    boolean shell = x == 0 || x == 4 || z == 0 || z == 4 || y == 3;
                    if (shell && !(x == 2 && z == 0 && y < 3)) {
                        object.setUnsigned(x, y, z, y == 2 && (x == 0 || x == 4) ? glass : planks);
                    }
                }
            }
        }
        return object;
    }

    private PlatformBlockState registryState(String key) {
        String normalized = key.toLowerCase();
        String namespaced = normalized.contains(":") ? normalized : "minecraft:" + normalized;
        return registryStates.computeIfAbsent(namespaced, value -> state(value, isSolidKey(value)));
    }

    private static boolean isSolidKey(String key) {
        return !key.contains("air") && !key.contains("water") && !key.contains("snow");
    }

    private static PlatformBlockState state(String key, boolean solid) {
        PlatformBlockState state = mock(PlatformBlockState.class);
        when(state.key()).thenReturn(key);
        when(state.materialKey()).thenReturn(key);
        when(state.isSolid()).thenReturn(solid);
        when(state.isOccluding()).thenReturn(solid);
        when(state.isAir()).thenReturn(key.contains("air"));
        return state;
    }

    private static String hash(List<String> writes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String write : writes) {
                digest.update(write.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            }
            return writes.size() + ":" + HexFormat.of().formatHex(digest.digest()).substring(0, 24);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** Uneven terrain: stone up to a height that changes with the column, air above, a stone roof from {@link #CEILING_Y}. */
    private static int terrainHeight(int x, int z) {
        return 64 + Math.floorMod(x * 3 + z * 5, 7) - 3;
    }

    private final class TerrainPlacer implements IObjectPlacer {
        private final Engine engine;
        private final Map<String, PlatformBlockState> written = new HashMap<>();
        private final List<String> writes = new ArrayList<>();

        private TerrainPlacer(Engine engine) {
            this.engine = engine;
        }

        List<String> writes() {
            return writes;
        }

        @Override
        public int getHighest(int x, int z, IrisData data) {
            return terrainHeight(x, z);
        }

        @Override
        public int getHighest(int x, int z, IrisData data, boolean ignoreFluid) {
            return terrainHeight(x, z);
        }

        @Override
        public void set(int x, int y, int z, PlatformBlockState state) {
            writes.add(x + "," + y + "," + z + "=" + PlatformBlockStates.namespaced(state.key()));
            written.put(x + ":" + y + ":" + z, state);
        }

        @Override
        public PlatformBlockState get(int x, int y, int z) {
            PlatformBlockState state = written.get(x + ":" + y + ":" + z);
            if (state != null) {
                return state;
            }
            return y <= terrainHeight(x, z) || y >= CEILING_Y ? registryState("minecraft:stone") : registryState("minecraft:air");
        }

        @Override
        public boolean isPreventingDecay() {
            return false;
        }

        @Override
        public boolean isCarved(int x, int y, int z) {
            return false;
        }

        @Override
        public boolean isSolid(int x, int y, int z) {
            return get(x, y, z).isSolid();
        }

        @Override
        public boolean isUnderwater(int x, int z) {
            return false;
        }

        @Override
        public int getFluidHeight() {
            return 0;
        }

        @Override
        public boolean isDebugSmartBore() {
            return false;
        }

        @Override
        public void setTile(int x, int y, int z, TileData tile) {
        }

        @Override
        public <T> void setData(int x, int y, int z, T data) {
        }

        @Override
        public <T> T getData(int x, int y, int z, Class<T> type) {
            return null;
        }

        @Override
        public Engine getEngine() {
            return engine;
        }
    }

    private static Map<ObjectPlaceMode, String> pinned() {
        return Map.ofEntries(
                Map.entry(ObjectPlaceMode.CENTER_HEIGHT, "74:2502f2a09e5b13834de5ddfe"),
                Map.entry(ObjectPlaceMode.MAX_HEIGHT, "74:3990440aae1bbacece06254f"),
                Map.entry(ObjectPlaceMode.FAST_MAX_HEIGHT, "74:67a78ba6ece4c11e182d012b"),
                Map.entry(ObjectPlaceMode.MIN_HEIGHT, "74:4f179fb8e183fb1d0dfd889e"),
                Map.entry(ObjectPlaceMode.FAST_MIN_HEIGHT, "74:8e351f8c8df9e3665e5ddb3d"),
                Map.entry(ObjectPlaceMode.STILT, "167:538fc49de1a5d3eac9bd5c9d"),
                Map.entry(ObjectPlaceMode.FAST_STILT, "129:28fa04ed8d905af6c9bdd427"),
                Map.entry(ObjectPlaceMode.MIN_STILT, "82:53c0de1267c70d8dd823cb83"),
                Map.entry(ObjectPlaceMode.FAST_MIN_STILT, "90:1fb5736f3a271ce2e092944d"),
                Map.entry(ObjectPlaceMode.CENTER_STILT, "113:1ce601c29f74e3a1f7bd5eb5"),
                Map.entry(ObjectPlaceMode.ERODE_STILT, "81:c167e68ce8ffb1c1fafbd1e7"),
                Map.entry(ObjectPlaceMode.ORGANIC_STILT, "306:dcc079da485dfbdbf7d9dbe4"),
                Map.entry(ObjectPlaceMode.CEILING_HANG, "101:0b814f84a454fd06038c4af0"),
                Map.entry(ObjectPlaceMode.VACUUM, "319:02e27e3e68f71e141022d6d3"),
                Map.entry(ObjectPlaceMode.VACUUM_HIGH, "586:696ef3ec248ecaedbd22f66a"),
                Map.entry(ObjectPlaceMode.VACUUM_FAST, "113:497ca8f7cec844d8d71c190f"),
                Map.entry(ObjectPlaceMode.VACUUM_ORGANIC, "317:7c80d5296b018678983138aa"),
                Map.entry(ObjectPlaceMode.VACUUM_WAVY, "528:709f4a142c37d361b73456cd"),
                Map.entry(ObjectPlaceMode.PAINT, "74:c74db6c9f40eca3898f5f520"),
                Map.entry(ObjectPlaceMode.FLOATING, "74:a117d1024b91539bb4700ed5"),
                Map.entry(ObjectPlaceMode.STRUCTURE_PIECE, "74:c6c8aa8c3668f885320582ea")
        );
    }
}
