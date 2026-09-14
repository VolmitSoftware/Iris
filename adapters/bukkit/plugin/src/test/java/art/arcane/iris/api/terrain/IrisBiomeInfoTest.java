package art.arcane.iris.api.terrain;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class IrisBiomeInfoTest {
    @Test
    public void snapshotsDoNotExposeMutableDerivativeLists() {
        List<IrisCustomBiomeInfo> derivatives = new ArrayList<>();
        derivatives.add(new IrisCustomBiomeInfo("amber", "iris:biomes/amber"));
        IrisBiomeInfo info = new IrisBiomeInfo(
                "forests/amber", "Amber Forest", "temperate", "Temperate",
                "minecraft:forest", "minecraft:forest", "land", derivatives);

        derivatives.clear();

        assertEquals(1, info.customDerivatives().size());
        assertThrows(UnsupportedOperationException.class, () -> info.customDerivatives().clear());
    }

    @Test
    public void unavailableStringsAreEmpty() {
        IrisBiomeInfo info = new IrisBiomeInfo(null, null, null, null, null, null, null, List.of());
        IrisCustomBiomeInfo custom = new IrisCustomBiomeInfo(null, null);

        assertEquals("", info.key());
        assertEquals("", info.name());
        assertEquals("", info.regionKey());
        assertEquals("", info.regionName());
        assertEquals("", info.derivativeKey());
        assertEquals("", info.vanillaDerivativeKey());
        assertEquals("", info.type());
        assertEquals("", custom.id());
        assertEquals("", custom.registryKey());
    }
}
