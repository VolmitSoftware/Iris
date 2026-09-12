package art.arcane.iris.pack.value;

import art.arcane.iris.generation.block.BlockDataMergeSupport;
import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.structure.object.IrisObjectRotation;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.collection.KMap;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PlatformStateHookContractTest {
    @Test
    public void boundRotatorWinsWhenBukkitClassesArePresent() {
        PlatformBlockState source = mock(PlatformBlockState.class);
        PlatformBlockState rotated = mock(PlatformBlockState.class);
        IrisObjectRotation.StateRotator hook = mock(IrisObjectRotation.StateRotator.class);
        IrisObjectRotation rotation = new IrisObjectRotation();
        when(hook.rotate(rotation, source, 1, 2, 3)).thenReturn(rotated);
        IrisObjectRotation.StateRotator previous = IrisObjectRotation.bindPlatformRotator(hook);
        try {
            assertSame(rotated, rotation.rotate(source, 1, 2, 3));
        } finally {
            IrisObjectRotation.restorePlatformRotator(previous);
        }
    }

    @Test
    public void boundMergerWinsWhenBukkitClassesArePresent() {
        PlatformBlockState base = mock(PlatformBlockState.class);
        PlatformBlockState update = mock(PlatformBlockState.class);
        PlatformBlockState merged = mock(PlatformBlockState.class);
        BlockDataMergeSupport.StateMerger hook = mock(BlockDataMergeSupport.StateMerger.class);
        when(hook.merge(base, update)).thenReturn(merged);
        BlockDataMergeSupport.StateMerger previous = BlockDataMergeSupport.bindPlatformMerger(hook);
        try {
            assertSame(merged, BlockDataMergeSupport.merge(base, update));
        } finally {
            BlockDataMergeSupport.restorePlatformMerger(previous);
        }
    }

    @Test
    public void boundTileFactoryWinsWhenBukkitClassesArePresent() {
        PlatformBlockState state = mock(PlatformBlockState.class);
        KMap<String, Object> properties = new KMap<>();
        TileData expected = mock(TileData.class);
        TileData.TileFactory hook = mock(TileData.TileFactory.class);
        when(hook.create(state, properties)).thenReturn(expected);
        TileData.TileFactory previous = TileData.bindPlatformFactory(hook);
        try {
            assertSame(expected, TileData.of(state, properties));
        } finally {
            TileData.restorePlatformFactory(previous);
        }
    }

    @Test
    public void boundTileReaderWinsWhenBukkitClassesArePresent() throws IOException {
        TileData expected = mock(TileData.class);
        TileData.TileReader hook = mock(TileData.TileReader.class);
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(new byte[0]));
        when(hook.read(input)).thenReturn(expected);
        TileData.TileReader previous = TileData.bindPlatformReader(hook);
        try {
            assertSame(expected, TileData.read(input));
        } finally {
            TileData.restorePlatformReader(previous);
        }
    }
}
