package art.arcane.iris.engine.object.placement;

import art.arcane.iris.engine.object.noise.IrisNoiseGenerator;
import art.arcane.iris.engine.object.placement.IObjectPlacer;
import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.iris.engine.object.placement.IrisSlopeClip;
import art.arcane.iris.engine.object.placement.IrisStiltSettings;
import art.arcane.iris.engine.object.placement.IrisVacuumSettings;
import art.arcane.iris.engine.object.placement.ObjectPlaceMode;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.prefab.IrisObjectRotation;
import art.arcane.iris.engine.object.prefab.IrisObjectTranslate;
import art.arcane.iris.engine.object.prefab.TileData;
import art.arcane.iris.engine.object.terrain.carving.CarveResult;
import art.arcane.iris.engine.object.terrain.carving.CarvingMode;
import art.arcane.iris.builtin.blocks.BlocksStages;
import art.arcane.iris.builtin.bore.BoreStages;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.BuiltInPlacementPack;
import art.arcane.iris.core.modules.ModuleObjectPlacement;
import art.arcane.iris.core.modules.ObjectPlacementResult;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.NativeStructureVolume;
import art.arcane.iris.engine.framework.PlacedObject;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.util.project.stream.ProceduralStream;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.block.BlockState;
import com.volmit.host.LoadedPack;
import com.volmit.placement.PlacedBlock;
import com.volmit.stage.PlacementStage;
import com.volmit.stage.Stage;
import com.volmit.stage.StageGraph;
import com.volmit.world.BlockPosition;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

public class IrisObjectPlacementRegressionTest {
    private static final int ANCHOR_Y = 80;
    private static final String SNOW_LAYER = "minecraft:snow[layers=1]";
    private static final String CAVE_AIR = "minecraft:cave_air";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final Map<String, PlatformBlockState> registryStates = new HashMap<>();
    private IrisData data;
    private Engine engine;
    private PlatformBlockState solid;
    private PlatformBlockState schematicAir;
    private LoadedPack pack;

