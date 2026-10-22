package art.arcane.iris.generation.chunk;

import art.arcane.iris.platform.bukkit.nms.INMS;
import art.arcane.iris.platform.bukkit.nms.INMSBinding;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.Arrays;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ChunkDataOutputTest {
    @Test
    public void nativeWriterReceivesTheSectionBuffer() {
        ChunkGenerator.ChunkData chunk = mock(ChunkGenerator.ChunkData.class);
        SectionBuffer blocks = new SectionBuffer(64);
        INMSBinding binding = mock(INMSBinding.class);
        when(binding.applyChunkDataBlocks(chunk, blocks)).thenReturn(true);

        try (MockedStatic<INMS> nms = mockStatic(INMS.class)) {
            nms.when(INMS::get).thenReturn(binding);
            ChunkDataOutput.apply(chunk, blocks);
        }

        verify(binding).applyChunkDataBlocks(chunk, blocks);
        verify(chunk, never()).setRegion(anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), any(BlockData.class));
    }

    @Test
    public void fallbackAppliesExplicitCaveAirBetweenSolidRunsWithNegativeMinimumHeight() {
        ChunkGenerator.ChunkData chunk = mock(ChunkGenerator.ChunkData.class);
        when(chunk.getMinHeight()).thenReturn(-256);
        when(chunk.getMaxHeight()).thenReturn(512);
        SectionBuffer blocks = new SectionBuffer(768);
        BlockData dirt = mock(BlockData.class);
        BlockData coal = mock(BlockData.class);
        BlockData caveAir = mock(BlockData.class);
        NativeBlockState dirtState = state(dirt);
        NativeBlockState coalState = state(coal);
        NativeBlockState caveAirState = state(caveAir);
        BlockData[] applied = new BlockData[768];
        Arrays.fill(applied, dirt);
        for (int y = 0; y <= 75; y++) {
            blocks.setRaw(14, y + 256, 6, y < 33 ? dirtState : y <= 54 ? caveAirState : coalState);
        }
        doAnswer(call -> {
            int y = call.getArgument(1);
            applied[y + 256] = call.getArgument(3);
            return null;
        }).when(chunk).setBlock(anyInt(), anyInt(), anyInt(), any(BlockData.class));
        doAnswer(call -> {
            int from = call.getArgument(1);
            int to = call.getArgument(4);
            BlockData block = call.getArgument(6);
            Arrays.fill(applied, from + 256, to + 256, block);
            return null;
        }).when(chunk).setRegion(anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), any(BlockData.class));

        try (MockedStatic<INMS> nms = mockStatic(INMS.class)) {
            nms.when(INMS::get).thenReturn(mock(INMSBinding.class));
            ChunkDataOutput.apply(chunk, blocks);
        }

        for (int y = -256; y < 512; y++) {
            BlockData expected = y >= 33 && y <= 54 ? caveAir : y >= 55 && y <= 75 ? coal : dirt;
            assertSame("column at " + y, expected, applied[y + 256]);
        }
        assertTrue(blocks.highestStoredY(14, 6) == 331);
    }

    private static NativeBlockState state(BlockData data) {
        NativeBlockState state = mock(NativeBlockState.class);
        when(state.nativeHandle()).thenReturn(data);
        return state;
    }
}
