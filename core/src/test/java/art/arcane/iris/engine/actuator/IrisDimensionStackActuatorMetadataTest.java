package art.arcane.iris.engine.actuator;

import art.arcane.iris.engine.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.engine.mantle.TerrainMatterView;
import art.arcane.iris.util.project.matter.IrisMatterSupport;
import art.arcane.iris.util.project.matter.slices.PreObjectMatterTest;
import art.arcane.volmlib.util.matter.IrisMatter;
import art.arcane.volmlib.util.matter.MatterCavern;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterSlice;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IrisDimensionStackActuatorMetadataTest {
    @Before
    public void bindPlatform() {
        IrisPlatforms.unbind();
        Map<String, PlatformBlockState> states = new HashMap<>();
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.block(anyString())).thenAnswer(invocation -> states.computeIfAbsent(
                invocation.getArgument(0),
                key -> {
                    PlatformBlockState state = mock(PlatformBlockState.class);
                    when(state.key()).thenReturn(key);
                    return state;
                }));
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);
    }

    @After
    public void unbindPlatform() {
        IrisPlatforms.unbind();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void stackCleanupPersistsNaturalCarveAndFluidFactsForLaterRerenders() throws IOException {
        PreObjectMatterTest.setUpBukkit();
        IrisMatterSupport.ensureRegistered();
        Matter section = new IrisMatter(16, 16, 16);
        MatterCavern cavern = new MatterCavern(true, "cave", (byte) 0);
        HydrologyCaveCell hydrology = new HydrologyCaveCell(HydrologyCaveAction.WET_SOURCE, "river", "flooded");
        section.<MatterCavern>slice(MatterCavern.class).set(3, 4, 7, cavern);
        section.<HydrologyCaveCell>slice(HydrologyCaveCell.class).set(3, 4, 7, hydrology);
        MantleChunk<Matter> chunk = mock(MantleChunk.class);
        when(chunk.get(1)).thenReturn(section);
        new IrisDimensionStackActuator.MetadataCleaner(chunk, 64).clear(3, 20, 7);
        assertNull(section.<MatterCavern>getSlice(MatterCavern.class).get(3, 4, 7));
        assertNull(section.<HydrologyCaveCell>getSlice(HydrologyCaveCell.class).get(3, 4, 7));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        section.write(bytes);
        Matter restored = Matter.read(new ByteArrayInputStream(bytes.toByteArray()));
        when(chunk.exists(1)).thenReturn(true);
        when(chunk.get(1)).thenReturn(restored);
        assertEquals(cavern, TerrainMatterView.get(chunk, 3, 20, 7, MatterCavern.class));
        assertEquals(hydrology, TerrainMatterView.get(chunk, 3, 20, 7, HydrologyCaveCell.class));
    }

    @Test
    public void speculativeCleanupHasNoPersistentChunk() {
        IrisDimensionStackActuator.MetadataCleaner cleaner =
                new IrisDimensionStackActuator.MetadataCleaner(null, 64);
        cleaner.clear(3, 20, 7);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void overwrittenStackCellsClearHostHydrologyCaveOwnership() {
        MantleChunk<Matter> chunk = mock(MantleChunk.class);
        Matter section = mock(Matter.class);
        MatterSlice<HydrologyCaveCell> hydrology = mock(MatterSlice.class);
        when(chunk.get(1)).thenReturn(section);
        doReturn(hydrology).when(section).getSlice(HydrologyCaveCell.class);

        IrisDimensionStackActuator.MetadataCleaner cleaner =
                new IrisDimensionStackActuator.MetadataCleaner(chunk, 64);
        cleaner.clear(3, 20, 7);

        verify(hydrology).set(3, 4, 7, null);
    }
}
