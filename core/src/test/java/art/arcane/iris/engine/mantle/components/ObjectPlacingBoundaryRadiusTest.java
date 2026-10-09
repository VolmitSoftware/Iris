package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.builtin.anchor.ObjectPlaceMode;
import art.arcane.iris.engine.object.TestSettings;
import art.arcane.iris.builtin.vacuum.VacuumSettings;
import art.arcane.iris.engine.object.noise.IrisGeneratorStyle;
import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.mantle.MantleComponent;
import art.arcane.iris.engine.mantle.MantlePass;
import art.arcane.iris.engine.mantle.MatterGenerator;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.prefab.IrisObjectRotation;
import art.arcane.iris.engine.object.prefab.IrisObjectScale;
import art.arcane.iris.engine.object.prefab.IrisObjectTranslate;
import art.arcane.iris.engine.object.procedural.IrisProceduralObjects;
import art.arcane.iris.engine.object.procedural.IrisProceduralPlacement;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.engine.object.noise.NoiseStyle;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.util.common.math.IrisBlockVector;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.mantle.flag.ReservedFlag;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ObjectPlacingBoundaryRadiusTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void bindPlatform() {
        IrisPlatforms.unbind();
        PlatformRegistries registries = mock(PlatformRegistries.class);
        // IrisObject retains these built-in states after this fixture unbinds its platform.
        Map<String, PlatformBlockState> registryStates = new HashMap<>();
        when(registries.block(anyString())).thenAnswer(invocation -> {
            String key = invocation.<String>getArgument(0).toLowerCase(java.util.Locale.ROOT);
            return registryStates.computeIfAbsent(key, value -> {
                PlatformBlockState block = mock(PlatformBlockState.class);
                when(block.key()).thenReturn(value);
                when(block.materialKey()).thenReturn(value.split("\\[", 2)[0]);
                when(block.isSolid()).thenReturn(!value.contains("air"));
                when(block.isOccluding()).thenReturn(!value.contains("air"));
                return block;
            });
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

        assertEquals(53, ObjectPlacing.calculatePlacementReach(new IrisBlockVector(5, 48, 7), placement));
    }

    @Test
    public void scaleWarpAndVacuumExpandPlacementReach() {
        IrisObjectPlacement placement = new IrisObjectPlacement()
                .setScale(new IrisObjectScale().setSize(2D))
                .setWarp(new IrisGeneratorStyle(NoiseStyle.SIMPLEX).setMultiplier(10D))
                .setMode(ObjectPlaceMode.VACUUM)
                .setVacuumSettings(TestSettings.of(VacuumSettings.class, "{\"radius\":12}"));

        assertEquals(43, ObjectPlacing.calculatePlacementReach(new IrisBlockVector(5, 4, 3), placement));
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
        when(engine.getData()).thenReturn(data);

        assertEquals(53, new ObjectPlacing(engine).reach());
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

        Engine engine = mock(Engine.class);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getData()).thenReturn(data);

        int reach = new ObjectPlacing(engine).reach();
        assertEquals(33, reach);
        int chunkRadius = Math.ceilDiv(reach, 16);
        assertEquals(3, chunkRadius);
        MantleComponent component = mock(MantleComponent.class);
        when(component.getRadius()).thenReturn(reach);
        when(component.getFlag()).thenReturn(ReservedFlag.OBJECT);
        when(component.isEnabled()).thenReturn(true);
        when(component.getPrerequisiteFlags()).thenReturn(MantleComponent.EMPTY_PREREQUISITES);

        Mantle<Matter> mantle = mock(Mantle.class);
        MantleChunk<Matter> chunk = mock(MantleChunk.class);
        when(mantle.getChunk(anyInt(), anyInt())).thenReturn(chunk);
        when(chunk.use()).thenReturn(chunk);
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(chunk).raiseFlagSuspend(any(), any(Runnable.class));

        List<String> generatedChunks = new ArrayList<>();
        doAnswer(invocation -> {
            int chunkX = invocation.getArgument(1);
            int chunkZ = invocation.getArgument(2);
            generatedChunks.add(chunkX + "," + chunkZ);
            return null;
        }).when(component).generateLayer(any(), anyInt(), anyInt(), any());

        TestMatterGenerator generator = new TestMatterGenerator(new GeneratorOptions(engine, mantle, component, chunkRadius));
        generator.generateMatter(0, 0, false, mock(ChunkContext.class));

        assertTrue(generatedChunks.contains("-2,0"));
        assertTrue(generatedChunks.contains("2,0"));
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
