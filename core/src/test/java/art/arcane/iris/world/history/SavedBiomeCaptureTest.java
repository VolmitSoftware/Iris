package art.arcane.iris.world.history;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.InferredType;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisRegion;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class SavedBiomeCaptureTest {
    @Test
    public void capturesSurfaceIdentityAtEveryHeightWhenCaveBiomesAreOmitted() throws Exception {
        IrisBiome surface = biome("flat");
        IrisBiome emptyCave = new IrisBiome().setInferredType(InferredType.CAVE);
        Engine engine = caveCaptureEngine(surface, emptyCave);
        SavedBiomeRuntime historical = mock(SavedBiomeRuntime.class);

        SavedBiomeChunk chunk = SavedBiomeCapture.capture(engine, stage(), historical, null);

        SavedBiomeChunk.Cell expected = new SavedBiomeChunk.Cell(1L, "flat", "flat-region");
        assertEquals(new SavedBiomeChunk.Header(-1, 2, 1L, -64, 16), chunk.header());
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                assertEquals(expected, chunk.surfaceAt(x, z));
                assertEquals(expected, chunk.caveBaseAt(x, z));
                assertEquals(1, chunk.column(x, z).vertical().size());
                for (int y = -64; y < -48; y++) {
                    assertEquals(expected, chunk.biomeAt(x, y, z));
                }
            }
        }
        assertSame(emptyCave, engine.getComplex().getCaveBiomeStream().get(-16D, 32D));
        assertNull(emptyCave.getLoadKey());
        assertTrue(emptyCave.getDecorators().isEmpty());
        assertTrue(emptyCave.getStructures().isEmpty());
        verifyNoInteractions(historical);
    }

    @Test
    public void retainsConfiguredCaveIdentityThroughTheEngineLookup() throws Exception {
        IrisBiome surface = biome("flat");
        IrisBiome cave = biome("configured-cave").setInferredType(InferredType.CAVE);
        Engine engine = caveCaptureEngine(surface, cave);

        SavedBiomeChunk chunk = SavedBiomeCapture.capture(engine, stage(), mock(SavedBiomeRuntime.class), null);

        assertSame(cave, engine.getCaveBiome(-16, 32));
        assertEquals("configured-cave", chunk.caveBaseAt(0, 0).biomeKey());
        assertEquals("configured-cave", chunk.biomeAt(0, -64, 0).biomeKey());
        assertEquals("configured-cave", chunk.biomeAt(0, -57, 0).biomeKey());
        assertEquals("flat", chunk.biomeAt(0, -56, 0).biomeKey());
        assertEquals("flat", chunk.surfaceAt(0, 0).biomeKey());
    }

    @Test
    public void stillRejectsAnUnkeyedSurfaceBiome() {
        Engine engine = caveCaptureEngine(new IrisBiome(), new IrisBiome().setInferredType(InferredType.CAVE));

        assertThrows(IllegalArgumentException.class,
                () -> SavedBiomeCapture.capture(engine, stage(), mock(SavedBiomeRuntime.class), null));
    }

    @Test
    public void retainsFloatingChildIdentityWhenItSharesTheHostDerivative() throws Exception {
        Engine engine = mock(Engine.class, RETURNS_DEEP_STUBS);
        GenerationHistory.GenerationStage stage = mock(GenerationHistory.GenerationStage.class);
        SavedBiomeRuntime historical = mock(SavedBiomeRuntime.class);
        IrisBiome host = biome("host");
        IrisBiome child = biome("floating-child");
        host.setDerivative("minecraft:plains");
        child.setDerivative("minecraft:plains");
        IrisRegion region = region("host-region");
        when(stage.activation()).thenReturn(GenerationActivation.initial("a".repeat(64), 1L));
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getHeight()).thenReturn(16);
        when(engine.getHeight(anyInt(), anyInt())).thenReturn(2);
        when(engine.getComplex().getTransitionGenerationPlan()).thenReturn(null);
        when(engine.getRegion(anyInt(), anyInt())).thenReturn(region);
        when(engine.getRegion(anyInt(), anyInt(), anyInt())).thenReturn(region);
        when(engine.getSurfaceBiome(anyInt(), anyInt())).thenReturn(host);
        when(engine.getCaveBiome(anyInt(), anyInt())).thenReturn(host);
        when(engine.getBiomeOrMantle(anyInt(), anyInt(), anyInt())).thenReturn(host);
        FloatingBiomeOverlay floating = new FloatingBiomeOverlay(16);
        FloatingBiomeOverlay.Identity childIdentity = new FloatingBiomeOverlay.Identity(child.getLoadKey(), region.getLoadKey());
        floating.record(0, 8, 0, childIdentity);
        floating.record(0, 9, 0, childIdentity);
        floating.record(4, 8, 0, childIdentity);
        floating.retainHighestSurfaces((x, z) -> x == 0 ? 9 : 12);

        SavedBiomeChunk chunk = SavedBiomeCapture.capture(engine, stage, historical, floating);

        assertEquals(host.getVanillaDerivativeKey(), child.getVanillaDerivativeKey());
        assertEquals("floating-child", chunk.surfaceAt(0, 0).biomeKey());
        assertEquals("host", chunk.surfaceAt(4, 0).biomeKey());
        assertEquals("floating-child", chunk.biomeAt(0, -56, 0).biomeKey());
        assertEquals("floating-child", chunk.biomeAt(3, -53, 3).biomeKey());
        assertEquals("host", chunk.biomeAt(0, -52, 0).biomeKey());
        assertEquals("host", chunk.caveBaseAt(0, 0).biomeKey());
        assertEquals(1L, chunk.biomeAt(0, -56, 0).activationId());
        verifyNoInteractions(historical);
    }

    @Test
    public void recordsExactSurfaceColumnsAndIndependentCaveAndVerticalIdentities() throws Exception {
        Engine engine = mock(Engine.class, RETURNS_DEEP_STUBS);
        GenerationHistory.GenerationStage stage = mock(GenerationHistory.GenerationStage.class);
        SavedBiomeRuntime historical = mock(SavedBiomeRuntime.class);
        IrisBiome left = biome("left");
        IrisBiome right = biome("right");
        IrisBiome cave = biome("base-cave");
        IrisBiome lower = biome("lower-volume");
        IrisBiome upper = biome("upper-volume");
        IrisRegion surfaceRegion = region("surface-region");
        IrisRegion caveRegion = region("volume-region");
        when(stage.chunkX()).thenReturn(-1);
        when(stage.chunkZ()).thenReturn(2);
        when(stage.activation()).thenReturn(GenerationActivation.initial("a".repeat(64), 1L));
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getHeight()).thenReturn(16);
        when(engine.getComplex().getTransitionGenerationPlan()).thenReturn(null);
        when(engine.getRegion(anyInt(), anyInt())).thenReturn(surfaceRegion);
        when(engine.getRegion(anyInt(), anyInt(), anyInt())).thenReturn(caveRegion);
        when(engine.getSurfaceBiome(anyInt(), anyInt())).thenAnswer(call -> (int) call.getArgument(0) == -15 ? right : left);
        when(engine.getCaveBiome(anyInt(), anyInt())).thenReturn(cave);
        when(engine.getBiomeOrMantle(anyInt(), anyInt(), anyInt())).thenAnswer(call -> (int) call.getArgument(1) < 8 ? lower : upper);

        SavedBiomeChunk chunk = SavedBiomeCapture.capture(engine, stage, historical, null);

        assertEquals(new SavedBiomeChunk.Header(-1, 2, 1L, -64, 16), chunk.header());
        assertEquals("left", chunk.surfaceAt(0, 0).biomeKey());
        assertEquals("right", chunk.surfaceAt(1, 0).biomeKey());
        assertEquals("surface-region", chunk.surfaceAt(1, 0).regionKey());
        assertEquals("base-cave", chunk.caveBaseAt(1, 0).biomeKey());
        assertEquals("lower-volume", chunk.biomeAt(1, -57, 0).biomeKey());
        assertEquals("upper-volume", chunk.biomeAt(1, -56, 0).biomeKey());
        assertEquals("volume-region", chunk.biomeAt(1, -56, 0).regionKey());
        assertEquals(2, chunk.column(0, 0).vertical().size());
        assertEquals(chunk.column(0, 0).vertical(), chunk.column(3, 3).vertical());
        verifyNoInteractions(historical);
    }

    private static Engine caveCaptureEngine(IrisBiome surface, IrisBiome cave) {
        Engine engine = mock(Engine.class, RETURNS_DEEP_STUBS);
        IrisRegion region = region("flat-region");
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getHeight()).thenReturn(16);
        when(engine.getHeight(anyInt(), anyInt())).thenReturn(8);
        when(engine.getDimensionStackContext()).thenReturn(null);
        when(engine.getComplex().getTransitionGenerationPlan()).thenReturn(null);
        when(engine.getComplex().getCaveBiomeStream().get(anyDouble(), anyDouble())).thenReturn(cave);
        when(engine.getRegion(anyInt(), anyInt())).thenReturn(region);
        when(engine.getRegion(anyInt(), anyInt(), anyInt())).thenReturn(region);
        when(engine.getSurfaceBiome(anyInt(), anyInt())).thenReturn(surface);
        doCallRealMethod().when(engine).getCaveBiome(anyInt(), anyInt());
        doCallRealMethod().when(engine).getBiomeOrMantle(anyInt(), anyInt(), anyInt());
        when(engine.getCaveOrMantleBiome(anyInt(), anyInt(), anyInt()))
                .thenAnswer(call -> engine.getCaveBiome(call.getArgument(0), call.getArgument(2)));
        return engine;
    }

    private static GenerationHistory.GenerationStage stage() {
        GenerationHistory.GenerationStage stage = mock(GenerationHistory.GenerationStage.class);
        when(stage.chunkX()).thenReturn(-1);
        when(stage.chunkZ()).thenReturn(2);
        when(stage.activation()).thenReturn(GenerationActivation.initial("a".repeat(64), 1L));
        return stage;
    }

    private static IrisBiome biome(String key) {
        IrisBiome biome = new IrisBiome();
        biome.setLoadKey(key);
        return biome;
    }

    private static IrisRegion region(String key) {
        IrisRegion region = new IrisRegion();
        region.setLoadKey(key);
        return region;
    }
}
