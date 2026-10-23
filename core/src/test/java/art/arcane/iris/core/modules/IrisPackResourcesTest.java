package art.arcane.iris.core.modules;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.volmlib.util.collection.KList;
import com.volmit.block.BlockState;
import com.volmit.mantle.BlockBox;
import com.volmit.object.GeneratedObject;
import com.volmit.object.ObjectBlock;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.resource.ResourceKind;
import com.volmit.world.BlockPosition;
import com.volmit.world.PackBiome;
import com.volmit.world.PackRegion;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The pack snapshot's resources: objects, biomes and regions by the ids a biome sample gives. */
public class IrisPackResourcesTest {
    private static final String NAMESPACE = "example-pack";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private IrisData data;
    private ResourceLoader<IrisObject> objectLoader;
    private ResourceLoader<IrisBiome> biomeLoader;
    private ResourceLoader<IrisRegion> regionLoader;
    private IrisPackResources resources;
    private final GeneratedObject tree = new GeneratedObject(new BlockBox(0, 0, 0, 1, 1, 1), new BlockPosition(0, 0, 0),
            List.of(new ObjectBlock(new BlockPosition(0, 0, 0), BlockState.parse("minecraft:oak_log"))), List.of());

    @Before
    @SuppressWarnings("unchecked")
    public void serveAPack() throws IOException {
        File packFolder = folder.newFolder(NAMESPACE);
        data = mock(IrisData.class);
        when(data.getDataFolder()).thenReturn(packFolder);
        objectLoader = mock(ResourceLoader.class);
        biomeLoader = mock(ResourceLoader.class);
        regionLoader = mock(ResourceLoader.class);
        when(data.getObjectLoader()).thenReturn(objectLoader);
        when(data.getBiomeLoader()).thenReturn(biomeLoader);
        when(data.getRegionLoader()).thenReturn(regionLoader);

        IrisObject object = mock(IrisObject.class);
        when(object.getGeometry()).thenReturn(tree);
        when(objectLoader.getPossibleKeys()).thenReturn(new String[]{"trees/oak"});
        when(objectLoader.load("trees/oak")).thenReturn(object);

        IrisBiome dunes = new IrisBiome();
        dunes.setName("Dunes");
        dunes.setRarity(3);
        dunes.setDerivative("minecraft:desert");
        dunes.setChildren(new KList<>(List.of("desert/oasis")));
        when(biomeLoader.getPossibleKeys()).thenReturn(new String[]{"desert/dunes", "desert"});
        when(biomeLoader.load("desert/dunes")).thenReturn(dunes);
        IrisBiome sharedKeyBiome = new IrisBiome();
        sharedKeyBiome.setName("Desert biome");
        sharedKeyBiome.setDerivative("minecraft:desert");
        when(biomeLoader.load("desert")).thenReturn(sharedKeyBiome);

        IrisRegion desert = new IrisRegion();
        desert.setName("The Desert");
        desert.setRarity(2);
        desert.setLandBiomes(new KList<>(List.of("desert/dunes")));
        desert.setSeaBiomes(new KList<>(List.of("ocean")));
        desert.setShoreBiomes(new KList<>(List.of("beach")));
        desert.setCaveBiomes(new KList<>(List.of("caves/dry")));
        when(regionLoader.getPossibleKeys()).thenReturn(new String[]{"desert"});
        when(regionLoader.load("desert")).thenReturn(desert);

        resources = new IrisPackResources(data, List::of);
    }

    private ResourceId id(String path) {
        return new ResourceId(NAMESPACE, path);
    }

    @Test
    public void thePackIsNamedByItsNamespace() {
        assertEquals(NAMESPACE, resources.packId().namespace());
    }

    @Test
    public void everySnapshotHasItsOwnId() {
        assertNotEquals(resources.snapshotId(), new IrisPackResources(data, List::of).snapshotId());
        assertEquals(resources.snapshotId(), resources.snapshotId());
    }

