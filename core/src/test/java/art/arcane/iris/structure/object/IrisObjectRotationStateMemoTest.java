package art.arcane.iris.structure.object;

import art.arcane.iris.platform.bukkit.BukkitBlockState;
import art.arcane.iris.testsupport.BukkitTestServer;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import org.bukkit.Axis;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

public class IrisObjectRotationStateMemoTest {
    @BeforeClass
    public static void installServer() {
        BukkitTestServer.install();
    }

    @Test
    public void rotatedStatesAreComputedOncePerQuantizedSpin() {
        AtomicInteger clones = new AtomicInteger();
        BlockData original = log("iristest:memo_log[axis=x]", Axis.X);
        doAnswer(invocation -> {
            clones.incrementAndGet();
            return log("iristest:memo_log[axis=x]", Axis.X);
        }).when(original).clone();
        NativeBlockState state = BukkitBlockState.of(original);
        IrisObjectRotation rotation = IrisObjectRotation.of(0, 90, 0);

        NativeBlockState first = rotation.rotate(state, 0, 0, 0);
        NativeBlockState repeated = rotation.rotate(state, 0, 0, 0);
        rotation.rotate(state, 0, 30, 0);
        rotation.rotate(state, 0, 90, 0);

        assertSame(first, repeated);
        assertEquals(2, clones.get());
        assertEquals("iristest:memo_log[axis=x]", first.key());
    }

    private static BlockData log(String key, Axis axis) {
        BlockData data = mock(BlockData.class, withSettings().extraInterfaces(Orientable.class));
        doReturn(key).when(data).getAsString();
        doReturn(key).when(data).getAsString(anyBoolean());
        doReturn(axis).when((Orientable) data).getAxis();
        doReturn(Set.of(Axis.X, Axis.Y, Axis.Z)).when((Orientable) data).getAxes();
        return data;
    }
}
