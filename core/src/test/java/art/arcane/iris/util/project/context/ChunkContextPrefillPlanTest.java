package art.arcane.iris.util.project.context;

import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisRegion;
import art.arcane.iris.util.project.stream.ProceduralStream;
import org.bukkit.block.data.BlockData;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class ChunkContextPrefillPlanTest {
    @Test
    public void noCavePrefillSkipsCaveCacheFill() {
        AtomicInteger caveCalls = new AtomicInteger();
        AtomicInteger heightCalls = new AtomicInteger();
        AtomicInteger biomeCalls = new AtomicInteger();
        AtomicInteger rockCalls = new AtomicInteger();
        AtomicInteger fluidCalls = new AtomicInteger();
        AtomicInteger regionCalls = new AtomicInteger();
        ChunkContext context = createContext(
                ChunkContext.PrefillPlan.NO_CAVE,
                caveCalls,
                heightCalls,
                biomeCalls,
                rockCalls,
                fluidCalls,
                regionCalls
        );

        assertEquals(256, heightCalls.get());
        assertEquals(256, biomeCalls.get());
        assertEquals(256, rockCalls.get());
        assertEquals(256, fluidCalls.get());
        assertEquals(256, regionCalls.get());
        assertEquals(0, caveCalls.get());

        assertEquals(34051D, context.getHeight().get(2, 3), 0D);
        context.getCave().get(2, 3);
        context.getCave().get(2, 3);
        assertEquals(1, caveCalls.get());
    }

    @Test
    public void allPrefillIncludesCaveCacheFill() {
        AtomicInteger caveCalls = new AtomicInteger();
        AtomicInteger heightCalls = new AtomicInteger();
        AtomicInteger biomeCalls = new AtomicInteger();
        AtomicInteger rockCalls = new AtomicInteger();
        AtomicInteger fluidCalls = new AtomicInteger();
        AtomicInteger regionCalls = new AtomicInteger();
        ChunkContext context = createContext(
                ChunkContext.PrefillPlan.ALL,
                caveCalls,
                heightCalls,
                biomeCalls,
                rockCalls,
                fluidCalls,
                regionCalls
        );

        assertEquals(256, heightCalls.get());
        assertEquals(256, biomeCalls.get());
        assertEquals(256, rockCalls.get());
        assertEquals(256, fluidCalls.get());
        assertEquals(256, regionCalls.get());
        assertEquals(256, caveCalls.get());

        context.getCave().get(1, 1);
        assertEquals(256, caveCalls.get());
    }

    private ChunkContext createContext(
            ChunkContext.PrefillPlan prefillPlan,
            AtomicInteger caveCalls,
            AtomicInteger heightCalls,
            AtomicInteger biomeCalls,
            AtomicInteger rockCalls,
            AtomicInteger fluidCalls,
            AtomicInteger regionCalls
    ) {
        IrisComplex complex = mock(IrisComplex.class);

        @SuppressWarnings("unchecked")
        ProceduralStream<Double> heightStream = mock(ProceduralStream.class);
        doAnswer(invocation -> {
            heightCalls.incrementAndGet();
            double worldX = invocation.getArgument(0);
            double worldZ = invocation.getArgument(1);
            return (worldX * 1000D) + worldZ;
        }).when(heightStream).get(anyDouble(), anyDouble());

        @SuppressWarnings("unchecked")
        ProceduralStream<IrisBiome> biomeStream = mock(ProceduralStream.class);
        IrisBiome biome = mock(IrisBiome.class);
        doAnswer(invocation -> {
            biomeCalls.incrementAndGet();
            return biome;
        }).when(biomeStream).get(anyDouble(), anyDouble());

        @SuppressWarnings("unchecked")
        ProceduralStream<IrisBiome> caveStream = mock(ProceduralStream.class);
        IrisBiome caveBiome = mock(IrisBiome.class);
        doAnswer(invocation -> {
            caveCalls.incrementAndGet();
            return caveBiome;
        }).when(caveStream).get(anyDouble(), anyDouble());

        @SuppressWarnings("unchecked")
        ProceduralStream<BlockData> rockStream = mock(ProceduralStream.class);
        BlockData rock = mock(BlockData.class);
        doAnswer(invocation -> {
            rockCalls.incrementAndGet();
            return rock;
        }).when(rockStream).get(anyDouble(), anyDouble());

        @SuppressWarnings("unchecked")
        ProceduralStream<BlockData> fluidStream = mock(ProceduralStream.class);
        BlockData fluid = mock(BlockData.class);
        doAnswer(invocation -> {
            fluidCalls.incrementAndGet();
            return fluid;
        }).when(fluidStream).get(anyDouble(), anyDouble());

        @SuppressWarnings("unchecked")
        ProceduralStream<IrisRegion> regionStream = mock(ProceduralStream.class);
        IrisRegion region = mock(IrisRegion.class);
        doAnswer(invocation -> {
            regionCalls.incrementAndGet();
            return region;
        }).when(regionStream).get(anyDouble(), anyDouble());

        doReturn(heightStream).when(complex).getHeightStream();
        doReturn(biomeStream).when(complex).getTrueBiomeStream();
        doReturn(caveStream).when(complex).getCaveBiomeStream();
        doReturn(rockStream).when(complex).getRockStream();
        doReturn(fluidStream).when(complex).getFluidStream();
        doReturn(regionStream).when(complex).getRegionStream();

        return new ChunkContext(32, 48, complex, true, prefillPlan, null);
    }
}