    @Test
    public void aPackObjectIsItsGeometry() {
        Optional<GeneratedObject> found = resources.find(PackResources.OBJECTS, id("trees/oak"));

        assertSame(tree, found.orElseThrow());
    }

    @Test
    public void aPackBiomeIsBuiltFromTheBiomeFile() {
        PackBiome biome = resources.require(PackResources.BIOMES, id("desert/dunes"));

        assertEquals(new PackBiome(id("desert/dunes"), "Dunes", ResourceId.parse("minecraft:desert"), 3, List.of(id("desert/oasis"))), biome);
    }

    @Test
    public void aPackRegionIsBuiltFromTheRegionFileWithItsFourBiomeListsAsPackIds() {
        PackRegion region = resources.require(PackResources.REGIONS, id("desert"));

        assertEquals(new PackRegion(id("desert"), "The Desert", 2, List.of(id("desert/dunes")), List.of(id("ocean")),
                List.of(id("beach")), List.of(id("caves/dry"))), region);
    }

    @Test
    public void oneIdCanBeABiomeAndARegion() {
        PackBiome biome = resources.require(PackResources.BIOMES, id("desert"));
        PackRegion region = resources.require(PackResources.REGIONS, id("desert"));

        assertEquals("Desert biome", biome.name());
        assertEquals("The Desert", region.name());
    }

    @Test
    public void anIdThePackLacksForThatKindIsAbsent() {
        assertEquals(Optional.empty(), resources.find(PackResources.REGIONS, id("desert/dunes")));
        assertEquals(Optional.empty(), resources.find(PackResources.OBJECTS, id("trees/birch")));
    }

    @Test
    public void anIdOfAnotherNamespaceIsAbsent() {
        assertEquals(Optional.empty(), resources.find(PackResources.OBJECTS, new ResourceId("other", "trees/oak")));
    }

    @Test
    public void aKindIrisDoesNotServeIsRejectedWhetherOrNotTheIdExists() {
        ResourceKind<GeneratedObject> unserved = ResourceKind.of("test:unserved", "unserved", GeneratedObject.class);

        assertThrows(IllegalArgumentException.class, () -> resources.find(unserved, id("trees/oak")));
        assertThrows(IllegalArgumentException.class, () -> resources.find(unserved, id("missing")));
    }

    @Test
    public void theIdsOfAKindAreThePacksKeysOfThatKindInIdOrder() {
        assertEquals(List.of(id("desert"), id("desert/dunes")), resources.ids(PackResources.BIOMES));
        assertEquals(List.of(id("trees/oak")), resources.ids(PackResources.OBJECTS));
    }

    @Test
    public void aResourceThatExistsButFailsToLoadIsAnErrorNotAbsent() {
        when(objectLoader.getPossibleKeys()).thenReturn(new String[]{"trees/broken"});
        IrisPackResources fresh = new IrisPackResources(data, List::of);

        assertThrows(IllegalStateException.class, () -> fresh.find(PackResources.OBJECTS, id("trees/broken")));
    }

    @Test
    public void eachResourceIsBuiltOncePerSnapshot() {
        PackBiome first = resources.require(PackResources.BIOMES, id("desert/dunes"));
        PackBiome second = resources.require(PackResources.BIOMES, id("desert/dunes"));

        assertSame(first, second);
        verify(biomeLoader, times(1)).load("desert/dunes");
    }

    @Test
    public void keysWithCapitalsAreFoundByTheLowerCaseIdTheyGet() {
        when(objectLoader.getPossibleKeys()).thenReturn(new String[]{"Trees/Oak"});
        IrisObject object = mock(IrisObject.class);
        when(object.getGeometry()).thenReturn(tree);
        when(objectLoader.load("Trees/Oak")).thenReturn(object);
        IrisPackResources fresh = new IrisPackResources(data, List::of);

        assertSame(tree, fresh.require(PackResources.OBJECTS, id("trees/oak")));
    }
}
