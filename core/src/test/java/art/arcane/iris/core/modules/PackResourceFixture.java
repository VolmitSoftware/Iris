package art.arcane.iris.core.modules;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import com.volmit.block.BlockState;
import com.volmit.geometry.BlockBox;
import com.volmit.geometry.BlockPosition;
import com.volmit.object.GeneratedObject;
import com.volmit.object.ObjectBlock;
import com.volmit.resource.PackResources;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A pack with one object, {@code trees/oak}, served through the same {@link IrisPackResources} every stage of a snapshot shares. */
public final class PackResourceFixture {
    public static final String OAK_KEY = "trees/oak";
    public static final GeneratedObject OAK = new GeneratedObject(new BlockBox(0, 0, 0, 1, 2, 1), new BlockPosition(0, 0, 0),
            List.of(new ObjectBlock(new BlockPosition(0, 0, 0), BlockState.parse("minecraft:oak_log")),
                    new ObjectBlock(new BlockPosition(0, 1, 0), BlockState.parse("minecraft:oak_leaves"))), List.of());

    private PackResourceFixture() {
    }

    /** Makes {@code data} (a mock whose data folder is set) serve the pack's resources, and returns them. */
    @SuppressWarnings("unchecked")
    public static IrisPackResources serve(IrisData data) {
        ResourceLoader<IrisObject> objects = mock(ResourceLoader.class);
        IrisObject oak = mock(IrisObject.class);
        when(oak.getGeometry()).thenReturn(OAK);
        when(objects.getPossibleKeys()).thenReturn(new String[]{OAK_KEY});
        when(objects.load(OAK_KEY)).thenReturn(oak);
        when(data.getObjectLoader()).thenReturn(objects);
        IrisPackResources resources = new IrisPackResources(data);
        when(data.getPackResources()).thenReturn(resources);
        return resources;
    }

    /** The oak {@code resources} serve, as a module finds it by its key. */
    public static GeneratedObject oak(PackResources resources) {
        return resources.require(PackResources.OBJECTS, resources.idOf(OAK_KEY));
    }

    /** The resources of a pack in {@code packFolder} without objects, biomes or regions, which names its keys by id. */
    @SuppressWarnings("unchecked")
    public static IrisPackResources empty(java.io.File packFolder) {
        IrisData data = mock(IrisData.class);
        when(data.getDataFolder()).thenReturn(packFolder);
        holdNoObjectsBiomesOrRegions(data);
        return new IrisPackResources(data, List::of);
    }

    /** Makes {@code data} (a mock) a pack without objects, biomes or regions, so resources built over it index none. */
    @SuppressWarnings("unchecked")
    public static void holdNoObjectsBiomesOrRegions(IrisData data) {
        ResourceLoader<IrisObject> objects = mock(ResourceLoader.class);
        ResourceLoader<IrisBiome> biomes = mock(ResourceLoader.class);
        ResourceLoader<IrisRegion> regions = mock(ResourceLoader.class);
        when(objects.getPossibleKeys()).thenReturn(new String[0]);
        when(biomes.getPossibleKeys()).thenReturn(new String[0]);
        when(regions.getPossibleKeys()).thenReturn(new String[0]);
        when(data.getObjectLoader()).thenReturn(objects);
        when(data.getBiomeLoader()).thenReturn(biomes);
        when(data.getRegionLoader()).thenReturn(regions);
    }
}
