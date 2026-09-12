package art.arcane.iris.configuration;

import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

public class IrisSettingsDefaultsTest {
    @Test
    public void concurrencyIsNotSerializedIntoIrisJson() {
        String json = new Gson().toJson(new IrisSettings());

        assertFalse("iris.json must not advertise an unconfigurable concurrency block",
                json.contains("\"concurrency\""));
    }

    @Test
    public void languageAndMetricsAreTheFirstGeneralControls() {
        String json = new Gson().toJson(new IrisSettings());

        int language = json.indexOf("\"language\"");
        int metrics = json.indexOf("\"metrics\"");
        int commandSounds = json.indexOf("\"commandSounds\"");
        assertTrue(language >= 0);
        assertTrue(language < metrics);
        assertTrue(metrics < commandSounds);
    }

    @Test
    public void datapackIngestStaysAutomaticWhileEditableConversionIsOptIn() {
        IrisSettings.IrisSettingsGeneral settings = new IrisSettings.IrisSettingsGeneral();

        assertTrue(settings.isAutoIngestDatapacks());
        assertFalse(settings.isAutoImportDatapackStructures());
    }

    @Test
    public void generationTransitionWidthHasSafeBounds() {
        IrisSettings.IrisSettingsGenerator settings = new IrisSettings.IrisSettingsGenerator();

        assertEquals(256, settings.getGenerationTransitionWidthBlocks());
        settings.setGenerationTransitionWidthBlocks(1);
        assertEquals(16, settings.getGenerationTransitionWidthBlocks());
        settings.setGenerationTransitionWidthBlocks(20_000);
        assertEquals(8_192, settings.getGenerationTransitionWidthBlocks());
    }

    @Test
    public void hotloadSnapshotIsValidatedBeforeReplacingLiveSettings() {
        IrisSettings previous = IrisSettings.settings;
        IrisSettings live = new IrisSettings();
        IrisSettings.settings = live;
        try {
            assertThrows(IllegalArgumentException.class,
                    () -> IrisSettings.installHotloadSnapshot("{\"general\":"));
            assertSame(live, IrisSettings.settings);

            IrisSettings installed = IrisSettings.installHotloadSnapshot(
                    "{\"general\":{\"language\":\"fr_FR\"},\"world\":null}");
            assertSame(installed, IrisSettings.settings);
            assertEquals("fr_FR", installed.getGeneral().getLanguage());
            assertNotNull(installed.getWorld());
        } finally {
            IrisSettings.settings = previous;
        }
    }

    @Test
    public void rejectedHotloadActivationKeepsPreviousLiveSettings() {
        IrisSettings previous = IrisSettings.settings;
        IrisSettings live = new IrisSettings();
        live.getGeneral().setLanguage("en_US");
        IrisSettings.settings = live;
        try {
            boolean applied = IrisSettings.applyHotloadSnapshot(
                    "{\"general\":{\"language\":\"de_DE\"}}",
                    candidate -> false
            );

            assertFalse(applied);
            assertSame(live, IrisSettings.settings);
            assertEquals("en_US", IrisSettings.settings.getGeneral().getLanguage());
        } finally {
            IrisSettings.settings = previous;
        }
    }

    @Test
    public void successfulHotloadActivationPublishesCandidateSettings() {
        IrisSettings previous = IrisSettings.settings;
        IrisSettings live = new IrisSettings();
        IrisSettings.settings = live;
        try {
            boolean applied = IrisSettings.applyHotloadSnapshot(
                    "{\"general\":{\"language\":\"de_DE\"},\"world\":null}",
                    candidate -> "de_DE".equals(candidate.getGeneral().getLanguage())
            );

            assertTrue(applied);
            assertNotSame(live, IrisSettings.settings);
            assertEquals("de_DE", IrisSettings.settings.getGeneral().getLanguage());
            assertNotNull(IrisSettings.settings.getWorld());
        } finally {
            IrisSettings.settings = previous;
        }
    }
}
