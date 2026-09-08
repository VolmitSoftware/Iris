package art.arcane.iris.probe;

import art.arcane.iris.engine.DimensionStackContext;
import art.arcane.iris.engine.DimensionStackLayout;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.testsupport.IrisRuntimeState;
import org.junit.AfterClass;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class DimensionStackSuperflatProbeTest {
    @AfterClass
    public static void releaseProbeRuntime() {
        IrisRuntimeState.reset();
    }

    private static final long SEED = 1_337L;
    private static final long ALTERNATE_SEED = 8_675_309L;
    private static final int WORLD_HEIGHT = 128;
    private static final int TERRAIN_HEIGHT = 15;
    private static final int SPACER = 8;
    private static final int BLEND_AMPLITUDE = 4;
    private static final String AIR = "minecraft:air";
    private static final String BOTTOM_ROCK = "minecraft:blue_concrete";
    private static final String BOTTOM_SURFACE = "minecraft:light_blue_wool";
    private static final String MIDDLE_ROCK = "minecraft:red_concrete";
    private static final String MIDDLE_SURFACE = "minecraft:pink_wool";
    private static final String TOP_ROCK = "minecraft:green_concrete";
    private static final String TOP_SURFACE = "minecraft:lime_wool";
    private static final String FLUID_ROCK = "minecraft:purple_concrete";
    private static final String FLUID_SURFACE = "minecraft:yellow_wool";
    private static final String FLUID = "minecraft:water";
    private static final String FLUID_SEA_LAYER = "minecraft:ice";
    private static final String BOTTOM_BIOME = "minecraft:plains";
    private static final String MIDDLE_BIOME = "minecraft:desert";
    private static final String TOP_BIOME = "minecraft:forest";
    private static final List<ChunkCoordinate> EXACT_CHUNKS = List.of(
            new ChunkCoordinate(-1, -1),
            new ChunkCoordinate(0, 0),
            new ChunkCoordinate(1, 1)
    );
    private static final List<ChunkCoordinate> BLENDED_CHUNKS = List.of(
            new ChunkCoordinate(-1, 0),
            new ChunkCoordinate(0, 0),
            new ChunkCoordinate(1, 0),
            new ChunkCoordinate(0, 1)
    );

    @Test
    public void stackedSuperflatDimensionsKeepExactUprightBandsAcrossSignedChunks() throws Exception {
        File pack = packFixture();
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(
                pack, "stack-root", "[dimension-stack-superflat-test]")) {
            try (RealPackProbeSupport.EngineSession session = workspace.openEngine(
                    SEED, false, "exact-stack")) {
                Engine engine = session.engine();
                assertHostEnvelopeAndSourceBounds(engine);
                for (ChunkCoordinate coordinate : EXACT_CHUNKS) {
                    RealPackProbeSupport.GeneratedChunk chunk = RealPackProbeSupport.generateChunk(
                            engine, coordinate.x(), coordinate.z());
                    assertExactChunk(chunk);
                    assertExactBiomes(chunk);
                }
                for (ChunkCoordinate coordinate : EXACT_CHUNKS) {
                    int blockX = coordinate.x() << 4;
                    int blockZ = coordinate.z() << 4;
                    assertEquals("height query must resolve the top stacked surface at "
                                    + blockX + "," + blockZ,
                            63, engine.getHeight(blockX, blockZ, false));
                }
            }
        }
    }

    @Test
    public void outOfPoolStackFocusRegionIdentityIsStableAcrossEngines() throws Exception {
        File pack = packFixture();
        String first;
        String repeated;
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(
                pack, "stack-root", "[dimension-stack-focus-region-test]")) {
            try (RealPackProbeSupport.EngineSession session = workspace.openEngine(
                    SEED, false, "focus-region-first")) {
                first = session.engine().getRegion(0, 0).getLoadKey();
            }
            try (RealPackProbeSupport.EngineSession session = workspace.openEngine(
                    SEED, false, "focus-region-repeated")) {
                repeated = session.engine().getRegion(0, 0).getLoadKey();
            }
        }

        assertEquals(first, repeated);
    }

    @Test
    public void blendedStackProducesDeterministicBoundedSmoothGapsAcrossChunkSeams() throws Exception {
        File pack = packFixture();
        Map<ColumnCoordinate, ColumnLayout> first;
        Map<ColumnCoordinate, ColumnLayout> repeated;
        Map<ColumnCoordinate, ColumnLayout> alternateSeed;
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(
                pack, "stack-blended", "[dimension-stack-blended-test]")) {
            first = captureBlendedColumns(workspace, SEED, "first");
            repeated = captureBlendedColumns(workspace, SEED, "repeated");
            alternateSeed = captureBlendedColumns(workspace, ALTERNATE_SEED, "alternate-seed");
        }

        assertEquals("same seed must reproduce every blended stack boundary", first, repeated);
        assertDifferentSeedLayouts(first, alternateSeed);
        assertGapVariation(first, ColumnLayout::middleGap, "middle");
        assertGapVariation(first, ColumnLayout::topGap, "top");
        assertChunkSeam(first, -1, 0, 0, 0);
        assertChunkSeam(first, 15, 0, 16, 0);
        assertChunkSeam(first, 0, 15, 0, 16);
    }

    @Test
    public void stackedFluidLayerKeepsTerrainAndFluidHeightsAndUsesItsOwnPalettes() throws Exception {
        File pack = packFixture();
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(
                pack, "stack-fluid", "[dimension-stack-fluid-test]")) {
            try (RealPackProbeSupport.EngineSession session = workspace.openEngine(
                    SEED, false, "fluid-stack")) {
                Engine engine = session.engine();
                DimensionStackContext stackContext = requireStackContext(engine);
                DimensionStackLayout layout = stackContext.sample(0, 0);
                DimensionStackLayout.Layer fluidLayer = layout.layersTopToBottom().get(0);

                assertEquals("fluid", fluidLayer.terrainContext().getDimension().getLoadKey());
                assertEquals(28, fluidLayer.surfaceY());
                assertEquals(36, fluidLayer.fluidY());
                assertEquals(36, fluidLayer.contentTopY());
                assertTrue(fluidLayer.visible());
                assertTrue(fluidLayer.fluidY() > fluidLayer.surfaceY());
                assertEquals(28, stackContext.getStackTerrainHeight(0, 0));
                assertEquals(36, stackContext.getStackTopHeight(0, 0));
                assertEquals(28, engine.getHeight(0, 0, true));
                assertEquals(36, engine.getHeight(0, 0, false));

                RealPackProbeSupport.GeneratedChunk chunk = RealPackProbeSupport.generateChunk(engine, 0, 0);
                assertRange(chunk, 0, 0, 0, 14, BOTTOM_ROCK);
                assertBlock(chunk, 0, 0, 15, BOTTOM_SURFACE);
                assertRange(chunk, 0, 0, 16, 23, AIR);
                assertRange(chunk, 0, 0, 24, 27, FLUID_ROCK);
                assertBlock(chunk, 0, 0, 28, FLUID_SURFACE);
                assertRange(chunk, 0, 0, 29, 35, FLUID);
                assertBlock(chunk, 0, 0, 36, FLUID_SEA_LAYER);
                assertRange(chunk, 0, 0, 37, WORLD_HEIGHT - 1, AIR);
            }
        }
    }

    @Test
    public void whollyCeilingClippedLayerDoesNotRaiseEngineOrRenderedContextHeights() throws Exception {
        File pack = packFixture();
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(
                pack, "stack-clipped", "[dimension-stack-clipped-test]")) {
            try (RealPackProbeSupport.EngineSession session = workspace.openEngine(
                    SEED, false, "clipped-stack")) {
                Engine engine = session.engine();
                DimensionStackContext stackContext = requireStackContext(engine);
                DimensionStackLayout layout = stackContext.sample(0, 0);
                DimensionStackLayout.Layer bottomLayer = layout.layersBottomToTop().get(0);
                DimensionStackLayout.Layer clippedLayer = layout.layersBottomToTop().get(1);

                assertEquals(-64, engine.getMinHeight());
                assertEquals(64, engine.getHeight());
                assertTrue(bottomLayer.visible());
                assertFalse(clippedLayer.visible());
                assertEquals(80, clippedLayer.localBaseY());
                assertEquals(95, layout.stackTerrainTopY());
                assertEquals(95, layout.stackTopY());
                assertEquals(15, layout.clippedStackTerrainTopY());
                assertEquals(15, layout.clippedStackTopY());
                assertEquals(15, stackContext.getStackTerrainHeight(0, 0));
                assertEquals(15, stackContext.getStackTopHeight(0, 0));
                assertEquals(15, engine.getHeight(0, 0, true));
                assertEquals(15, engine.getHeight(0, 0, false));
                assertEquals("stack-clipped", stackContext.getLayerAt(0, 63, 0)
                        .terrainContext().getDimension().getLoadKey());
            }
        }
    }

    private static Map<ColumnCoordinate, ColumnLayout> captureBlendedColumns(
            RealPackProbeSupport.Workspace workspace,
            long seed,
            String runLabel
    ) throws Exception {
        LinkedHashMap<ColumnCoordinate, ColumnLayout> layouts = new LinkedHashMap<>();
        try (RealPackProbeSupport.EngineSession session = workspace.openEngine(seed, false, runLabel)) {
            Engine engine = session.engine();
            for (ChunkCoordinate coordinate : BLENDED_CHUNKS) {
                RealPackProbeSupport.GeneratedChunk chunk = RealPackProbeSupport.generateChunk(
                        engine, coordinate.x(), coordinate.z());
                int minimumX = coordinate.x() << 4;
                int minimumZ = coordinate.z() << 4;
                for (int localX = 0; localX < 16; localX++) {
                    for (int localZ = 0; localZ < 16; localZ++) {
                        int blockX = minimumX + localX;
                        int blockZ = minimumZ + localZ;
                        ColumnLayout layout = assertBlendedColumn(chunk, blockX, blockZ);
                        assertEquals("height query must match blended top surface at "
                                        + blockX + "," + blockZ,
                                layout.topSurface(), engine.getHeight(blockX, blockZ, false));
                        layouts.put(new ColumnCoordinate(blockX, blockZ), layout);
                    }
                }
            }
        }
        return Map.copyOf(layouts);
    }

    private static void assertHostEnvelopeAndSourceBounds(Engine engine) {
        assertEquals(-64, engine.getMinHeight());
        assertEquals(WORLD_HEIGHT, engine.getHeight());
        DimensionStackLayout layout = requireStackContext(engine).sample(0, 0);
        List<DimensionStackLayout.Layer> layers = layout.layersBottomToTop();
        assertLayerBounds(layers.get(0), "stack-root", -64, 64, 128);
        assertLayerBounds(layers.get(1), "middle", -32, 32, 64);
        assertLayerBounds(layers.get(2), "top", 0, 64, 64);
    }

    private static void assertLayerBounds(
            DimensionStackLayout.Layer layer,
            String dimensionKey,
            int minimumY,
            int maximumY,
            int localHeight
    ) {
        assertEquals(dimensionKey, layer.terrainContext().getDimension().getLoadKey());
        assertEquals(minimumY, layer.terrainContext().getDimension().getMinHeight());
        assertEquals(maximumY, layer.terrainContext().getDimension().getMaxHeight());
        assertEquals(localHeight, layer.terrainContext().getLocalHeight());
    }

    private static DimensionStackContext requireStackContext(Engine engine) {
        DimensionStackContext stackContext = engine.getDimensionStackContext();
        assertNotNull("dimension stack context is missing", stackContext);
        return stackContext;
    }

    private static void assertExactChunk(RealPackProbeSupport.GeneratedChunk chunk) {
        int minimumX = chunk.chunkX() << 4;
        int minimumZ = chunk.chunkZ() << 4;
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int blockX = minimumX + localX;
                int blockZ = minimumZ + localZ;
                assertRange(chunk, blockX, blockZ, 0, 14, BOTTOM_ROCK);
                assertBlock(chunk, blockX, blockZ, 15, BOTTOM_SURFACE);
                assertRange(chunk, blockX, blockZ, 16, 23, AIR);
                assertRange(chunk, blockX, blockZ, 24, 38, MIDDLE_ROCK);
                assertBlock(chunk, blockX, blockZ, 39, MIDDLE_SURFACE);
                assertRange(chunk, blockX, blockZ, 40, 47, AIR);
                assertRange(chunk, blockX, blockZ, 48, 62, TOP_ROCK);
                assertBlock(chunk, blockX, blockZ, 63, TOP_SURFACE);
                assertRange(chunk, blockX, blockZ, 64, WORLD_HEIGHT - 1, AIR);
            }
        }
    }

    private static void assertExactBiomes(RealPackProbeSupport.GeneratedChunk chunk) {
        int blockX = chunk.chunkX() << 4;
        int blockZ = chunk.chunkZ() << 4;
        assertEquals(BOTTOM_BIOME, chunk.biomeAt(blockX, 15, blockZ).key());
        assertEquals(BOTTOM_BIOME, chunk.biomeAt(blockX, 20, blockZ).key());
        assertEquals(MIDDLE_BIOME, chunk.biomeAt(blockX, 30, blockZ).key());
        assertEquals(TOP_BIOME, chunk.biomeAt(blockX, 55, blockZ).key());
    }

    private static ColumnLayout assertBlendedColumn(
            RealPackProbeSupport.GeneratedChunk chunk,
            int blockX,
            int blockZ
    ) {
        assertRange(chunk, blockX, blockZ, 0, TERRAIN_HEIGHT - 1, BOTTOM_ROCK);
        assertBlock(chunk, blockX, blockZ, TERRAIN_HEIGHT, BOTTOM_SURFACE);

        int middleBase = findBlock(chunk, blockX, blockZ, TERRAIN_HEIGHT + 1, MIDDLE_ROCK);
        int middleSurface = findBlock(chunk, blockX, blockZ, middleBase + 1, MIDDLE_SURFACE);
        int topBase = findBlock(chunk, blockX, blockZ, middleSurface + 1, TOP_ROCK);
        int topSurface = findBlock(chunk, blockX, blockZ, topBase + 1, TOP_SURFACE);
        ColumnLayout layout = new ColumnLayout(middleBase, middleSurface, topBase, topSurface);

        assertRange(chunk, blockX, blockZ, TERRAIN_HEIGHT + 1, middleBase - 1, AIR);
        assertRange(chunk, blockX, blockZ, middleBase, middleSurface - 1, MIDDLE_ROCK);
        assertRange(chunk, blockX, blockZ, middleSurface + 1, topBase - 1, AIR);
        assertRange(chunk, blockX, blockZ, topBase, topSurface - 1, TOP_ROCK);
        assertRange(chunk, blockX, blockZ, topSurface + 1, WORLD_HEIGHT - 1, AIR);
        assertEquals("middle band must remain upright and sixteen blocks thick at "
                        + blockX + "," + blockZ,
                TERRAIN_HEIGHT, middleSurface - middleBase);
        assertEquals("top band must remain upright and sixteen blocks thick at "
                        + blockX + "," + blockZ,
                TERRAIN_HEIGHT, topSurface - topBase);
        assertGapBounds(layout.middleGap(), blockX, blockZ, "middle");
        assertGapBounds(layout.topGap(), blockX, blockZ, "top");
        return layout;
    }

    private static void assertGapBounds(int gap, int blockX, int blockZ, String layer) {
        int minimum = SPACER - BLEND_AMPLITUDE;
        int maximum = SPACER + BLEND_AMPLITUDE;
        assertTrue(layer + " gap at " + blockX + "," + blockZ + " was " + gap
                        + ", expected " + minimum + ".." + maximum,
                gap >= minimum && gap <= maximum);
    }

    private static void assertGapVariation(
            Map<ColumnCoordinate, ColumnLayout> layouts,
            GapSelector selector,
            String layer
    ) {
        Set<Integer> gaps = new HashSet<>();
        for (ColumnLayout layout : layouts.values()) {
            gaps.add(selector.gap(layout));
        }
        assertTrue(layer + " blend must produce more than one gap width, observed " + gaps,
                gaps.size() > 1);
    }

    private static void assertDifferentSeedLayouts(
            Map<ColumnCoordinate, ColumnLayout> first,
            Map<ColumnCoordinate, ColumnLayout> second
    ) {
        assertEquals(first.keySet(), second.keySet());
        int changedColumns = 0;
        for (Map.Entry<ColumnCoordinate, ColumnLayout> entry : first.entrySet()) {
            ColumnLayout alternate = second.get(entry.getKey());
            ColumnLayout original = entry.getValue();
            if (original.middleGap() != alternate.middleGap()
                    || original.topGap() != alternate.topGap()) {
                changedColumns++;
            }
        }
        assertTrue("different seeds must change at least one blended boundary across the sampled area",
                changedColumns > 0);
    }

    private static void assertChunkSeam(
            Map<ColumnCoordinate, ColumnLayout> layouts,
            int firstX,
            int firstZ,
            int secondX,
            int secondZ
    ) {
        ColumnLayout first = layouts.get(new ColumnCoordinate(firstX, firstZ));
        ColumnLayout second = layouts.get(new ColumnCoordinate(secondX, secondZ));
        assertNotNull("missing first chunk-seam column", first);
        assertNotNull("missing second chunk-seam column", second);
        assertTrue("middle gap jumped across chunk seam " + firstX + "," + firstZ
                        + " -> " + secondX + "," + secondZ,
                Math.abs(first.middleGap() - second.middleGap()) <= 1);
        assertTrue("top gap jumped across chunk seam " + firstX + "," + firstZ
                        + " -> " + secondX + "," + secondZ,
                Math.abs(first.topGap() - second.topGap()) <= 1);
    }

    private static int findBlock(
            RealPackProbeSupport.GeneratedChunk chunk,
            int blockX,
            int blockZ,
            int startY,
            String expected
    ) {
        for (int y = startY; y < WORLD_HEIGHT; y++) {
            if (expected.equals(blockKey(chunk.blockAt(blockX, y, blockZ)))) {
                return y;
            }
        }
        throw new AssertionError("Missing " + expected + " at " + blockX + "," + blockZ
                + " from Y " + startY);
    }

    private static void assertRange(
            RealPackProbeSupport.GeneratedChunk chunk,
            int blockX,
            int blockZ,
            int minimumY,
            int maximumY,
            String expected
    ) {
        for (int y = minimumY; y <= maximumY; y++) {
            assertBlock(chunk, blockX, blockZ, y, expected);
        }
    }

    private static void assertBlock(
            RealPackProbeSupport.GeneratedChunk chunk,
            int blockX,
            int blockZ,
            int y,
            String expected
    ) {
        assertEquals("unexpected block at " + blockX + "," + y + "," + blockZ,
                expected, blockKey(chunk.blockAt(blockX, y, blockZ)));
    }

    private static String blockKey(PlatformBlockState state) {
        return state == null || state.isAir() ? AIR : state.key();
    }

    private static File packFixture() throws Exception {
        URL resource = DimensionStackSuperflatProbeTest.class.getClassLoader()
                .getResource("dimension-stack-superflat");
        assertNotNull("dimension stack fixture is missing", resource);
        return new File(resource.toURI());
    }

    private record ChunkCoordinate(int x, int z) {
    }

    private record ColumnCoordinate(int x, int z) {
    }

    private record ColumnLayout(int middleBase, int middleSurface, int topBase, int topSurface) {
        int middleGap() {
            return middleBase - TERRAIN_HEIGHT - 1;
        }

        int topGap() {
            return topBase - middleSurface - 1;
        }
    }

    @FunctionalInterface
    private interface GapSelector {
        int gap(ColumnLayout layout);
    }
}
