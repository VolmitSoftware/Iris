package art.arcane.iris.configuration;

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
    public void updateChecksDefaultOnAndPersistAnExplicitOptOut() throws Exception {
        assertTrue(IrisSettings.parseHotloadSnapshot("[general]").getGeneral().isUpdateNotifications());
        IrisSettings optedOut = IrisSettings.parseHotloadSnapshot("[general]\nupdateNotifications = false");
        assertFalse(optedOut.getGeneral().isUpdateNotifications());
        assertFalse(IrisSettingsFile.parse(IrisSettingsFile.render(optedOut))
                .getGeneral().isUpdateNotifications());
    }

    @Test
    public void regionBudgetLeavesHeapForHydrologyAndServerChunks() {
        IrisSettings.IrisSettingsPregen settings = new IrisSettings.IrisSettingsPregen();
        assertEquals(21, settings.effectiveResidentTectonicPlates(384, 4L << 30));
        assertEquals(42, settings.effectiveResidentTectonicPlates(384, 8L << 30));
        assertEquals(16, settings.effectiveResidentTectonicPlates(768, 4L << 30));
        settings.setMaxResidentTectonicPlates(16);
        assertEquals(16, settings.effectiveResidentTectonicPlates(384, 16L << 30));
    }

    @Test
    public void concurrencyIsNotWrittenToTheSettingsFile() {
        String toml = IrisSettingsFile.render(new IrisSettings());

        assertFalse("iris.toml must not advertise an unconfigurable concurrency block",
                toml.contains("concurrency"));
    }

    @Test
    public void languageAndMetricsAreTheFirstGeneralControls() {
        String toml = IrisSettingsFile.render(new IrisSettings());

        int language = toml.indexOf("\nlanguage = ");
        int metrics = toml.indexOf("\nmetrics = ");
        int commandSounds = toml.indexOf("\ncommandSounds = ");
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
    public void studioBlendingDefaultsOnAndAcceptsDisabledHotload() {
        IrisSettings defaults = new IrisSettings();
        assertTrue(defaults.getStudio().isBlendChanges());
        assertTrue(IrisSettings.parseHotloadSnapshot("[studio]").getStudio().isBlendChanges());
        assertTrue(IrisSettingsFile.render(defaults).contains("\nblendChanges = true"));

        IrisSettings changed = IrisSettings.parseHotloadSnapshot("[studio]\nblendChanges = false");

        assertFalse(changed.getStudio().isBlendChanges());
        assertEquals(256, changed.getGenerator().getGenerationTransitionWidthBlocks());
    }

    @Test
    public void hotloadSnapshotIsValidatedBeforeReplacingLiveSettings() {
        IrisSettings previous = IrisSettings.settings;
        IrisSettings live = new IrisSettings();
        IrisSettings.settings = live;
        try {
            assertThrows(IllegalArgumentException.class,
                    () -> IrisSettings.installHotloadSnapshot("[general"));
            assertSame(live, IrisSettings.settings);

            IrisSettings installed = IrisSettings.installHotloadSnapshot("[general]\nlanguage = \"fr_FR\"");
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
                    "[general]\nlanguage = \"de_DE\"",
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
                    "[general]\nlanguage = \"de_DE\"",
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