    @Before
    public void bindPlatform() throws IOException {
        IrisPlatforms.unbind();
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.block(anyString())).thenAnswer(invocation -> registryState(invocation.getArgument(0)));
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);

        engine = mock(Engine.class);
        when(engine.getHeight()).thenReturn(256);
        BuiltInPlacementPack.seedWorld(engine, 7L);
        data = mock(IrisData.class);
        when(data.getEngine()).thenReturn(engine);
        usePack(List.of());
        solid = registryState("minecraft:stone");
        schematicAir = registryState("minecraft:air");
    }

    @After
    public void unbindPlatform() {
        pack.close();
        IrisPlatforms.unbind();
    }

    /** Placements run Iris's built-in modules followed by {@code packModules}, as a pack's placement graph does. */
    private void usePack(List<IrisModule> packModules) throws IOException {
        if (pack != null) {
            pack.close();
        }
        pack = BuiltInPlacementPack.serve(data, folder, packModules);
    }

    /** Platform states by key, the way a registry answers them; keys without a namespace are Minecraft's. */
    private PlatformBlockState registryState(String key) {
        String normalized = key.toLowerCase();
        String namespaced = normalized.contains(":") ? normalized : "minecraft:" + normalized;
        return registryStates.computeIfAbsent(namespaced, value -> state(value, !value.contains("air")));
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
        placement.setBoreExtendMaxY(1);
        placement.setRotation(IrisObjectRotation.of(0, 90, 0));
        placement.setTranslate(new IrisObjectTranslate().setX(4).setYRandom(3));

        int resultY = lineObject(3).place(0, ANCHOR_Y, 0, placer, placement, new RNG(2L), data);

        // The object's own blocks replace the bored air below, so only the extension stays air.
        List<BlockWrite> airWrites = placer.writesOf(CAVE_AIR);
        assertEquals(3, airWrites.size());
        assertTrue(airWrites.stream().allMatch(write -> write.y() == resultY + 1));
        assertTrue(airWrites.stream().allMatch(write -> write.x() == 0));
        assertTrue(airWrites.stream().allMatch(write -> write.z() <= -3 && write.z() >= -5));
    }

    @Test
    public void collisionCheckUsesTheTransformedBounds() {
        PlacedObject forbidden = new PlacedObject(null, object("forbidden"), 1, 0, 0);
        when(engine.getObjectPlacement(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
            int x = invocation.getArgument(0);
            int y = invocation.getArgument(1);
            int z = invocation.getArgument(2);
            return x == 0 && y == ANCHOR_Y && z == -4 ? forbidden : null;
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

        List<BlockWrite> snowWrites = placer.writesOf(SNOW_LAYER);
        assertEquals(1, snowWrites.size());
        assertEquals(ANCHOR_Y, snowWrites.get(0).y());
    }

    @Test
    public void debugPlacementDoesNotMutateSmartBoreCache() {
        IrisObject object = new IrisObject(1, 1, 1);
        object.setUnsigned(0, 0, 0, IrisObject.States.VAIR);
        object.setSmartBored(true);
        IrisObjectPlacement placement = placement();
        placement.setSmartBore(true);
        RecordingPlacer debug = new RecordingPlacer(null);
        debug.setDebugSmartBore(true);
        RecordingPlacer normal = new RecordingPlacer(null);

        object.place(0, ANCHOR_Y, 0, debug, placement, new RNG(2L), data);
        object.place(0, ANCHOR_Y, 0, normal, placement, new RNG(2L), data);

        PlatformBlockState cachedState = object.getBlocks().get(object.getSigned(0, 0, 0));
        assertSame(IrisObject.States.VAIR, cachedState);
        assertEquals(BlockState.parse(IrisObject.States.VAIR.key()), object.getGeometry().blocks().getFirst().state());
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
            assertEquals(solid.key(), placer.get(0, ANCHOR_Y, z).key());
            assertEquals(solid.key(), placer.get(0, ANCHOR_Y + 3, z).key());
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

        assertEquals(solid.key(), placer.get(0, 82, 0).key());
        assertEquals(solid.key(), placer.get(0, 89, 0).key());
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
            assertEquals(solid.key(), placer.get(0, y, 0).key());
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
    public void packStagesSeeTheWrittenObjectAndCommitWithIt() throws IOException {
        List<Integer> writtenSizes = new ArrayList<>();
        usePack(List.of(new CapModule(writtenSizes)));
        RecordingPlacer placer = new RecordingPlacer(engine);

        assertEquals(ANCHOR_Y, lineObject(3).place(0, ANCHOR_Y, 0, placer, placement(), new RNG(2L), data));

        assertEquals(List.of(3), writtenSizes);
        assertEquals(4, placer.writes().size());
        assertEquals(solid.key(), placer.get(0, ANCHOR_Y + 1, 0).key());
    }

    @Test
    public void aRejectingPackStageDiscardsEveryEarlierWrite() throws IOException {
        usePack(List.of(new VetoModule()));
        RecordingPlacer placer = new RecordingPlacer(engine);

        ObjectPlacementResult result = place(placement().setBore(true), ANCHOR_Y, placer, null);

        assertEquals(Optional.of("veto"), result.rejection());
        assertEquals(-1, result.heightOrRejected());
        assertTrue(placer.writes().isEmpty());
    }

    @Test
    public void withoutAnEngineThePlacementDrawsItsSeedOnceFromTheCallersRandom() {
        RNG rng = new RNG(91);
        RNG expected = new RNG(91);
        expected.nextLong();
        IrisObjectPlacement config = placement();
        config.setTranslate(new IrisObjectTranslate().setYRandom(3));

        lineObject(1).place(0, ANCHOR_Y, 0, new RecordingPlacer(null), config, rng, data);

        assertEquals(expected.nextLong(), rng.nextLong());
    }

    @Test
    public void withAnEngineTheSamePlacementPlacesTheSameWayWithoutTheCallersRandom() {
        IrisObjectPlacement config = placement();
        config.setTranslate(new IrisObjectTranslate().setYRandom(20));
        RNG rng = new RNG(91);

        int first = lineObject(1).place(5, ANCHOR_Y, 5, new RecordingPlacer(engine), config, rng, data);
        int second = lineObject(1).place(5, ANCHOR_Y, 5, new RecordingPlacer(engine), config, new RNG(3), data);

        assertEquals(first, second);
        assertEquals(new RNG(91).nextLong(), rng.nextLong());
    }

    /** Puts a block on top of the object once it is written, reporting how many blocks the object wrote. */
    private static final class CapModule implements IrisModule {
        private static final PlacementStage<Void> CAP = Stage.placement("test:cap/top");
        private final List<Integer> writtenSizes;

        private CapModule(List<Integer> writtenSizes) {
            this.writtenSizes = writtenSizes;
        }

        @Override
        public ModuleInfo info() {
            return ModuleInfo.of("test:cap", "Cap", "1");
        }

        @Override
        public void install(StageGraph graph) {
            graph.define(CAP).requires(BlocksStages.COMPLETE, BlocksStages.WRITE).run(context -> {
                List<PlacedBlock> written = context.output(BlocksStages.WRITE).blocks();
                writtenSizes.add(written.size());
                BlockPosition first = written.getFirst().position();
                context.access().set(new PlacedBlock(new BlockPosition(0, first.y() + 1, 0), written.getFirst().state(), Optional.empty()));
            });
        }
    }

    /** Rejects every placement after it is bored and written. */
    private static final class VetoModule implements IrisModule {
        private static final PlacementStage<Void> VETO = Stage.placement("test:veto/all");

        @Override
        public ModuleInfo info() {
            return ModuleInfo.of("test:veto", "Veto", "1");
        }

        @Override
        public void install(StageGraph graph) {
            graph.define(VETO).requires(BoreStages.CLEAR, BlocksStages.COMPLETE).run(context -> context.reject("veto"));
        }
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
            ObjectPlacementResult result = place(config, anchor, placer, cave);
            assertEquals(Optional.of(reason), result.rejection());
            assertEquals(-1, result.heightOrRejected());
            assertTrue(reason, placer.writes().isEmpty());

            config.setForcePlace(true);
            assertTrue(reason, place(config, anchor, placer, cave).isPlaced());
            assertFalse(reason, placer.writes().isEmpty());
        }
    }

    @Test
    public void forcePlaceCannotBypassNativeProtectionButStructurePiecesCan() {
        when(engine.getNativeStructureVolumes(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(
                new KList<>(NativeStructureVolume.of("minecraft:village", 0, ANCHOR_Y, 0, 0, ANCHOR_Y, 0)));
        RecordingPlacer placer = new RecordingPlacer(engine);
        IrisObjectPlacement config = placement().setForcePlace(true).setBore(true).setSnow(0.1);

        assertEquals(Optional.of("native-structure"), place(config, ANCHOR_Y, placer, null).rejection());
        assertTrue(placer.writes().isEmpty());

        config.setMode(ObjectPlaceMode.STRUCTURE_PIECE);
        assertEquals(ANCHOR_Y, place(config, ANCHOR_Y, placer, null).heightOrRejected());
        assertFalse(placer.writes().isEmpty());
    }

    @Test
    public void collisionRejectionPrecedesBoreAndAllowedObjectsOverrideForbiddenOnes() {
        PlacedObject forbidden = new PlacedObject(null, object("forbidden"), 1, 0, 0);
        when(engine.getObjectPlacement(anyInt(), anyInt(), anyInt())).thenReturn(forbidden);
        RecordingPlacer placer = new RecordingPlacer(engine);
        IrisObjectPlacement config = placement().setBore(true);
        config.getForbiddenCollisions().add("forbidden");

        assertEquals(Optional.of("object-collision"), place(config, ANCHOR_Y, placer, null).rejection());
        assertTrue(placer.writes().isEmpty());

        config.getAllowedCollisions().add("forbidden");
        assertTrue(place(config, ANCHOR_Y, placer, null).isPlaced());
        assertFalse(placer.writes().isEmpty());

        config.getAllowedCollisions().clear();
        config.setForcePlace(true);
        assertTrue(place(config, ANCHOR_Y, new RecordingPlacer(engine), null).isPlaced());
    }

    @Test
    public void missingCollisionEngineFailsBeforeWrites() {
        when(data.getEngine()).thenReturn(null);
        RecordingPlacer placer = new RecordingPlacer(null);
        IrisObjectPlacement config = placement().setBore(true);
        config.getForbiddenCollisions().add("forbidden");
        assertEquals("Object placement requires active Iris data for collision settings.",
                assertThrows(IllegalStateException.class,
                        () -> place(config, ANCHOR_Y, placer, null)).getMessage());
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
        assertEquals(solid.key(), center.get(0).state().key());
        assertEquals(SNOW_LAYER, center.get(2).state().key());
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

    private ObjectPlacementResult place(IrisObjectPlacement config, int y, RecordingPlacer placer, CarveResult cave) {
        return ModuleObjectPlacement.place(lineObject(1), 0, y, 0, placer, config, new RNG(2L), null, cave, data);
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

    private static PlatformBlockState state(String key, boolean solid) {
        PlatformBlockState state = mock(PlatformBlockState.class);
        when(state.key()).thenReturn(key);
        when(state.materialKey()).thenReturn(key);
        when(state.isSolid()).thenReturn(solid);
        when(state.isOccluding()).thenReturn(solid);
        return state;
    }

    private static final class RecordingPlacer implements IObjectPlacer {
        private final List<BlockWrite> writes = new ArrayList<>();
        private final Map<String, PlatformBlockState> world = new HashMap<>();
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

        private List<BlockWrite> writesOf(String key) {
            return writes.stream().filter(write -> write.state().key().equals(key)).toList();
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
        public void set(int x, int y, int z, PlatformBlockState state) {
            if (writes.size() >= failAfterWrites) {
                throw new IllegalStateException("write failed");
            }
            writes.add(new BlockWrite(x, y, z, state));
            world.put(x + ":" + y + ":" + z, state);
        }

        @Override
        public PlatformBlockState get(int x, int y, int z) {
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
            PlatformBlockState state = get(x, y, z);
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

    private record BlockWrite(int x, int y, int z, PlatformBlockState state) {
    }
}
