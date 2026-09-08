package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.engine.UpperDimensionContext;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.engine.mantle.MantleComponent;
import art.arcane.iris.engine.mantle.MantlePass;
import art.arcane.iris.engine.mantle.MantleWriter;
import art.arcane.iris.engine.mantle.MatterGenerator;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisGeneratorStyle;
import art.arcane.iris.engine.object.IrisObject;
import art.arcane.iris.engine.object.IrisObjectPlacement;
import art.arcane.iris.engine.object.IrisObjectRotation;
import art.arcane.iris.engine.object.IrisObjectScale;
import art.arcane.iris.engine.object.IrisObjectTranslate;
import art.arcane.iris.engine.object.IrisProceduralObjects;
import art.arcane.iris.engine.object.IrisProceduralPlacement;
import art.arcane.iris.engine.object.IrisRegion;
import art.arcane.iris.engine.object.IrisVacuumSettings;
import art.arcane.iris.engine.object.NoiseStyle;
import art.arcane.iris.engine.object.ObjectPlaceMode;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.iris.util.common.math.IrisBlockVector;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

public class MantleObjectComponentBoundaryRadiusTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void bindPlatform() {
        IrisPlatforms.unbind();
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.block(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            PlatformBlockState block = mock(PlatformBlockState.class);
            when(block.key()).thenReturn(key);
            when(block.materialKey()).thenReturn(key);
            when(block.isSolid()).thenReturn(!key.toLowerCase().contains("air"));
            when(block.isOccluding()).thenReturn(!key.toLowerCase().contains("air"));
            return block;
        });
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);
    }

    @After
    public void unbindPlatform() {
        IrisPlatforms.unbind();
    }

    @Test
    public void verticalRotationAndTranslationExpandHorizontalReach() {
        IrisObjectPlacement placement = new IrisObjectPlacement()
                .setRotation(IrisObjectRotation.of(90, 0, 0))
                .setTranslate(new IrisObjectTranslate().setX(3).setY(4));

        assertEquals(53, MantleObjectComponent.calculatePlacementReach(new IrisBlockVector(5, 48, 7), placement));
    }

    @Test
    public void scaleWarpAndVacuumExpandPlacementReach() {
        IrisObjectPlacement placement = new IrisObjectPlacement()
                .setScale(new IrisObjectScale().setSize(2D))
                .setWarp(new IrisGeneratorStyle(NoiseStyle.SIMPLEX).setMultiplier(10D))
                .setMode(ObjectPlaceMode.VACUUM)
                .setVacuumSettings(new IrisVacuumSettings().setRadius(12));

        assertEquals(43, MantleObjectComponent.calculatePlacementReach(new IrisBlockVector(5, 4, 3), placement));
    }

    @Test
    public void knownObjectFootprintMustClearTheTransitionBand() {
        IrisComplex complex = mock(IrisComplex.class);
        IrisObject object = new IrisObject(5, 4, 3);
        IrisObjectPlacement placement = new IrisObjectPlacement();
        when(complex.allowsNewGenerationFootprint(95, 195, 105, 205)).thenReturn(false);

        assertFalse(MantleObjectComponent.allowsObjectPlacement(
                complex,
                object,
                placement,
                100,
                200));
        verify(complex).allowsNewGenerationFootprint(95, 195, 105, 205);
    }

    @Test
    public void sourceReplayUsesAscendingSignedCoordinates() {
        List<String> replayedSources = new ArrayList<>();

        MantleObjectComponent.replaySourceChunks(
                -2,
                -3,
                16,
                (chunkX, chunkZ) -> replayedSources.add(chunkX + "," + chunkZ)
        );

        assertEquals(List.of(
                "-3,-4", "-3,-3", "-3,-2",
                "-2,-4", "-2,-3", "-2,-2",
                "-1,-4", "-1,-3", "-1,-2"
        ), replayedSources);
    }

    @Test
    public void sourcePredecessorReplayUsesTheStableAnchoredPrefix() {
        List<String> replayedSources = new ArrayList<>();

        MantleObjectComponent.replaySourcePredecessors(
                -2,
                -3,
                16,
                (chunkX, chunkZ) -> replayedSources.add(chunkX + "," + chunkZ)
        );

        assertEquals(List.of(
                "-3,-4", "-3,-3", "-3,-2",
                "-2,-4"
        ), replayedSources);
    }

    @Test
    public void collisionInputRadiusCoversBothSourceTraversalLegs() {
        assertEquals(82, MantleObjectComponent.calculateInputRadius(33, false));
        assertEquals(130, MantleObjectComponent.calculateInputRadius(33, true));
        assertEquals(Integer.MAX_VALUE,
                MantleObjectComponent.calculateInputRadius(Integer.MAX_VALUE, true));
    }

    @Test
    public void proceduralTreeTransformsContributeToRadius() {
        IrisObjectPlacement placement = new IrisObjectPlacement()
                .setRotation(IrisObjectRotation.of(90, 0, 0))
                .setTranslate(new IrisObjectTranslate().setX(3).setY(4));
        IrisData data = mock(IrisData.class);
        IrisProceduralPlacement proceduralPlacement = mock(IrisProceduralPlacement.class);
        when(proceduralPlacement.getVariantObjects(data)).thenReturn(new KList<>(new IrisObject(5, 48, 7)));
        when(proceduralPlacement.asPlacement()).thenReturn(placement);
        IrisProceduralObjects proceduralObjects = mock(IrisProceduralObjects.class);
        when(proceduralObjects.isEmpty()).thenReturn(false);
        when(proceduralObjects.getAllPlacements()).thenReturn(new KList<>(proceduralPlacement));
        IrisRegion region = mock(IrisRegion.class);
        when(region.getObjects()).thenReturn(new KList<>());
        when(region.getProceduralObjects()).thenReturn(proceduralObjects);
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.getAllRegions(any())).thenReturn(new KList<>(region));
        when(dimension.getReachableBiomes(any())).thenReturn(new KList<>());
        Engine engine = mock(Engine.class);
        when(engine.getDimension()).thenReturn(dimension);
        EngineMantle engineMantle = mock(EngineMantle.class);
        when(engineMantle.getEngine()).thenReturn(engine);
        when(engineMantle.getData()).thenReturn(data);

        assertEquals(53, new MantleObjectComponent(engineMantle).getRadius());
    }

    @Test
    public void upperDimensionObjectsDriveRadiusAndCollisionMode() {
        IrisObjectPlacement placement = new IrisObjectPlacement()
                .setRotation(IrisObjectRotation.of(90, 0, 0))
                .setTranslate(new IrisObjectTranslate().setX(3).setY(4));
        placement.getForbiddenCollisions().add("upper/blocker");
        IrisData baseData = mock(IrisData.class);
        IrisData upperData = mock(IrisData.class);
        IrisProceduralPlacement proceduralPlacement = mock(IrisProceduralPlacement.class);
        when(proceduralPlacement.getVariantObjects(upperData))
                .thenReturn(new KList<>(new IrisObject(5, 48, 7)));
        when(proceduralPlacement.asPlacement()).thenReturn(placement);
        IrisProceduralObjects proceduralObjects = mock(IrisProceduralObjects.class);
        when(proceduralObjects.isEmpty()).thenReturn(false);
        when(proceduralObjects.getAllPlacements()).thenReturn(new KList<>(proceduralPlacement));
        IrisRegion upperRegion = mock(IrisRegion.class);
        when(upperRegion.getObjects()).thenReturn(new KList<>());
        when(upperRegion.getProceduralObjects()).thenReturn(proceduralObjects);
        IrisDimension baseDimension = mock(IrisDimension.class);
        when(baseDimension.isUpperDimensionObjects()).thenReturn(true);
        when(baseDimension.getAllRegions(any())).thenReturn(new KList<>());
        when(baseDimension.getReachableBiomes(any())).thenReturn(new KList<>());
        IrisDimension upperDimension = mock(IrisDimension.class);
        when(upperDimension.getAllRegions(any())).thenReturn(new KList<>(upperRegion));
        when(upperDimension.getReachableBiomes(any())).thenReturn(new KList<>());
        UpperDimensionContext upperContext = mock(UpperDimensionContext.class);
        when(upperContext.getDimension()).thenReturn(upperDimension);
        when(upperContext.getData()).thenReturn(upperData);
        Engine engine = mock(Engine.class);
        when(engine.getDimension()).thenReturn(baseDimension);
        when(engine.getUpperContext()).thenReturn(upperContext);
        EngineMantle engineMantle = mock(EngineMantle.class);
        when(engineMantle.getEngine()).thenReturn(engine);
        when(engineMantle.getData()).thenReturn(baseData);

        MantleObjectComponent component = new MantleObjectComponent(engineMantle);

        assertEquals(53, component.getRadius());
        assertEquals(182, component.getInputRadius());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void translatedTreeSchedulesDistantOwnerChunks() throws Exception {
        File objectFile = temporaryFolder.newFile("tree.iob");
        try (DataOutputStream output = new DataOutputStream(new FileOutputStream(objectFile))) {
            output.writeInt(1);
            output.writeInt(1);
            output.writeInt(1);
        }

        IrisObjectPlacement placement = new IrisObjectPlacement()
                .setPlace(new KList<>("test/tree"))
                .setTranslate(new IrisObjectTranslate().setX(32));
        IrisBiome biome = new IrisBiome().setObjects(new KList<>(placement));
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.isUseMantle()).thenReturn(true);
        when(dimension.getAllRegions(any())).thenReturn(new KList<>());
        when(dimension.getReachableBiomes(any())).thenReturn(new KList<>(biome));

        ResourceLoader<IrisObject> objectLoader = mock(ResourceLoader.class);
        when(objectLoader.findFile("test/tree")).thenReturn(objectFile);
        IrisData data = mock(IrisData.class);
        when(data.getObjectLoader()).thenReturn(objectLoader);

        EngineMantle engineMantle = mock(EngineMantle.class);
        Engine engine = mock(Engine.class);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getMantle()).thenReturn(engineMantle);
        when(engineMantle.getEngine()).thenReturn(engine);
        when(engineMantle.getData()).thenReturn(data);

        MantleObjectComponent component = spy(new MantleObjectComponent(engineMantle));
        assertEquals(33, component.getRadius());
        assertEquals(33, component.getOutputRadius());
        assertEquals(0, component.getInputRadius());

        Mantle<Matter> mantle = mock(Mantle.class);
        MantleChunk<Matter> chunk = mock(MantleChunk.class);
        when(mantle.getWorldHeight()).thenReturn(64);
        when(mantle.getChunk(anyInt(), anyInt())).thenReturn(chunk);
        when(chunk.use()).thenReturn(chunk);
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(chunk).raiseFlagSuspend(any(), any(Runnable.class));

        MantleWriter directWriter = mock(MantleWriter.class);
        when(directWriter.getMantle()).thenReturn(mantle);
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(2);
            task.run();
            return null;
        }).when(directWriter).withChunkFence(anyInt(), anyInt(), any(Runnable.class));
        AtomicInteger directOrigins = new AtomicInteger();
        doAnswer(invocation -> {
            directOrigins.incrementAndGet();
            return null;
        }).when(component).generateOrigin(any(), anyInt(), anyInt(), any());

        component.generateLayer(directWriter, 0, 0, mock(ChunkContext.class));

        assertEquals(1, directOrigins.get());
        component.generateLayer(directWriter, 0, 0, mock(ChunkContext.class));
        assertEquals(2, directOrigins.get());

        List<String> generatedChunks = new ArrayList<>();
        doAnswer(invocation -> {
            int chunkX = invocation.getArgument(1);
            int chunkZ = invocation.getArgument(2);
            generatedChunks.add(chunkX + "," + chunkZ);
            return null;
        }).when(component).generateLayer(any(), anyInt(), anyInt(), any());

        TestMatterGenerator generator = new TestMatterGenerator(new GeneratorOptions(
                engine,
                mantle,
                component,
                component.getOutputRadius()
        ));
        generator.generateMatter(0, 0, false, mock(ChunkContext.class));

        assertEquals(49, generatedChunks.size());

        List<String> replayedSources = new ArrayList<>();
        MantleObjectComponent.replaySourceChunks(
                0,
                0,
                component.getRadius(),
                (chunkX, chunkZ) -> replayedSources.add(chunkX + "," + chunkZ)
        );
        assertEquals(49, replayedSources.size());
        assertEquals("-3,-3", replayedSources.getFirst());
        assertEquals("-3,-2", replayedSources.get(1));
        assertEquals("3,3", replayedSources.getLast());

        placement.getForbiddenCollisions().add("test/tree");
        component.hotload();
        assertEquals(130, component.getInputRadius());
    }

    private static final class TestMatterGenerator implements MatterGenerator {
        private final Engine engine;
        private final Mantle<Matter> mantle;
        private final List<MantlePass> components;
        private final int radius;

        private TestMatterGenerator(GeneratorOptions options) {
            this.engine = options.engine();
            this.mantle = options.mantle();
            this.components = List.of(new MantlePass(List.of(options.component()), options.radius(), 0));
            this.radius = options.radius();
        }

        @Override
        public Engine getEngine() {
            return engine;
        }

        @Override
        public Mantle<Matter> getMantle() {
            return mantle;
        }

        @Override
        public int getRadius() {
            return radius;
        }

        @Override
        public int getRealRadius() {
            return 0;
        }

        @Override
        public List<MantlePass> getComponents() {
            return components;
        }
    }

    private record GeneratorOptions(Engine engine, Mantle<Matter> mantle, MantleComponent component, int radius) {
    }
}
