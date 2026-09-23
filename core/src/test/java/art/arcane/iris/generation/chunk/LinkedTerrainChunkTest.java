package art.arcane.iris.generation.chunk;

import art.arcane.iris.platform.bukkit.BukkitBiome;
import art.arcane.iris.testsupport.BukkitTestServer;
import art.arcane.iris.world.history.BoundaryColumnGeometry;
import art.arcane.iris.world.history.NativeTerrainReceipt;
import art.arcane.iris.world.history.SavedTerrainChunk;
import art.arcane.volmlib.nativelib.terrain.NativeBiome;
import io.papermc.paper.InternalAPIBridge;
import io.papermc.paper.registry.RegistryAccess;
import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class LinkedTerrainChunkTest {
    @BeforeClass
    public static void initializeBiomes() throws Exception {
        BukkitTestServer.install();
        AtomicReference<Object> registered = new AtomicReference<>();
        RegistryAccess access = mock(RegistryAccess.class, invocation -> {
            if (invocation.getMethod().getName().equals("getRegistry")) {
                if (registered.get() == null) {
                    registered.set(Proxy.newProxyInstance(Registry.class.getClassLoader(), new Class<?>[]{Registry.class},
                            (proxy, method, arguments) -> method.getName().equals("getOrThrow")
                                    ? biome((Key) arguments[0]) : null));
                }
                return registered.get();
            }
            return RETURNS_DEFAULTS.answer(invocation);
        });
        InternalAPIBridge bridge = mock(InternalAPIBridge.class, invocation ->
                invocation.getMethod().getName().equals("constructLegacyCustomBiome")
                        ? biome(Key.key("minecraft:custom")) : RETURNS_DEFAULTS.answer(invocation));
        try (MockedStatic<RegistryAccess> registries = mockStatic(RegistryAccess.class);
             MockedStatic<InternalAPIBridge> internals = mockStatic(InternalAPIBridge.class)) {
            registries.when(RegistryAccess::registryAccess).thenReturn(access);
            internals.when(InternalAPIBridge::get).thenReturn(bridge);
            Class.forName(Registry.class.getName());
            Class.forName(Biome.class.getName());
        }
    }

    @Test
    public void biomeReadsReuseCanonicalHandlesAndPreserveReceiptBytes() throws Exception {
        LinkedTerrainChunk chunk = chunk();
        NativeBiome plains = BukkitBiome.of(Biome.PLAINS);
        NativeBiome desert = BukkitBiome.of(Biome.DESERT);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                chunk.fillBiomeColumn(x, z, plains);
            }
        }
        NativeBiome alternate = mock(NativeBiome.class);
        when(alternate.nativeHandle()).thenReturn(Biome.DESERT);
        when(alternate.key()).thenReturn("ignored:alias");
        chunk.setBiome(0, -12, 0, alternate);
        chunk.setBiome(17, 100, -1, desert);
        AtomicInteger conversions = new AtomicInteger();
        try (MockedStatic<BukkitBiome> wrappers = mockStatic(BukkitBiome.class, invocation -> {
            conversions.incrementAndGet();
            return CALLS_REAL_METHODS.answer(invocation);
        })) {
            assertSame(desert, chunk.getBiome(0, -12, 0));
            assertSame(desert, chunk.getBiome(1, -1, 15));
            assertSame(plains, chunk.getBiome(0, -100, 0));
            for (boolean boundary : new boolean[]{false, true}) {
                SavedTerrainChunk.VoxelSource actual = source((x, y, z) -> chunk.getBiome(x, y, z).key());
                SavedTerrainChunk.VoxelSource expected = source((x, y, z) ->
                        x == 0 && y == -12 && z == 0 || x == 1 && y == -1 && z == 15
                                ? "minecraft:desert" : "minecraft:plains");
                SavedTerrainChunk captured = capture(actual, boundary);
                assertArrayEquals(NativeTerrainReceipt.encode(capture(expected, boundary), 7L, "epoch"),
                        NativeTerrainReceipt.encode(captured, 7L, "epoch"));
            }
            assertEquals(0, conversions.get());
        }
    }

    @Test
    public void emptyColumnsResolveTheDefaultBiomeOnlyOnce() {
        LinkedTerrainChunk chunk = chunk();
        NativeBiome plains = BukkitBiome.of(Biome.PLAINS);
        AtomicInteger conversions = new AtomicInteger();
        try (MockedStatic<BukkitBiome> wrappers = mockStatic(BukkitBiome.class, invocation -> {
            conversions.incrementAndGet();
            return CALLS_REAL_METHODS.answer(invocation);
        })) {
            for (int y = -32; y < 16; y++) {
                assertSame(plains, chunk.getBiome(0, y, 0));
            }
            assertEquals(1, conversions.get());
        }
    }

    private static LinkedTerrainChunk chunk() {
        ChunkData data = mock(ChunkData.class);
        when(data.getMinHeight()).thenReturn(-16);
        when(data.getMaxHeight()).thenReturn(0);
        return new LinkedTerrainChunk(data);
    }

    private static SavedTerrainChunk capture(SavedTerrainChunk.VoxelSource source, boolean boundary) throws Exception {
        return boundary ? SavedTerrainChunk.captureBoundary(-1, 2, -16, 16, "minecraft:noise", source)
                : SavedTerrainChunk.capture(-1, 2, -16, 16, "minecraft:noise", source);
    }

    private static SavedTerrainChunk.VoxelSource source(BiomeKey source) {
        BoundaryColumnGeometry.Voxel stone = new BoundaryColumnGeometry.Voxel(
                "minecraft:stone", BoundaryColumnGeometry.Phase.SOLID, "", false);
        return new SavedTerrainChunk.VoxelSource() {
            @Override
            public BoundaryColumnGeometry.Voxel voxel(int x, int y, int z) {
                return stone;
            }

            @Override
            public String biome(int x, int y, int z) {
                return source.get(x, y, z);
            }
        };
    }

    private static Biome biome(Key key) {
        NamespacedKey namespaced = new NamespacedKey(key.namespace(), key.value());
        return (Biome) Proxy.newProxyInstance(Biome.class.getClassLoader(), new Class<?>[]{Biome.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getKey" -> namespaced;
                    case "hashCode" -> namespaced.hashCode();
                    case "equals" -> proxy == arguments[0];
                    case "toString", "name" -> key.value();
                    default -> null;
                });
    }

    private interface BiomeKey {
        String get(int x, int y, int z);
    }
}
