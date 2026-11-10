package art.arcane.iris.engine.object.prefab;

import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.math.Vector3i;
import com.volmit.object.ScaleMethod;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Rotation drops blocks with no valid orientation and their tiles; scaling reads one snapshot of the object. */
public class IrisObjectTransformsStabilityTest {
    private static final long WAIT_SECONDS = 5;
    private static final long PARK_MILLIS = 1;
    private static final int QUARTER_TURN_DEGREES = 90;
    private static final double SCALE = 2;

    @Before
    public void bindPlatform() {
        IrisPlatforms.unbind();
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.block(anyString())).thenAnswer(call -> state(call.getArgument(0)));
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);
    }

    @After
    public void unbindPlatform() {
        IrisPlatforms.unbind();
    }

    @Test
    public void rotationOmitsUnsupportedBlocksAndTheirTiles() {
        IrisObject source = new IrisObject(3, 1, 1);
        PlatformBlockState unsupported = state("minecraft:wall_torch");
        PlatformBlockState chest = state("minecraft:chest");
        source.setUnsigned(0, 0, 0, unsupported);
        source.setUnsignedTile(0, 0, 0, tile("minecraft:wall_torch", "omitted"));
        source.setUnsigned(1, 0, 0, chest);
        source.setUnsignedTile(1, 0, 0, tile("minecraft:chest", "kept"));
        source.setUnsigned(2, 0, 0, state("minecraft:stone"));
        BlockStateRotation.StateRotator previous = BlockStateRotation.bindPlatformRotator(
                (turn, block) -> block == unsupported ? null : block);
        IrisObject rotated;
        try {
            rotated = source.rotateCopy(ObjectTurn.of(0, QUARTER_TURN_DEGREES, 0));
        } finally {
            BlockStateRotation.restorePlatformRotator(previous);
        }

        assertEquals(2, rotated.getBlocks().size());
        assertEquals(1, rotated.getStates().size());
        rotated.getStates().forEach(entry -> {
            assertEquals(chest, rotated.getBlocks().get(entry.getKey()));
            assertEquals("kept", entry.getValue().getProperties().get("name"));
        });
        assertEquals(3, source.getBlocks().size());
        assertEquals(2, source.getStates().size());
    }

    @Test
    public void scalingWaitsForMatchingGeometryAndVolume() throws Exception {
        IrisObject source = new IrisObject(9, 5, 3);
        source.setUnsigned(0, 0, 0, state("minecraft:stone"));
        FutureTask<IrisObject> scaling = new FutureTask<>(() -> source.scaled(SCALE, ScaleMethod.NONE));
        Thread worker = new Thread(scaling, "object-scaling-snapshot");
        source.writeLock.lock();
        try {
            worker.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
            while (worker.getState() != Thread.State.WAITING && System.nanoTime() < deadline) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(PARK_MILLIS));
            }
            assertEquals(Thread.State.WAITING, worker.getState());
            source.setW(2);
            source.setH(2);
            source.setD(2);
            source.setCenter(new Vector3i(1, 1, 1));
            source.getBlocks().clear();
            source.setUnsigned(0, 0, 0, state("minecraft:chest"));
        } finally {
            source.writeLock.unlock();
        }
        try {
            IrisObject actual = scaling.get(WAIT_SECONDS, TimeUnit.SECONDS);
            IrisObject expected = source.scaled(SCALE, ScaleMethod.NONE);

            assertEquals(expected.getW(), actual.getW());
            assertEquals(expected.getH(), actual.getH());
            assertEquals(expected.getD(), actual.getD());
            assertEquals(expected.getBlocks().size(), actual.getBlocks().size());
            expected.getBlocks().forEach(entry -> {
                assertNotNull(actual.getBlocks().get(entry.getKey()));
                assertEquals(entry.getValue(), actual.getBlocks().get(entry.getKey()));
            });
        } finally {
            worker.interrupt();
            worker.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));
        }
    }

    private static PlatformBlockState state(String key) {
        PlatformBlockState state = mock(PlatformBlockState.class);
        when(state.key()).thenReturn(key);
        when(state.materialKey()).thenReturn(key);
        when(state.isAir()).thenReturn(false);
        when(state.isSolid()).thenReturn(true);
        return state;
    }

    private static TileData tile(String id, String name) {
        TileData tile = new TileData(id, new KMap<>());
        tile.getProperties().put("name", name);
        return tile;
    }
}
