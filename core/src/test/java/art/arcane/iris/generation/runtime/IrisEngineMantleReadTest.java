package art.arcane.iris.generation.runtime;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.util.mantle.runtime.MantleDataAdapter;
import art.arcane.volmlib.util.matter.IrisMatter;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterBiomeInject;
import art.arcane.volmlib.util.matter.slices.BiomeInjectMatter;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class IrisEngineMantleReadTest {
    @Test
    public void missingReadsAndRemovalsLeaveSectionEmpty() {
        MantleDataAdapter<Matter> adapter = IrisEngineMantle.createRuntimeDataAdapter(mock(IrisData.class));
        Matter section = new IrisMatter(16, 16, 16);

        for (int y = 0; y < 16; y++) {
            assertNull(adapter.get(section, 0, y, 0, MatterBiomeInject.class));
            adapter.remove(section, 0, y, 0, MatterBiomeInject.class);
        }

        assertTrue(section.getSliceMap().isEmpty());
    }

    @Test
    public void existingSlicesKeepValuesAndCoordinateRemoval() {
        MantleDataAdapter<Matter> adapter = IrisEngineMantle.createRuntimeDataAdapter(mock(IrisData.class));
        Matter section = new IrisMatter(16, 16, 16);
        BiomeInjectMatter slice = new BiomeInjectMatter(16, 16, 16);
        MatterBiomeInject value = BiomeInjectMatter.get("minecraft:plains");
        section.putSlice(MatterBiomeInject.class, slice);
        slice.set(0, 0, 0, value);
        slice.set(4, 4, 4, value);

        assertSame(value, adapter.get(section, 4, 4, 4, MatterBiomeInject.class));
        adapter.remove(section, 0, 0, 0, MatterBiomeInject.class);

        assertNull(adapter.get(section, 0, 0, 0, MatterBiomeInject.class));
        assertSame(value, adapter.get(section, 4, 4, 4, MatterBiomeInject.class));
        assertEquals(1, section.getSliceMap().size());
    }
}
