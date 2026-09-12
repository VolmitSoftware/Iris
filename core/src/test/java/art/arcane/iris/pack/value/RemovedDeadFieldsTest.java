package art.arcane.iris.pack.value;

import art.arcane.iris.generation.decoration.tree.IrisTree;
import art.arcane.iris.generation.terrain.InferredType;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.structure.object.IrisObjectPlacement;

import art.arcane.iris.configuration.IrisSettings;
import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

public class RemovedDeadFieldsTest {
    @Test
    public void removedFieldsStayRemoved() {
        assertThrows(NoSuchFieldException.class, () -> IrisDimension.class.getDeclaredField("forceConvertTo320Height"));
        assertThrows(NoSuchFieldException.class, () -> IrisDimension.class.getDeclaredField("rockZoom"));
        assertThrows(NoSuchFieldException.class, () -> IrisDimension.class.getDeclaredField("disableExplorerMaps"));
        assertThrows(NoSuchFieldException.class, () -> IrisRegion.class.getDeclaredField("riverStyle"));
        assertThrows(NoSuchFieldException.class, () -> IrisRegion.class.getDeclaredField("lakeStyle"));
        assertThrows(NoSuchFieldException.class, () -> IrisRegion.class.getDeclaredField("riverGen"));
        assertThrows(NoSuchFieldException.class, () -> IrisRegion.class.getDeclaredField("lakeGen"));
        assertThrows(NoSuchFieldException.class, () -> IrisRegion.class.getDeclaredField("riverChanceGen"));
        assertThrows(NoSuchFieldException.class, () -> IrisRegion.class.getDeclaredField("realRiverBiomes"));
        assertThrows(NoSuchFieldException.class, () -> IrisRegion.class.getDeclaredField("realLakeBiomes"));
        assertThrows(NoSuchFieldException.class, () -> IrisObjectPlacement.class.getDeclaredField("translateCenter"));
        assertThrows(NoSuchFieldException.class, () -> IrisTree.class.getDeclaredField("anyTree"));
        assertThrows(NoSuchFieldException.class, () -> IrisTree.class.getDeclaredField("anySize"));
        assertThrows(NoSuchMethodException.class, () -> IrisRegion.class.getDeclaredMethod("getBiomeZoom", InferredType.class));
    }

    @Test
    public void legacyKeysAreIgnoredNotFatal() {
        IrisDimension dimension = new Gson().fromJson(
                "{\"name\":\"x\",\"rockZoom\":9,\"disableExplorerMaps\":true,\"forceConvertTo320Height\":true}",
                IrisDimension.class);

        assertNotNull(dimension);
        assertEquals("x", dimension.getName());

        IrisTree tree = new Gson().fromJson("{\"anyTree\":true,\"anySize\":true}", IrisTree.class);
        assertNotNull(tree);

        IrisRegion region = new Gson().fromJson(
                "{\"name\":\"r\",\"riverStyle\":{\"zoom\":7.77},\"lakeStyle\":{}}", IrisRegion.class);
        assertNotNull(region);
        assertEquals("r", region.getName());
    }

    @Test
    public void studioSettingsKeepTheirLiveToggles() throws Exception {
        // disableTimeAndWeather is read by WorldRuntimeControlService, and autoStartDefaultStudio
        // by Iris.autoStartStudio() at boot - both are live and must NOT be removed.
        assertNotNull(IrisSettings.IrisSettingsStudio.class.getDeclaredField("disableTimeAndWeather"));
        assertNotNull(IrisSettings.IrisSettingsStudio.class.getDeclaredField("autoStartDefaultStudio"));
    }
}
