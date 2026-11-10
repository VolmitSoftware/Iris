package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.ObjectSettings;
import art.arcane.iris.modules.objects.ObjectSize;
import com.volmit.geometry.BlockBox;
import com.volmit.object.PackObjects;
import com.volmit.object.PlaceableObject;
import com.volmit.resource.PackResources;
import com.volmit.block.BlockState;
import art.arcane.iris.engine.procedural.ProceduralObjectCatalog;
import art.arcane.iris.engine.procedural.part.ProceduralObject;
import art.arcane.iris.builtin.anchor.ObjectPlaceMode;
import art.arcane.iris.builtin.prepare.ObjectRotation;
import art.arcane.iris.builtin.prepare.ObjectTranslate;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.mantle.MantleComponent;
import art.arcane.iris.engine.mantle.MantlePass;
import art.arcane.iris.engine.mantle.MatterGenerator;
import art.arcane.iris.engine.object.TestSettings;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.volmlib.util.mantle.flag.ReservedFlag;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import com.volmit.config.Level;
import com.volmit.IrisFeature;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.config.PackSettings;
import com.volmit.config.SettingsStack;
import com.volmit.host.ModuleLogs;
import com.volmit.resource.ResourceId;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PlacementReachTest {
    private static final ResourceId SOURCE = ResourceId.parse("test:biome/a");

    @Test
    public void verticalRotationAndTranslationExpandHorizontalReach() {
        ObjectPlacement placement = TestSettings.of(ObjectPlacement.class,
                "{\"rotation\":{\"xAxis\":{\"enabled\":true,\"min\":90,\"max\":90,\"interval\":90},\"yAxis\":{\"enabled\":false}},"
                        + "\"translate\":{\"x\":3,\"y\":4}}");

        assertEquals(53, PlacementReach.of(new ObjectSize(5, 48, 7), placement));
    }

    @Test
    public void slopeTiltReachesAsFarAsAVerticalRotationEvenWhenTheRotationIsDisabled() {
        String disabledRotation = "\"rotation\":{\"enabled\":false}";
        ObjectPlacement flat = TestSettings.of(ObjectPlacement.class, "{" + disabledRotation + ",\"translate\":{\"x\":3,\"y\":4}}");
        ObjectPlacement tilted = TestSettings.of(ObjectPlacement.class,
                "{" + disabledRotation + ",\"rotateTowardsSlope\":true,\"translate\":{\"x\":3,\"y\":4}}");
        ObjectSize tallObject = new ObjectSize(5, 48, 7);

        assertEquals(10, PlacementReach.of(tallObject, flat));
        assertEquals(53, PlacementReach.of(tallObject, tilted));
    }

    @Test
    public void scaleWarpAndVacuumExpandPlacementReach() {
        ObjectPlacement placement = TestSettings.of(ObjectPlacement.class,
                "{\"scale\":{\"size\":2},\"warp\":{\"style\":\"SIMPLEX\",\"multiplier\":10},\"mode\":\"VACUUM\","
                        + "\"vacuumSettings\":{\"radius\":12}}");

        assertEquals(43, PlacementReach.of(new ObjectSize(5, 4, 3), placement));
    }

    @Test
    public void aProceduralObjectReachesAsFarAsItsLargestVariantTransformed() {
        PlaceableObject variant = mock(PlaceableObject.class);
        when(variant.width()).thenReturn(5);
        when(variant.height()).thenReturn(48);
        when(variant.depth()).thenReturn(7);
        PackResources resources = mock(PackResources.class, RETURNS_DEEP_STUBS);
        PackObjects objects = mock(PackObjects.class);
        when(resources.objects()).thenReturn(objects);
        when(objects.build(any(), anyInt(), anyInt(), anyInt(), any())).thenReturn(variant);
        when(resources.blocks().find(anyString())).thenReturn(Optional.of(BlockState.parse("minecraft:oak_log")));
        when(resources.idOf(anyString())).thenAnswer(invocation -> new ResourceId("test", invocation.getArgument(0)));
        when(resources.find(eq(ProceduralObjectCatalog.DEFINITIONS), any())).thenReturn(Optional.of(TestSettings.of(ProceduralObject.class,
                "{\"variants\":1,\"materials\":[{\"block\":\"minecraft:oak_log\"}],\"parts\":[{\"stem\":{\"length\":3}}]}")));
        ObjectPlacement placement = TestSettings.of(ObjectPlacement.class, "{\"place\":[\"procedural/trees/tall\"],"
                + "\"rotation\":{\"xAxis\":{\"enabled\":true,\"min\":90,\"max\":90,\"interval\":90},\"yAxis\":{\"enabled\":false}},"
                + "\"translate\":{\"x\":3,\"y\":4}}");
        ObjectSettings settings = TestSettings.with(new ObjectSettings(), "objects", List.of(placement));

        assertEquals(53, new PlacementReach(resources, ModuleLogs.discarding()).ofPack(declaring(ObjectSettings.class, settings)));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void translatedTreeSchedulesDistantOwnerChunks() throws Exception {
        ObjectPlacement placement = TestSettings.of(ObjectPlacement.class, "{\"place\":[\"test/tree\"],\"translate\":{\"x\":32}}");
        PackResources resources = mock(PackResources.class);
        PackObjects objects = mock(PackObjects.class);
        ResourceId tree = ResourceId.parse("test:test/tree");
        when(resources.objects()).thenReturn(objects);
        when(resources.idOf("test/tree")).thenReturn(tree);
        when(objects.sizeOf(tree)).thenReturn(Optional.of(new BlockBox(0, 0, 0, 1, 1, 1)));
        ObjectSettings settings = TestSettings.with(new ObjectSettings(), "objects", List.of(placement));

        int reach = new PlacementReach(resources, ModuleLogs.discarding()).ofPack(declaring(ObjectSettings.class, settings));
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

        Engine engine = mock(Engine.class);
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.isUseMantle()).thenReturn(true);
        when(engine.getDimension()).thenReturn(dimension);
        TestMatterGenerator generator = new TestMatterGenerator(new GeneratorOptions(engine, mantle, component, chunkRadius));
        generator.generateMatter(0, 0, false, mock(ChunkContext.class));

        assertTrue(generatedChunks.contains("-2,0"));
        assertTrue(generatedChunks.contains("2,0"));
    }

    private static <F extends IrisFeature> PackSettings declaring(Class<F> type, F settings) {
        return new PackSettings() {
            @Override
            @SuppressWarnings("unchecked")
            public <T extends IrisFeature> SettingsStack<T> declared(Class<T> featureType) {
                return featureType == type ? SettingsStack.of(List.of(new Entry<>(Level.BIOME, SOURCE, (T) settings))) : SettingsStack.empty();
            }

            @Override
            public PackResources resources() {
                throw new UnsupportedOperationException("the reach is handed the resources itself");
            }
        };
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
