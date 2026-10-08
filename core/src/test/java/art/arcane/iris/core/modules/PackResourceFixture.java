package art.arcane.iris.core.modules;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.engine.object.prefab.IrisObject;
import com.volmit.block.BlockState;
import com.volmit.mantle.BlockBox;
import com.volmit.object.GeneratedObject;
import com.volmit.object.ObjectBlock;
import com.volmit.resource.ResourceKey;
import com.volmit.world.BlockPosition;

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

    /** The key of the oak in the pack in {@code packFolder}. */
    public static ResourceKey<GeneratedObject> oakKey(java.io.File packFolder) {
        return new ResourceKey<>(new PackResourceIds(packFolder).of(OAK_KEY), GeneratedObject.class);
    }
}
