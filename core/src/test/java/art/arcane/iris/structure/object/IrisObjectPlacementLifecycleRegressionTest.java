package art.arcane.iris.structure.object;

import art.arcane.iris.generation.terrain.IrisSlopeClip;

import art.arcane.iris.generation.noise.IrisNoiseGenerator;

import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.generation.cave.CarveResult;
import art.arcane.iris.generation.cave.CarvingMode;
import art.arcane.iris.generation.decoration.IrisStiltSettings;
import art.arcane.iris.generation.decoration.IrisVacuumSettings;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.junit.ClassRule;


import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.volmlib.nativelib.terrain.structure.NativeStructureVolume;
import art.arcane.iris.structure.placement.PlacedObject;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisObjectPlacementLifecycleRegressionTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    private static final int ANCHOR_Y = 80;

    private IrisData data;
    private Engine engine;
    private NativeBlockState solid;
    private NativeBlockState schematicAir;

    @Before
    public void bindPlatform() {
        IrisPlatforms.unbind();
        PlatformRegistries registries = mock(PlatformRegistries.class);
        Map<String, NativeBlockState> registryStates = new HashMap<>();
        when(registries.block(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return registryStates.computeIfAbsent(key, value -> state(value.toLowerCase(), !value.toLowerCase().contains("air")));
        });
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);

        engine = mock(Engine.class);
        when(engine.getHeight()).thenReturn(256);
        data = mock(IrisData.class);
        when(data.getEngine()).thenReturn(engine);
        solid = state("minecraft:stone", true);
        schematicAir = state("minecraft:air", false);
    }

    @After
    public void unbindPlatform() {
        IrisPlatforms.unbind();
    }

    @Test
    public void placementFailurePropagatesToTheCaller() {
        RecordingPlacer placer = new RecordingPlacer(null);
        placer.failAfterWrites(1);
        IrisObject object = lineObject(3);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> object.place(0, ANCHOR_Y, 0, placer, placement(), new RNG(2L), data));

        assertTrue(error.getMessage().contains("write failed"));
        assertEquals(1, placer.writes().size());
    }

    @Test
    public void boreUsesRotatedTranslationAndRandomizedAnchor() {
        RecordingPlacer placer = new RecordingPlacer(null);
        IrisObjectPlacement placement = placement();
        placement.setBore(true);
        placement.setRotation(IrisObjectRotation.of(0, 90, 0));
        placement.setTranslate(new IrisObjectTranslate().setX(4).setYRandom(3));

        int resultY = lineObject(3).place(0, ANCHOR_Y, 0, placer, placement, new RNG(2L), data);

        List<BlockWrite> airWrites = placer.writesOf(IrisObject.States.air());
        assertFalse(airWrites.isEmpty());
        assertTrue(airWrites.stream().allMatch(write -> write.y() >= resultY && write.y() <= resultY));
        assertTrue(airWrites.stream().allMatch(write -> write.x() == 0));
        assertTrue(airWrites.stream().allMatch(write -> write.z() <= -3 && write.z() >= -5));
    }

    @Test
    public void collisionCheckUsesTheTransformedBounds() {
        PlacedObject forbidden = new PlacedObject(null, object("forbidden"), 1, 0, 0);
        when(engine.resolveObjectPlacementMarker(anyInt(), anyInt(), org.mockito.ArgumentMatchers.isNull())).thenAnswer(invocation -> {
            int x = invocation.getArgument(0);
            int z = invocation.getArgument(1);
            return x == 0 && z == -4 ? forbidden : null;
        });
        RecordingPlacer placer = new RecordingPlacer(engine);
        IrisObjectPlacement placement = placement();
        placement.setRotation(IrisObjectRotation.of(0, 90, 0));
        placement.setTranslate(new IrisObjectTranslate().setX(4));
        placement.getForbiddenCollisions().add("forbidden");

        int result = lineObject(3).place(0, ANCHOR_Y, 0, placer, placement, new RNG(2L), data);

        assertEquals(-1, result);
        assertTrue(placer.writes().isEmpty());
    }

    @Test
    public void arbitraryRotationSamplesEveryTransformedFootprintColumn() {
        RecordingPlacer placer = new RecordingPlacer(null);
        IrisObjectPlacement placement = placement();
        placement.setMode(ObjectPlaceMode.MAX_HEIGHT);
        placement.setRotation(IrisObjectRotation.of(0, 45, 0));

        boxObject(5, 1, 3).place(0, -1, 0, placer, placement, new RNG(2L), data);

        assertTrue(placer.sampledColumns().contains("-2:-2"));
        assertTrue(placer.sampledColumns().contains("2:2"));
        assertTrue(placer.sampledColumns().size() >= 25);
    }

    @Test
    public void snowUsesOnlyBlocksThatWereWritten() {
        RecordingPlacer placer = new RecordingPlacer(null);
        IrisObject object = new IrisObject(1, 3, 1);
        object.setUnsigned(0, 0, 0, solid);
        object.setUnsigned(0, 2, 0, schematicAir);
        IrisObjectPlacement placement = placement();
        placement.setSnow(0.1);

        object.place(0, ANCHOR_Y, 0, placer, placement, new RNG(2L), data);

        List<BlockWrite> snowWrites = placer.writesOf(IrisObject.States.snowLayer(0));
        assertEquals(1, snowWrites.size());
        assertEquals(ANCHOR_Y, snowWrites.get(0).y());
    }

    @Test
    public void debugPlacementDoesNotMutateSmartBoreCache() {
        IrisObject object = new IrisObject(1, 1, 1);
        object.setUnsigned(0, 0, 0, IrisObject.States.vair());
        object.setSmartBored(true);
        IrisObjectPlacement placement = placement();
        placement.setSmartBore(true);
        RecordingPlacer debug = new RecordingPlacer(null);
        debug.setDebugSmartBore(true);
        RecordingPlacer normal = new RecordingPlacer(null);

        object.place(0, ANCHOR_Y, 0, debug, placement, new RNG(2L), data);
        object.place(0, ANCHOR_Y, 0, normal, placement, new RNG(2L), data);

        NativeBlockState cachedState = object.getBlocks().get(object.getSigned(0, 0, 0));
        assertSame(IrisObject.States.vair(), cachedState);
        if (IrisObject.States.vair() != IrisObject.States.vairDebug()) {
            assertTrue(cachedState != IrisObject.States.vairDebug());
        }
    }

    @Test
    public void placementDataIsRequiredAtTheBoundary() {
        NullPointerException error = assertThrows(NullPointerException.class,
                () -> lineObject(1).place(0, ANCHOR_Y, 0, new RecordingPlacer(null), placement(), new RNG(2L), null));

        assertEquals("Object placement data is required.", error.getMessage());
    }

    private IrisObjectPlacement placement() {
        IrisObjectPlacement placement = new IrisObjectPlacement();
        placement.setMode(ObjectPlaceMode.CENTER_HEIGHT);
        placement.setRequireSurfaceSupport(false);
        return placement;
    }

    @Test
    public void stiltWaitsForTheWholeTransformedFootprint() {
        RecordingPlacer placer = new RecordingPlacer(null);
        IrisObjectPlacement config = placement();
        config.setMode(ObjectPlaceMode.STILT);
        config.setRotation(IrisObjectRotation.of(0, 90, 0));
        config.setTranslate(new IrisObjectTranslate().setX(4));

        lineObject(3).place(0, ANCHOR_Y + 4, 0, placer, config, new RNG(2L), data);

        // Every bottom column reaches the ground after rotation and translation.
        for (int z = -5; z <= -3; z++) {
            assertSame(solid, placer.get(0, ANCHOR_Y, z));
            assertSame(solid, placer.get(0, ANCHOR_Y + 3, z));
        }
        assertTrue(placer.writes().stream().allMatch(write -> write.x() == 0));
    }

    @Test
    public void ceilingFlipsBeforeTranslationAndFillsUpward() {
        RecordingPlacer placer = new RecordingPlacer(null);
        placer.world.put("0:90:0", solid);
        IrisObjectPlacement config = placement();
        config.setMode(ObjectPlaceMode.CEILING_HANG);
        config.setTranslate(new IrisObjectTranslate().setY(3));
        config.setStiltSettings(new IrisStiltSettings().setOrganicJitter(0).setOrganicScratch(0));

        boxObject(1, 3, 1).place(0, ANCHOR_Y, 0, placer, config, new RNG(2L), data);

        assertSame(solid, placer.get(0, 82, 0));
        assertSame(solid, placer.get(0, 89, 0));
        assertTrue(placer.writes().stream().noneMatch(write -> write.y() < 82 || write.y() >= 90));
    }

    @Test
    public void organicStiltStopsAtTheFloor() {
        RecordingPlacer placer = new RecordingPlacer(null);
        placer.world.put("0:75:0", solid);
        IrisObjectPlacement config = placement();
        config.setMode(ObjectPlaceMode.ORGANIC_STILT);
        config.setStiltSettings(new IrisStiltSettings().setOrganicJitter(0).setOrganicScratch(0));

        lineObject(1).place(0, ANCHOR_Y, 0, placer, config, new RNG(2L), data);

        for (int y = 76; y <= ANCHOR_Y; y++) {
            assertSame(solid, placer.get(0, y, 0));
        }
        assertTrue(placer.writes().stream().noneMatch(write -> write.y() <= 75));
    }

    @Test
    public void erodeStiltResolvesItsOwnSampledAnchor() {
        RecordingPlacer placer = new RecordingPlacer(null);
        IrisObjectPlacement config = placement();
        config.setMode(ObjectPlaceMode.ERODE_STILT);
        assertEquals(ANCHOR_Y, lineObject(1).place(0, -1, 0, placer, config, new RNG(2L), data));
        assertFalse(placer.writes().isEmpty());
    }

    @Test
    public void explicitMinimumHeightAndFloatingDoNotNeedTerrainSampling() {
        for (ObjectPlaceMode mode : List.of(ObjectPlaceMode.MIN_HEIGHT, ObjectPlaceMode.FAST_MIN_HEIGHT,
                ObjectPlaceMode.FLOATING, ObjectPlaceMode.STRUCTURE_PIECE)) {
            RecordingPlacer placer = new RecordingPlacer(null);
            IrisObjectPlacement config = placement();
            config.setMode(mode);
            assertEquals(ANCHOR_Y, lineObject(1).place(0, ANCHOR_Y, 0, placer, config, new RNG(2L), data));
            assertTrue(mode.toString(), placer.sampledColumns().isEmpty());
        }
    }

    @Test
    public void customPlanCompletesTheBlockPassBeforeAfterBlocks() {
        RecordingPlacer placer = new RecordingPlacer(engine);
        PlacementLifecycleBuilder builder = new PlacementLifecycleBuilder();
        ObjectPlaceMode.CENTER_HEIGHT.install(builder);
        builder.add(new PlacementLifecycleDependency("test.offset"), PlacementStage.HEIGHT,
                (context, state, ignored) -> state.y += 5, PlacementModuleAnchor.HEIGHT);
        List<Integer> finishedSizes = new ArrayList<>();
        builder.add(new PlacementLifecycleDependency("test.finished"), PlacementStage.AFTER_BLOCKS,
                (context, state, ignored) -> finishedSizes.add(placer.writes().size()));
        PlacementContext context = new PlacementContext(placement(), data, placer, null, new RNG(2L), lineObject(3),
                0, 0, ANCHOR_Y, null);

        assertEquals(ANCHOR_Y + 5, builder.build().execute(context).heightOrRejected());
        assertEquals(List.of(3), finishedSizes);
        assertTrue(placer.writes().stream().allMatch(write -> write.y() == ANCHOR_Y + 5));
    }

    @Test
    public void fromBottomKeepsItsAnchorWithoutSamplingTerrain() {
        RecordingPlacer placer = new RecordingPlacer(null);
        IrisObjectPlacement config = placement();
        config.setFromBottom(true);
        assertEquals(2, lineObject(1).place(0, -1, 0, placer, config, new RNG(2L), data));
        assertTrue(placer.sampledColumns().isEmpty());
    }

    @Test
    public void eligibilityGuardsRejectBeforeBoreAndOwnTheirForcePlaceExemptions() {
        for (String reason : List.of("carving-anchor", "underwater-anchor", "cave-clearance", "fluid-height", "height-clamp")) {
            RecordingPlacer placer = new RecordingPlacer(null);
            IrisObjectPlacement config = placement().setBore(true).setSnow(0.1);
            CarveResult cave = null;
            int anchor = ANCHOR_Y;
            switch (reason) {
                case "carving-anchor" -> {
                    placer.carved = true;
                    config.setCarvingSupport(CarvingMode.SURFACE_ONLY);
                }
                case "underwater-anchor" -> {
                    placer.underwater = true;
                    anchor = -1;
                }
                case "cave-clearance" -> cave = new CarveResult(ANCHOR_Y, ANCHOR_Y + 1);
                case "fluid-height" -> {
                    config.setUnderwater(true);
                    placer.fluidHeight = ANCHOR_Y;
                }
                case "height-clamp" -> config.getClamp().setMaximumHeight(ANCHOR_Y - 1);
            }
            PlacementState result = execute(config, anchor, placer, cave);
            assertEquals(reason, result.getRejection());
            assertEquals(-1, result.heightOrRejected());
            assertTrue(reason, placer.writes().isEmpty());

            config.setForcePlace(true);
            assertFalse(reason, execute(config, anchor, placer, cave).isRejected());
            assertFalse(reason, placer.writes().isEmpty());
        }
    }

    @Test
    public void forcePlaceCannotBypassNativeProtectionButStructurePiecesCan() {
        when(engine.getNativeStructureVolumes(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(
                new KList<>(NativeStructureVolume.of("minecraft:village", 0, ANCHOR_Y, 0, 0, ANCHOR_Y, 0)));
        RecordingPlacer placer = new RecordingPlacer(engine);
        IrisObjectPlacement config = placement().setForcePlace(true).setBore(true).setSnow(0.1);

        assertEquals("native-structure", execute(config, ANCHOR_Y, placer, null).getRejection());
        assertTrue(placer.writes().isEmpty());

        config.setMode(ObjectPlaceMode.STRUCTURE_PIECE);
        assertEquals(ANCHOR_Y, execute(config, ANCHOR_Y, placer, null).heightOrRejected());
        assertFalse(placer.writes().isEmpty());
    }

    @Test
    public void collisionRejectionPrecedesBoreAndAllowedObjectsOverrideForbiddenOnes() {
        PlacedObject forbidden = new PlacedObject(null, object("forbidden"), 1, 0, 0);
        when(engine.resolveObjectPlacementMarker(anyInt(), anyInt(), org.mockito.ArgumentMatchers.isNull())).thenReturn(forbidden);
        RecordingPlacer placer = new RecordingPlacer(engine);
        IrisObjectPlacement config = placement().setBore(true);
        config.getForbiddenCollisions().add("forbidden");

        assertEquals("object-collision", execute(config, ANCHOR_Y, placer, null).getRejection());
        assertTrue(placer.writes().isEmpty());

        config.getAllowedCollisions().add("forbidden");
        assertFalse(execute(config, ANCHOR_Y, placer, null).isRejected());
        assertFalse(placer.writes().isEmpty());

        config.getAllowedCollisions().clear();
        config.setForcePlace(true);
        assertFalse(execute(config, ANCHOR_Y, new RecordingPlacer(engine), null).isRejected());
    }

    @Test
    public void missingCollisionEngineFailsBeforeWrites() {
        when(data.getEngine()).thenReturn(null);
        RecordingPlacer placer = new RecordingPlacer(null);
        IrisObjectPlacement config = placement().setBore(true);
        config.getForbiddenCollisions().add("forbidden");
        assertEquals("Object placement requires active Iris data for collision settings.",
                assertThrows(IllegalStateException.class,
                        () -> execute(config, ANCHOR_Y, placer, null)).getMessage());
        assertTrue(placer.writes().isEmpty());
    }

    @Test
    public void vacuumAndSnowObserveOnlyWrittenBlocksAndFinishInOrder() {
        RecordingPlacer placer = new RecordingPlacer(engine);
        IrisObject object = new IrisObject(1, 3, 1);
        object.setUnsigned(0, 0, 0, solid);
        object.setUnsigned(0, 2, 0, schematicAir);
        IrisObjectPlacement config = placement().setMode(ObjectPlaceMode.VACUUM).setSnow(0.1);
        config.setTranslate(new IrisObjectTranslate().setY(3));
        config.setVacuumSettings(new IrisVacuumSettings().setRadius(1));

        object.place(0, ANCHOR_Y, 0, placer, config, new RNG(2L), data);

        List<BlockWrite> center = placer.writes().stream().filter(write -> write.x() == 0 && write.z() == 0).toList();
        assertEquals(List.of(82, 81, 83), center.stream().map(BlockWrite::y).toList());
        assertSame(solid, center.get(0).state());
        assertSame(IrisObject.States.snowLayer(0), center.get(2).state());
    }

    @Test
    public void preparationPreservesRandomDrawsForPlacementAndLaterCallers() {
        for (int range : List.of(-3, 0, 3)) {
            RNG rng = new RNG(91);
            RNG expected = new RNG(91);
            expected.imax();
            expected.imax();
            expected.imax();
            int offset = range > 0 ? expected.i(0, range) : range < 0 ? expected.i(range, 0) : 0;
            IrisObjectPlacement config = placement().setBore(true);
            config.setTranslate(new IrisObjectTranslate().setYRandom(range));
            assertEquals(ANCHOR_Y + offset,
                    lineObject(1).place(0, ANCHOR_Y, 0, new RecordingPlacer(null), config, rng, data));
            assertEquals(expected.nextLong(), rng.nextLong());
        }
    }

    @Test
    public void heightmapPreparationKeepsItsSeedDrawBeforeRotation() {
        RNG rng = new RNG(91);
        RNG expected = new RNG(91);
        expected.nextLong();
        expected.imax();
        expected.imax();
        expected.imax();
        RecordingPlacer placer = new RecordingPlacer(null);
        IrisObjectPlacement config = placement();
        config.setHeightmap(new IrisNoiseGenerator(false).setOffsetY(93));

        assertEquals(93, lineObject(1).place(0, -1, 0, placer, config, rng, data));
        assertTrue(placer.sampledColumns().isEmpty());
        assertEquals(expected.nextLong(), rng.nextLong());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void slopeRejectionConsumesNoTransformRandomness() {
        IrisComplex complex = mock(IrisComplex.class);
        ProceduralStream<Double> slopes = mock(ProceduralStream.class);
        when(engine.getComplex()).thenReturn(complex);
        when(complex.getSlopeStream()).thenReturn(slopes);
        when(slopes.get(anyDouble(), anyDouble())).thenReturn(0.5);
        IrisObjectPlacement config = placement().setBore(true);
        config.setSlopeCondition(new IrisSlopeClip(1, 2));
        RNG rng = new RNG(91);
        RecordingPlacer placer = new RecordingPlacer(engine);

        assertEquals(-1, lineObject(1).place(0, ANCHOR_Y, 0, placer, config, rng, data));
        assertEquals(new RNG(91).nextLong(), rng.nextLong());
        assertTrue(placer.writes().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void slopeRotationUsesAPerPlacementCopy() {
        IrisComplex complex = mock(IrisComplex.class);
        ProceduralStream<Double> heights = mock(ProceduralStream.class);
        when(engine.getComplex()).thenReturn(complex);
        when(complex.getHeightStream()).thenReturn(heights);
        when(heights.get(anyDouble(), anyDouble())).thenAnswer(call -> call.<Double>getArgument(0) > 0 ? 70D : 80D);
        IrisObjectPlacement config = placement().setRotateTowardsSlope(true);
        IrisObjectRotation original = config.getRotation();
        RecordingPlacer placer = new RecordingPlacer(engine);

        lineObject(3).place(0, ANCHOR_Y, 0, placer, config, new RNG(2L), data);

        assertSame(original, config.getRotation());
        assertEquals(0, original.getYAxis().getMin(), 0);
        assertEquals(3, placer.writes().size());
        assertTrue(placer.writes().stream().allMatch(write -> write.x() == 0));
        assertEquals(List.of(-1, 0, 1), placer.writes().stream().map(BlockWrite::z).sorted().toList());
    }

    private PlacementState execute(IrisObjectPlacement config, int y, RecordingPlacer placer, CarveResult cave) {
        return PlacementLifecycles.defaults().get(config.getMode()).execute(
                new PlacementContext(config, data, placer, cave, new RNG(2L), lineObject(1), 0, 0, y, null));
    }

    private IrisObject lineObject(int width) {
        IrisObject object = new IrisObject(width, 1, 1);
        for (int x = 0; x < width; x++) {
            object.setUnsigned(x, 0, 0, solid);
        }
        return object;
    }

    private IrisObject boxObject(int width, int height, int depth) {
        IrisObject object = new IrisObject(width, height, depth);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < depth; z++) {
                    object.setUnsigned(x, y, z, solid);
                }
            }
        }
        return object;
    }

    private IrisObject object(String key) {
        IrisObject object = lineObject(1);
        object.setLoadKey(key);
        return object;
    }

    private static NativeBlockState state(String key, boolean solid) {
        NativeBlockState state = mock(NativeBlockState.class);
        when(state.key()).thenReturn(key);
        when(state.materialKey()).thenReturn(key);
        when(state.isSolid()).thenReturn(solid);
        when(state.isOccluding()).thenReturn(solid);
        return state;
    }

    private static final class RecordingPlacer implements IObjectPlacer {
        private final List<BlockWrite> writes = new ArrayList<>();
        private final Map<String, NativeBlockState> world = new HashMap<>();
        private final List<String> sampledColumns = new ArrayList<>();
        private final Engine engine;
        private int failAfterWrites = Integer.MAX_VALUE;
        private boolean debugSmartBore;
        private boolean carved;
        private boolean underwater;
        private int fluidHeight;

        private RecordingPlacer(Engine engine) {
            this.engine = engine;
        }

        private void failAfterWrites(int writes) {
            failAfterWrites = writes;
        }

        private void setDebugSmartBore(boolean debugSmartBore) {
            this.debugSmartBore = debugSmartBore;
        }

        private List<BlockWrite> writes() {
            return writes;
        }

        private List<BlockWrite> writesOf(NativeBlockState state) {
            return writes.stream().filter(write -> write.state() == state).toList();
        }

        private List<String> sampledColumns() {
            return sampledColumns;
        }

        @Override
        public int getHighest(int x, int z, IrisData data) {
            sampledColumns.add(x + ":" + z);
            return ANCHOR_Y;
        }

        @Override
        public int getHighest(int x, int z, IrisData data, boolean ignoreFluid) {
            sampledColumns.add(x + ":" + z);
            return ANCHOR_Y;
        }

        @Override
        public void set(int x, int y, int z, NativeBlockState state) {
            if (writes.size() >= failAfterWrites) {
                throw new IllegalStateException("write failed");
            }
            writes.add(new BlockWrite(x, y, z, state));
            world.put(x + ":" + y + ":" + z, state);
        }

        @Override
        public NativeBlockState get(int x, int y, int z) {
            return world.get(x + ":" + y + ":" + z);
        }

        @Override
        public boolean isPreventingDecay() {
            return false;
        }

        @Override
        public boolean isCarved(int x, int y, int z) {
            return carved;
        }

        @Override
        public boolean isSolid(int x, int y, int z) {
            NativeBlockState state = get(x, y, z);
            return state != null && state.isSolid();
        }

        @Override
        public boolean isUnderwater(int x, int z) {
            return underwater;
        }

        @Override
        public int getFluidHeight() {
            return fluidHeight;
        }

        @Override
        public boolean isDebugSmartBore() {
            return debugSmartBore;
        }

        @Override
        public void setTile(int x, int y, int z, TileData tile) {
        }

        @Override
        public <T> void setData(int x, int y, int z, T data) {
        }

        @Override
        public <T> T getData(int x, int y, int z, Class<T> type) {
            return null;
        }

        @Override
        public Engine getEngine() {
            return engine;
        }
    }

    private record BlockWrite(int x, int y, int z, NativeBlockState state) {
    }
}
