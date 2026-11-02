package art.arcane.iris.localization;

import art.arcane.volmlib.util.localization.LanguageCatalogMerge;
import art.arcane.volmlib.util.localization.LanguageUpstreamSync;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.MessageValue;
import art.arcane.volmlib.util.localization.TextValue;
import art.arcane.volmlib.util.localization.TomlLanguageEditor;
import art.arcane.volmlib.util.localization.TomlLanguageParser;
import art.arcane.volmlib.util.localization.VolmitLocales;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class IrisLanguageSyncTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private File dataFolder;
    private Path languages;

    @Before
    public void setUp() throws Exception {
        dataFolder = temporaryFolder.newFolder();
        languages = dataFolder.toPath().resolve("languages");
    }

    @After
    public void tearDown() {
        IrisLanguage.shutdown();
        assertTrue(IrisLanguage.reload(dataFolder, "en_US"));
        IrisLanguage.shutdown();
    }

    @Test
    public void updateMergesTheRepositoryBranchWhileManualEditsAndCustomFilesSurvive() throws Exception {
        try (LoopbackLanguageRepository repository = new LoopbackLanguageRepository()) {
            repository.publish(LoopbackLanguageRepository.REVISION_ONE, "fr_FR", """
                    [iris.command]
                    unknown = "Commande inconnue v1"

                    [iris.command.reload]
                    success = "Langue {locale} v1"
                    """);
            assertTrue(IrisLanguage.reload(dataFolder, "en_US"));
            IrisLanguage.start();
            UUID player = UUID.randomUUID();
            IrisLanguage.selections().selectPlayer(player, "fr_FR").get(10L, TimeUnit.SECONDS);

            Path french = languages.resolve("fr_FR.toml");
            assertTrue(Files.isRegularFile(languages.resolve(".upstream/fr_FR.toml")));
            assertTrue(Files.isRegularFile(languages.resolve(".upstream/upstream.properties")));
            assertEquals("Commande inconnue v1", IrisLanguage.plain(player, IrisMessages.COMMAND_UNKNOWN, MessageArgs.empty()));

            Files.writeString(french, TomlLanguageEditor.upsert(Files.readString(french),
                    IrisMessages.COMMAND_RELOAD_SUCCESS.id(), new TextValue("Langue locale {locale}")).content());
            Path pirate = languages.resolve("pirate.toml");
            String custom = """
                    [meta]
                    name = "Pirate Speak"
                    base = "fr_FR"

                    [iris.command]
                    unknown = "Arr, no such command"
                    """;
            Files.writeString(pirate, custom);
            repository.publish(LoopbackLanguageRepository.REVISION_TWO, "fr_FR", """
                    [iris.command]
                    unknown = "Commande inconnue v2"
                    permission_denied = "Permission {permission} manquante"

                    [iris.command.reload]
                    success = "Langue {locale} v2"
                    """);
            repository.advance(LoopbackLanguageRepository.REVISION_TWO);
            Path state = languages.resolve(".upstream/upstream.properties");
            Files.writeString(state, Files.readString(state).replaceAll("(?m)^checked=.*$", "checked=0"));

            LanguageUpstreamSync.SyncReport report = IrisLanguage.checkUpstream().get(10L, TimeUnit.SECONDS);

            assertTrue(report.failure().isEmpty());
            assertEquals(List.of("fr_FR"), report.changedLocales());
            LanguageCatalogMerge.MergeResult merged = report.merged().get("fr_FR");
            assertEquals(List.of(IrisMessages.COMMAND_RELOAD_SUCCESS.id()), merged.kept());
            assertEquals(List.of(IrisMessages.COMMAND_UNKNOWN.id()), merged.updated());
            assertEquals(List.of(IrisMessages.COMMAND_PERMISSION_DENIED.id()), merged.added());
            Map<String, MessageValue> values = TomlLanguageParser.parseValidValues(Files.readString(french), IrisLanguage.catalog());
            assertEquals("Langue locale {locale}", text(values.get(IrisMessages.COMMAND_RELOAD_SUCCESS.id())));
            assertEquals("Commande inconnue v2", text(values.get(IrisMessages.COMMAND_UNKNOWN.id())));
            assertTrue(Files.readString(languages.resolve(".upstream/fr_FR.toml")).contains("Langue {locale} v2"));
            assertEquals(custom, Files.readString(pirate));
            assertEquals("Commande inconnue v2", IrisLanguage.plain(player, IrisMessages.COMMAND_UNKNOWN, MessageArgs.empty()));
            assertEquals(LoopbackLanguageRepository.REVISION_TWO,
                    IrisLanguage.upstreamStatus().orElseThrow().revision().orElseThrow());
            assertTrue(IrisLanguage.debugReport().contains(LoopbackLanguageRepository.REVISION_TWO));

            String settled = Files.readString(french);
            LanguageUpstreamSync.SyncReport again = IrisLanguage.checkUpstream().get(10L, TimeUnit.SECONDS);

            assertTrue(again.coolingDown());
            assertTrue(again.changedLocales().isEmpty());
            assertEquals(settled, Files.readString(french));
        }
    }

    @Test
    public void metaBaseChainResolvesBeforeEnglish() throws Exception {
        Files.createDirectories(languages);
        Files.writeString(languages.resolve("de_DE.toml"), """
                [iris.command]
                unknown = "Unbekannter Befehl"

                [iris.command.reload]
                success = "Sprache {locale} geladen"
                """);
        Files.writeString(languages.resolve("de_AT.toml"), """
                [meta]
                name = "Österreichisch"
                base = "de_DE"

                [iris.command]
                unknown = "Unbekannter Befehl, Oida"
                """);

        assertTrue(IrisLanguage.reload(dataFolder, "de_AT"));

        assertEquals("Unbekannter Befehl, Oida", IrisLanguage.plain(IrisMessages.COMMAND_UNKNOWN));
        assertEquals("Sprache x geladen", IrisLanguage.plain(IrisMessages.COMMAND_RELOAD_SUCCESS,
                MessageArgument.untrusted("locale", "x")));
        assertFalse(IrisLanguage.plain(IrisMessages.COMMAND_PERMISSION_DENIED,
                MessageArgument.untrusted("permission", "iris.all")).isBlank());
        assertTrue(IrisLanguage.availableLocales().contains("de_AT"));
        assertEquals("Österreichisch", IrisLanguage.displayName("de_AT"));
        assertEquals("Österreichisch", IrisLanguage.metaName("de_AT"));
        assertNull(IrisLanguage.metaName("de_DE"));
        assertEquals(VolmitLocales.displayName("de_DE").orElseThrow(), IrisLanguage.displayName("de_DE"));
        assertEquals("de_DE", IrisLanguage.meta("de_AT").base().orElseThrow());
    }

    @Test
    public void englishReferenceGainsNewCatalogKeysWhileKeepingEdits() throws Exception {
        assertTrue(IrisLanguage.reload(dataFolder, "en_US"));
        Path english = languages.resolve("en_US.toml");
        Path sidecar = languages.resolve(".upstream/en_US.toml");
        String older = TomlLanguageEditor.remove(Files.readString(sidecar), IrisMessages.COMMAND_UNKNOWN.id()).content();
        Files.writeString(sidecar, older);
        Files.writeString(english, TomlLanguageEditor.upsert(older, IrisMessages.COMMAND_RELOAD_SUCCESS.id(),
                new TextValue("Reloaded {locale} locally")).content());
        IrisLanguage.shutdown();

        assertTrue(IrisLanguage.reload(dataFolder, "en_US"));

        Map<String, MessageValue> values = TomlLanguageParser.parseValidValues(Files.readString(english), IrisLanguage.catalog());
        assertEquals(IrisLanguage.plain(IrisMessages.COMMAND_UNKNOWN), text(values.get(IrisMessages.COMMAND_UNKNOWN.id())));
        assertEquals("Reloaded {locale} locally", text(values.get(IrisMessages.COMMAND_RELOAD_SUCCESS.id())));
        assertEquals("Reloaded x locally", IrisLanguage.plain(IrisMessages.COMMAND_RELOAD_SUCCESS,
                MessageArgument.untrusted("locale", "x")));
        assertEquals(IrisLanguage.englishReference(), Files.readString(sidecar));
    }

    private static String text(Object value) {
        return ((TextValue) value).template();
    }
}
