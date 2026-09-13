package art.arcane.iris.world.storage.matter;

import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.generation.decoration.tree.TreeBlockMaterial;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.integration.Identifier;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.IrisRegistrant;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.structure.nativegen.NativeStructureOwnershipBundle;
import art.arcane.iris.structure.nativegen.NativeStructureOwnershipRecord;
import art.arcane.iris.structure.nativegen.NativeStructureOwnershipRecord.DecisionSnapshot;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.iris.world.entity.IrisSpawner;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.matter.IrisMatter;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterHeader;
import art.arcane.volmlib.util.matter.MatterSlice;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public class IrisMatterPersistenceTest {
    private static final TypeToken<KMap<String, Object>> TILE_PROPERTIES = new TypeToken<>() { };

    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    private final Fixture<?> fixture;
    private TileData.TileReader previousReader;

    public IrisMatterPersistenceTest(Fixture<?> fixture) {
        this.fixture = fixture;
    }

    @Parameterized.Parameters(name = "{index}: {0}")
    public static List<Fixture<?>> fixtures() {
        IrisSpawner spawner = new IrisSpawner();
        spawner.setLoadKey("test-spawner");
        KMap<String, Object> tileProperties = new KMap<>();
        tileProperties.put("CustomName", "stored chest");
        HydrologyCaveCell hydrology = new HydrologyCaveCell(
                HydrologyCaveAction.WET_SOURCE, "river", "iris:flooded");
        NativeStructureOwnershipRecord ownership = new NativeStructureOwnershipRecord(
                NativeStructureOwnershipRecord.CURRENT_SCHEMA, "minecraft:village_plains",
                0, 0, 42L, 64, 0, 64, 0, 15, 80, 15, 70,
                0, 0, 0, 0, "1".repeat(64), new DecisionSnapshot("null", "{}"));
        return List.of(
                new Fixture<>(Identifier.class, new Identifier("custom", "stored_block"),
                        "art.arcane.iris.core.link.Identifier"),
                new Fixture<>(IrisSpawner.class, spawner,
                        "art.arcane.iris.engine.object.IrisSpawner"),
                new Fixture<>(TileWrapper.class, new TileWrapper(new TileData("minecraft:chest", tileProperties)),
                        "art.arcane.iris.util.project.matter.TileWrapper"),
                new Fixture<>(PreObjectMatterCell.class,
                        PreObjectMatterCell.string("stored marker").captureHydrology(hydrology),
                        "art.arcane.iris.util.project.matter.PreObjectMatterCell"),
                new Fixture<>(HydrologyCaveCell.class, hydrology,
                        "art.arcane.iris.engine.hydrology.cave.HydrologyCaveCell"),
                new Fixture<>(NativeStructureOwnershipBundle.class,
                        NativeStructureOwnershipBundle.empty().with(ownership),
                        "art.arcane.iris.engine.framework.NativeStructureOwnershipBundle"),
                new Fixture<>(TreeBlockMaterial.class, TreeBlockMaterial.of("minecraft:oak_log"),
                        "art.arcane.iris.engine.framework.TreeBlockMaterial"));
    }

    @Before
    public void registerStorage() {
        IrisMatterSupport.ensureRegistered();
        Gson gson = new Gson();
        previousReader = TileData.bindPlatformReader(input -> new TileData(input.readUTF(),
                gson.fromJson(input.readUTF(), TILE_PROPERTIES)));
    }

    @After
    public void restoreStorage() {
        TileData.restorePlatformReader(previousReader);
    }

    @Test
    public void savesKeepTheirEstablishedTypeIdentifier() throws IOException {
        byte[] saved = save(source(fixture));
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(saved))) {
            assertEquals(2, input.readInt());
            assertEquals(2, input.readInt());
            assertEquals(2, input.readInt());
            assertEquals(1, input.readUnsignedByte());
            new MatterHeader().read(input);
            int sliceBytes = input.readInt();
            assertEquals(input.available(), sliceBytes);
            assertEquals(fixture.id(), input.readUTF());
        }
        assertValues(read(saved));
    }

    @Test
    public void readsAndResavesDataWithEstablishedIdentifiers() throws IOException {
        Matter loaded = read(establishedFile());
        assertValues(loaded);
        assertValues(read(save(loaded)));
    }

    private byte[] establishedFile() throws IOException {
        Matter source = source(fixture);
        ByteArrayOutputStream sliceBytes = new ByteArrayOutputStream();
        source.getSlice(fixture.type()).write(new DataOutputStream(sliceBytes));
        ByteArrayOutputStream storedSlice = new ByteArrayOutputStream();
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(sliceBytes.toByteArray()));
             DataOutputStream output = new DataOutputStream(storedSlice)) {
            input.readUTF();
            output.writeUTF(fixture.id());
            input.transferTo(output);
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(2);
            output.writeInt(2);
            output.writeInt(2);
            output.writeByte(1);
            output.writeUTF("stored world");
            output.writeLong(123456L);
            output.writeShort(1);
            output.writeInt(storedSlice.size());
            storedSlice.writeTo(output);
        }
        return bytes.toByteArray();
    }

    private <T> Matter source(Fixture<T> entry) {
        Matter matter = new IrisMatter(2, 2, 2);
        MatterSlice<T> slice = matter.slice(entry.type());
        slice.set(1, 0, 1, entry.value());
        slice.set(1, 1, 1, entry.value());
        return matter;
    }

    private byte[] save(Matter matter) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        matter.write(bytes);
        return bytes.toByteArray();
    }

    private Matter read(byte[] bytes) throws IOException {
        try (IrisMatterContext.Scope scope = IrisMatterContext.open(data());
             ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            return Matter.read(input);
        }
    }

    @SuppressWarnings("unchecked")
    private IrisData data() {
        IrisData data = mock(IrisData.class);
        KMap<Class<? extends IrisRegistrant>, ResourceLoader<? extends IrisRegistrant>> loaders = new KMap<>();
        if (fixture.value() instanceof IrisSpawner spawner) {
            ResourceLoader<IrisSpawner> loader = mock(ResourceLoader.class);
            when(loader.load(spawner.getLoadKey())).thenReturn(spawner);
            loaders.put(IrisSpawner.class, loader);
        }
        when(data.getLoaders()).thenReturn(loaders);
        return data;
    }

    private void assertValues(Matter matter) {
        assertEquals(1, matter.getSliceTypes().size());
        MatterSlice<?> slice = matter.getSlice(fixture.type());
        assertNotNull(slice);
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 2; z++) {
                    assertEquals(x == 1 && z == 1 ? fixture.value() : null, slice.get(x, y, z));
                }
            }
        }
    }

    public record Fixture<T>(Class<T> type, T value, String id) {
        @Override
        public String toString() {
            return type.getSimpleName();
        }
    }
}
