package art.arcane.iris.engine.platform.studio.generators;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.core.runtime.ObjectStudioLayout;
import art.arcane.iris.core.service.ObjectStudioSaveService;
import art.arcane.iris.engine.data.chunk.TerrainChunk;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineTarget;
import art.arcane.iris.engine.framework.GenerationSessionLease;
import art.arcane.iris.engine.object.IrisObject;
import art.arcane.iris.engine.object.IrisWorld;
import art.arcane.iris.engine.object.TileData;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.math.IrisBlockVector;
import art.arcane.iris.util.project.matter.slices.PreObjectMatterTest;
import art.arcane.volmlib.util.collection.KMap;
import io.papermc.paper.InternalAPIBridge;
import io.papermc.paper.registry.RegistryAccess;
import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class ObjectStudioGeometryTest {
    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @BeforeClass
    @SuppressWarnings("unchecked")
    public static void initializeBukkit() throws Exception {
        PreObjectMatterTest.setUpBukkit();
        AtomicReference<Registry<Biome>> registered = new AtomicReference<>();
        RegistryAccess access = mock(RegistryAccess.class, invocation -> {
            if (invocation.getMethod().getName().equals("getRegistry")) {
                if (registered.get() == null) {
                    registered.set(biomeRegistry());
                }
                return registered.get();
            }
            return RETURNS_DEFAULTS.answer(invocation);
        });
        InternalAPIBridge bridge = mock(InternalAPIBridge.class, invocation -> {
            if (invocation.getMethod().getName().equals("constructLegacyCustomBiome")) {
                return biome(Key.key("minecraft:custom"));
            }
            return RETURNS_DEFAULTS.answer(invocation);
        });
        try (MockedStatic<RegistryAccess> registries = mockStatic(RegistryAccess.class);
             MockedStatic<InternalAPIBridge> internals = mockStatic(InternalAPIBridge.class)) {
            registries.when(RegistryAccess::registryAccess).thenReturn(access);
            internals.when(InternalAPIBridge::get).thenReturn(bridge);
            ClassLoader loader = ObjectStudioGenerator.class.getClassLoader();
            Class.forName(Registry.class.getName(), true, loader);
            Class.forName(Biome.class.getName(), true, loader);
            Class.forName(ObjectStudioGenerator.class.getName(), true, loader);
        }
    }

    @SuppressWarnings("unchecked")
    private static Registry<Biome> biomeRegistry() {
        return (Registry<Biome>) Proxy.newProxyInstance(Registry.class.getClassLoader(),
                new Class<?>[]{Registry.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getOrThrow" -> biome((Key) arguments[0]);
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    case "toString" -> "iris-object-studio-biome-registry";
                    case "size" -> 0;
                    case "hasTag" -> false;
                    default -> null;
                });
    }

    @Test
    public void jungleClutterSignedBoundsStayInsideTheEditableCellAboveTheFloor() throws Exception {
        IrisObject source = filled(5, 3, 4, new Position(-3, -2, -2), new Position(1, -1, 1));
        Fixture fixture = fixture(source, 64, new Position(14, 65, 14));

        Map<Position, PlatformBlockState> rendered = render(fixture);

        assertPlacement(fixture, rendered, new Position(-3, -2, -2));
        assertEquals(40, source.getBlocks().size());
        assertTrue(rendered.containsKey(new Position(14, 65, 14)));
        assertTrue(rendered.containsKey(new Position(18, 66, 17)));
    }

    @Test
    public void oddEvenAndNegativeChunkPlacementPreservesSignedBlocksAndTileBytes() throws Exception {
        int[][] dimensions = {{5, 3, 4}, {4, 4, 6}, {1, 1, 1}};
        for (int[] size : dimensions) {
            Position minimum = new Position(-3, -2, -4);
            IrisObject source = filled(size[0], size[1], size[2], minimum,
                    new Position(minimum.x() + size[0] - 1, minimum.y() + size[1] - 1,
                            minimum.z() + size[2] - 1));
            KMap<String, Object> properties = new KMap<>();
            properties.put("CustomName", "Stored tile");
            TileData tile = new TileData("minecraft:chest", properties);
            source.getStates().put(new IrisBlockVector(minimum.x(), minimum.y(), minimum.z()), tile);
            Fixture fixture = fixture(source, 64, new Position(-18, 65, -17));
            Map<Position, PlatformBlockState> rendered = render(fixture);
            assertPlacement(fixture, rendered, minimum);

            IrisObject captured = fixture.generator().createCapture(fixture.cell());
            for (Map.Entry<Position, PlatformBlockState> entry : rendered.entrySet()) {
                Position position = entry.getKey();
                captured.setUnsigned(position.x() - fixture.cell().originX(),
                        position.y() - fixture.cell().originY(), position.z() - fixture.cell().originZ(),
                        entry.getValue());
            }
            captured.setUnsignedTile(0, 0, 0, tile);

            assertSame(tile, captured.getStates().get(new IrisBlockVector(minimum.x(), minimum.y(), minimum.z())));
            assertEquals(serialized(source), serialized(captured));
        }
    }

    @Test
    public void highWorldFloorRaisesBothObjectAndCaptureCell() throws Exception {
        IrisObject source = filled(5, 3, 4, new Position(-3, -2, -2), new Position(1, -1, 1));
        Fixture fixture = fixture(source, 160, new Position(2, 65, 2));

        Map<Position, PlatformBlockState> rendered = render(fixture);

        assertEquals(161, fixture.cell().originY());
        assertPlacement(fixture, rendered, new Position(-3, -2, -2));
        assertTrue(rendered.containsKey(new Position(2, 161, 2)));
    }

    private static IrisObject filled(int width, int height, int depth, Position minimum, Position maximum) {
        PlatformBlockState block = mock(PlatformBlockState.class);
        when(block.key()).thenReturn("minecraft:oak_leaves[persistent=true]");
        IrisObject object = new IrisObject(width, height, depth);
        for (int x = minimum.x(); x <= maximum.x(); x++) {
            for (int y = minimum.y(); y <= maximum.y(); y++) {
                for (int z = minimum.z(); z <= maximum.z(); z++) {
                    object.getBlocks().put(new IrisBlockVector(x, y, z), block);
                }
            }
        }
        return object;
    }

    @SuppressWarnings("unchecked")
    private Fixture fixture(IrisObject source, int minHeight, Position origin) throws Exception {
        File worldFolder = temporary.newFolder();
        File sourceFolder = temporary.newFolder();
        File objectFile = new File(sourceFolder, "jungleclutt6.iob");
        try (DataOutputStream header = new DataOutputStream(Files.newOutputStream(objectFile.toPath()))) {
            header.writeInt(source.getW());
            header.writeInt(source.getH());
            header.writeInt(source.getD());
        }
        IrisData data = mock(IrisData.class);
        ResourceLoader<IrisObject> loader = mock(ResourceLoader.class);
        when(data.getDataFolder()).thenReturn(sourceFolder);
        when(data.getObjectLoader()).thenReturn(loader);
        when(loader.getPossibleKeys()).thenReturn(new String[]{"jungleclutt6"});
        when(loader.findFile("jungleclutt6")).thenReturn(objectFile);
        when(loader.load("jungleclutt6")).thenReturn(source);
        ObjectStudioLayout.GridCell cell = new ObjectStudioLayout.GridCell(sourceFolder.getName(), "jungleclutt6",
                origin.x(), origin.y(), origin.z(), source.getW(), source.getH(), source.getD());
        Constructor<ObjectStudioLayout> constructor = ObjectStudioLayout.class.getDeclaredConstructor(
                int.class, int.class, List.class);
        constructor.setAccessible(true);
        constructor.newInstance(2, 160, List.of(cell)).save(new File(worldFolder, ".iris/object-studio-layout.json"));
        Engine engine = mock(Engine.class);
        EngineTarget target = mock(EngineTarget.class);
        IrisWorld world = mock(IrisWorld.class);
        when(world.worldFolder()).thenReturn(worldFolder);
        when(target.getWorld()).thenReturn(world);
        when(engine.getTarget()).thenReturn(target);
        when(engine.getData()).thenReturn(data);
        when(engine.getMinHeight()).thenReturn(minHeight);
        when(engine.getMaxHeight()).thenReturn(320);
        when(engine.acquireGenerationLease(anyString())).thenAnswer(invocation -> GenerationSessionLease.noop());
        ObjectStudioGenerator generator = new ObjectStudioGenerator(engine, 2,
                mock(PlatformBlockState.class), mock(PlatformBlockState.class), mock(PlatformBlockState.class));
        TerrainChunk initial = mock(TerrainChunk.class);
        when(initial.getMaxHeight()).thenReturn(320);
        try (MockedStatic<ObjectStudioSaveService> saves = mockStatic(ObjectStudioSaveService.class)) {
            saves.when(ObjectStudioSaveService::get).thenReturn(mock(ObjectStudioSaveService.class));
            generator.generateChunk(engine, initial, cell.chunkMinX(), cell.chunkMinZ());
        }
        ObjectStudioLayout.GridCell placed = generator.getLayout().get("jungleclutt6");
        assertNotNull(placed);
        return new Fixture(generator, engine, placed, source);
    }

    private static Map<Position, PlatformBlockState> render(Fixture fixture) throws Exception {
        Map<Position, PlatformBlockState> rendered = new HashMap<>();
        PlatformBlockState objectBlock = fixture.source().getBlocks().values().iterator().next();
        for (int chunkX = fixture.cell().chunkMinX(); chunkX <= fixture.cell().chunkMaxX(); chunkX++) {
            for (int chunkZ = fixture.cell().chunkMinZ(); chunkZ <= fixture.cell().chunkMaxZ(); chunkZ++) {
                int originX = chunkX << 4;
                int originZ = chunkZ << 4;
                TerrainChunk chunk = mock(TerrainChunk.class);
                when(chunk.getMaxHeight()).thenReturn(320);
                doAnswer(invocation -> {
                    int x = invocation.getArgument(0);
                    int y = invocation.getArgument(1);
                    int z = invocation.getArgument(2);
                    assertTrue(x >= 0 && x < 16 && z >= 0 && z < 16);
                    PlatformBlockState block = invocation.getArgument(3);
                    if (block == objectBlock) {
                        rendered.put(new Position(originX + x, y, originZ + z), block);
                    }
                    return null;
                }).when(chunk).setBlock(anyInt(), anyInt(), anyInt(), any());
                fixture.generator().generateChunk(fixture.engine(), chunk, chunkX, chunkZ);
            }
        }
        return rendered;
    }

    private static void assertPlacement(Fixture fixture, Map<Position, PlatformBlockState> rendered, Position minimum) {
        assertEquals(fixture.source().getBlocks().size(), rendered.size());
        for (Map.Entry<IrisBlockVector, PlatformBlockState> entry : fixture.source().getBlocks()) {
            IrisBlockVector signed = entry.getKey();
            Position expected = new Position(fixture.cell().originX() + signed.getBlockX() - minimum.x(),
                    fixture.cell().originY() + signed.getBlockY() - minimum.y(),
                    fixture.cell().originZ() + signed.getBlockZ() - minimum.z());
            assertSame(entry.getValue(), rendered.get(expected));
        }
    }

    private static SerializedObject serialized(IrisObject object) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        object.write(output);
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(output.toByteArray()))) {
            int width = input.readInt();
            int height = input.readInt();
            int depth = input.readInt();
            assertEquals("Iris V2 IOB;", input.readUTF());
            String[] palette = new String[input.readShort()];
            for (int index = 0; index < palette.length; index++) {
                palette[index] = input.readUTF();
            }
            int blockCount = input.readInt();
            Map<Position, String> blocks = new HashMap<>();
            for (int index = 0; index < blockCount; index++) {
                Position position = new Position(input.readShort(), input.readShort(), input.readShort());
                blocks.put(position, palette[input.readShort()]);
            }
            int tileCount = input.readInt();
            Map<Position, TileData> tiles = new HashMap<>();
            for (int index = 0; index < tileCount; index++) {
                Position position = new Position(input.readShort(), input.readShort(), input.readShort());
                tiles.put(position, TileData.read(input));
            }
            assertEquals(-1, input.read());
            return new SerializedObject(width, height, depth, blocks, tiles);
        }
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

    private record Position(int x, int y, int z) {
    }

    private record Fixture(ObjectStudioGenerator generator, Engine engine, ObjectStudioLayout.GridCell cell,
                           IrisObject source) {
    }

    private record SerializedObject(int width, int height, int depth, Map<Position, String> blocks,
                                    Map<Position, TileData> tiles) {
    }
}
