package art.arcane.iris.generation.cave;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.runtime.PreservationRegistry;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import com.google.gson.Gson;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class IrisCaveLoadingTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    private IrisData data;
    private IrisSettings previousSettings;

    @Before
    public void registerServices() {
        previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        IrisServices.register(PreservationRegistry.class, mock(PreservationRegistry.class));
    }

    @After
    public void closeData() {
        if (data != null) {
            data.close();
        }
        IrisServices.remove(PreservationRegistry.class);
        IrisSettings.settings = previousSettings;
    }

    @Test
    public void aPackLoadsItsCavesFromTheCavesFolderWithEveryFormOfValue() throws Exception {
        Path root = temporary.newFolder("pack").toPath();
        Files.createDirectories(root.resolve("caves"));
        Files.writeString(root.resolve("caves/halls.json"), """
                {"cavern": {"size": 110, "coverage": {"min": 0.1, "max": 0.3, "style": {"style": "SIMPLEX", "zoom": 2}},
                            "pillars": {"spacing": 26}},
                 "tunnel": {"coverage": 0.4},
                 "depth": {"min": -64, "max": 10}, "fade": 14, "biome": "grotto", "allowLava": false}
                """);
        data = IrisData.get(root.toFile());

        IrisCave halls = data.getCaveLoader().load("halls");

        assertEquals(List.of("halls"), List.of(data.getCaveLoader().getPossibleKeys()));
        assertEquals("caves", halls.getFolderName());
        assertEquals(110D, halls.cavern().orElseThrow().size().min(), 0D);
        assertTrue(halls.cavern().orElseThrow().coverage().varies());
        assertEquals(2D, halls.cavern().orElseThrow().coverage().style().orElseThrow().getZoom(), 0D);
        assertEquals(26D, halls.cavern().orElseThrow().pillars().orElseThrow().spacing().min(), 0D);
        assertEquals(0.4D, halls.tunnel().orElseThrow().coverage().share().min(), 0D);
        assertEquals(-64D, halls.depth().orElseThrow().min().min(), 0D);
        assertEquals(14D, halls.fade().min(), 0D);
        assertEquals("grotto", halls.biome());
        assertFalse(halls.allowLava());
        assertTrue(halls.allowFluid());
        assertEquals(12, halls.fluidMinDepthBelowSurface());
    }

    @Test
    public void aCaveReadsBackAsItWasWritten() {
        Gson gson = TestSettings.gson();
        IrisCave written = TestSettings.of(IrisCave.class, """
                {"tunnel": {"width": {"min": 3, "max": 6}, "coverage": {"share": 0.5, "softness": 0.2}}, "fade": 9}
                """);

        IrisCave read = gson.fromJson(gson.toJson(written), IrisCave.class);

        assertEquals(written.tunnel().orElseThrow().width().min(), read.tunnel().orElseThrow().width().min(), 0D);
        assertEquals(written.tunnel().orElseThrow().width().max(), read.tunnel().orElseThrow().width().max(), 0D);
        assertEquals(0.5D, read.tunnel().orElseThrow().coverage().share().min(), 0D);
        assertEquals(0.2D, read.tunnel().orElseThrow().coverage().softness().min(), 0D);
        assertEquals(9D, read.fade().min(), 0D);
        assertNull(read.cavern().orElse(null));
    }
}
