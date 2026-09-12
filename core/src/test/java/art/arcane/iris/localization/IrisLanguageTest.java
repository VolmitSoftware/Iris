package art.arcane.iris.localization;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.testsupport.ProjectPaths;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.LocaleOverlay;
import art.arcane.volmlib.util.localization.LocalizationValidationResult;
import art.arcane.volmlib.util.localization.LocalizationValidator;
import art.arcane.volmlib.util.localization.LinesValue;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.MessageValue;
import art.arcane.volmlib.util.localization.PluralValue;
import art.arcane.volmlib.util.localization.TextValue;
import art.arcane.volmlib.util.localization.VolmitLocales;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import art.arcane.volmlib.util.localization.LanguageAudience;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

public class IrisLanguageTest {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[A-Za-z][A-Za-z0-9_]*}");
    private static final Pattern COLOR_CODE = Pattern.compile("(?i)(?:§|&)[0-9A-FK-ORX]");
    private static final Pattern MINI_MESSAGE_TAG = Pattern.compile("<[^<>\\n]+>");
    private static final Pattern URL = Pattern.compile("\\b[a-z][a-z0-9+.-]*://\\S+");
    private static final Pattern COMMAND = Pattern.compile(
            "/iris(?:\\s|$).*?(?=(?:\\s+(?:to|for|from|when|or)\\s+)|(?:\\s+(?:first|again)\\b)|[.;,)\\n]|$)"
                    + "|/execute(?:\\s|$)[^.;,)\\n]*"
    );
    private static final Pattern IDENTIFIER = Pattern.compile(
            "\\b[a-z][a-z0-9_]*:[a-z0-9_./{}-]+\\b"
                    + "|(?:\\*/)?(?:plugins|world|objects|dimensions|structures|jigsaw-pieces|jigsaw-pools|packs|dump|config|assets|data)(?:/[A-Za-z0-9_*{}.-]*[A-Za-z0-9_*{}-])+/?"
                    + "|\\b[A-Za-z0-9_*{}.-]+\\.(?:json|toml|ya?ml|jar|nbt|iob|properties|log|zip)\\b"
    );
    private static final Pattern PROPER_NAME = Pattern.compile(
            "\\b(?:IrisDimensions|Iris|Minecraft|CraftBukkit|Bukkit|Paper|Folia|Fabric|NeoForge|Forge|Modrinth|GitHub|VSCode|Brigadier|Mantle|MiniMessage|Adventure|NMS|NBT|JSON|TOML|YAML|GUI|HUD|TPS|MSPT|FPS|CPU|GPU|RAM|JAR|API|UUID|BUD|ETA|HD|LQ|ms)\\b"
    );
    private static final List<String> IRIS_PRODUCT_NAMES = List.of(
            "Iris Vision",
            "Object Studio",
            "Noise Explorer",
            "Volmit Software",
            "WorldEdit",
            "ResourceManager",
            "GoldenHash",
            "IGenData",
            "TectonicPlates"
    );
    private static final Pattern MARKER_DEBRIS = Pattern.compile("(?:⟬|⟭|\\b(?:XQ|QZ)[A-Z0-9_]*\\b|[\\uE000-\\uF8FF])");
    private static final Pattern MOJIBAKE = Pattern.compile("(?:Ã[©¨ªº§³´¼¶¢£¥]|Â[©®°±·«» ]|â(?:€|€™|€œ|€|€“|€”|€¦)|ðŸ)");
    private static final Pattern CONTROL_CHARACTER = Pattern.compile("[\\p{Cc}&&[^\\n\\r\\t]]");
    private static final Pattern WORD = Pattern.compile(
            "[\\p{L}\\p{N}_]+"
    );
    private static final List<Pattern> REQUIRED_LITERAL_PATTERNS = List.of(
            MINI_MESSAGE_TAG,
            URL,
            COMMAND,
            IDENTIFIER,
            PROPER_NAME
    );
    private static final Map<String, Pattern> FORBIDDEN_TRANSLATION_ARTIFACTS = Map.ofEntries(
            Map.entry("es_ES", Pattern.compile("(?iu)\\b(?:Pónganse|sdatapackImports|TectonicPlates Conde|Iris World Director|FED 7)\\b")),
            Map.entry("fr_FR", Pattern.compile("(?iu)\\b(?:sentinelle|groupe électrogène|Iris Directeur mondial|FED 7)\\b")),
            Map.entry("he_IL", Pattern.compile("וניל")),
            Map.entry("it_IT", Pattern.compile("(?iu)\\bMonolocale\\b")),
            Map.entry("ja-JP", Pattern.compile("お問い合わせ|返品について|データパックの摂取|構成されたdatapackの輸入|§a通信|§7ログイン|包装次元|バリアフリー Iris|第一次世界|サイトマップ|コンタクトサポート|新着情報|生物医学|プレジェント|パユース|ドーワン|フィードバック|簡体中文|ジャグジー")),
            Map.entry("ko_KR", Pattern.compile("회사연혁|사이트맵|뚱 베어|페이스 북|스페인 사람|이름 \\*|관련 기사|지원하다|이 모수|내 계정|세계 가족|스타트 낙하|견적 요청|포장 차원|세계 시장|제품\\s*정보|기타\\s*제품|₢")),
            Map.entry("lt_LT", Pattern.compile("(?iu)\\b(?:vanilė|vanilės)\\b")),
            Map.entry("fi_FI", Pattern.compile("(?iu)\\bYksiö\\b")),
            Map.entry("nl_NL", Pattern.compile("(?iu)\\b(?:StudioName|Vanille)\\b")),
            Map.entry("pl_PL", Pattern.compile("(?iu)\\bwanili[\\p{L}]*\\b")),
            Map.entry("ru_RU", Pattern.compile("(?iu)\\bванил[\\p{L}]*\\b")),
            Map.entry("tr_TR", Pattern.compile("(?iu)Studio\\s+Stüdyo|\\bvanilya\\b")),
            Map.entry("zh_CN", Pattern.compile("香草|虹膜|艾里斯|爱丽丝|地幔|包装|发电机|装入|卸货|包子|快跑 /iris")),
            Map.entry("zh_TW", Pattern.compile("香草|虹膜|艾里斯|愛麗絲|地幔|包裝|包装|發電機|发电机|裝入|装入|卸貨|卸货|解除安裝|包子|快跑 /iris"))
    );
    private static final String STRUCTURAL_CHARACTERS = "%\\[]{}<>\n";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private File dataFolder;

    @Before
    public void setUp() throws Exception {
        dataFolder = temporaryFolder.newFolder();
        assertTrue(IrisLanguage.reload(dataFolder, "en_US"));
        for (String locale : VolmitLocales.nonEnglish()) {
            Path target = dataFolder.toPath().resolve("languages/" + locale + ".toml");
            Files.createDirectories(target.getParent());
            Files.copy(ProjectPaths.moduleFile("src/main/resources/languages").resolve(locale + ".toml"), target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @After
    public void tearDown() {
        IrisLanguage.shutdown();
        assertTrue(IrisLanguage.reload(dataFolder, "en_US"));
    }

    private LocaleOverlay loadSourceOverlay(String locale) throws Exception {
        Path source = ProjectPaths.moduleFile("src/main/resources/languages").resolve(locale + ".toml");
        return IrisLanguage.parseDownloadedOverlay(source.toString(), locale, Files.readString(source));
    }

    @Test
    public void languageCatalogSelectsKnownKeysWithoutDiscardingValidTranslations() {
        String raw = """
                "iris.command.unknown" = "Unbekannter Iris-Befehl"
                "iris.modded.help.entry.command.version" = "Version anzeigen"
                """;
        LocaleOverlay downloaded = IrisLanguage.parseDownloadedOverlay("download", "de_DE", raw);
        assertEquals(Set.of(IrisMessages.COMMAND_UNKNOWN.id()), downloaded.values().keySet());
        assertTrue(LocalizationValidator.validate(IrisLanguage.catalog(), List.of(downloaded)).errors().isEmpty());
        LocaleOverlay local = IrisLanguage.parseOverlay("language", "de_DE", raw);
        assertEquals(downloaded.values(), local.values());
        assertTrue(LocalizationValidator.validate(IrisLanguage.catalog(), List.of(local)).errors().isEmpty());
        assertEquals("A pregeneration task is already running. Stop it first with /iris pregen stop.",
                IrisLanguage.plain(IrisMessages.PREGEN_ALREADY_RUNNING));
    }

    @Test
    public void invalidPlaceholdersAreExcludedWithoutDiscardingValidMessages() {
        LocaleOverlay downloaded = IrisLanguage.parseDownloadedOverlay("download", "de_DE", """
                "iris.command.permission_denied" = "Keine Erlaubnis"
                "iris.command.unknown" = "Unbekannter Iris-Befehl"
                """);
        assertEquals(Set.of(IrisMessages.COMMAND_UNKNOWN.id()), downloaded.values().keySet());
        assertTrue(LocalizationValidator.validate(IrisLanguage.catalog(), List.of(downloaded)).errors().isEmpty());
    }

    @Test
    public void missingLanguageUsesBuiltInEnglish() throws Exception {
        Files.delete(dataFolder.toPath().resolve("languages/de_DE.toml"));
        assertTrue(IrisLanguage.reload(dataFolder, "de_DE"));
        assertEquals("Unknown Iris command", IrisLanguage.plain(IrisMessages.COMMAND_UNKNOWN));
    }

    @Test
    public void failedServerSelectionRetainsSettingsAndRenderedLanguage() throws Exception {
        IrisSettings previous = IrisSettings.settings;
        IrisSettings current = new IrisSettings();
        IrisSettings.settings = current;
        Files.createDirectory(dataFolder.toPath().resolve("iris.json"));
        IrisLanguage.start();
        try {
            assertThrows(ExecutionException.class,
                    () -> IrisLanguage.selections().selectDefault("de_DE").get(5, TimeUnit.SECONDS));
            assertEquals("en_US", current.getGeneral().getLanguage());
            assertEquals("en_US", IrisLanguage.activeLocale());
            assertEquals("Unknown Iris command", IrisLanguage.plain(IrisMessages.COMMAND_UNKNOWN));
        } finally {
            IrisLanguage.shutdown();
            IrisSettings.settings = previous;
        }
    }

    @Test
    public void personalSelectionDoesNotChangeAnotherPlayerOrTheServerDefault() throws Exception {
        UUID germanPlayer = UUID.randomUUID();
        UUID otherPlayer = UUID.randomUUID();
        IrisLanguage.start();
        try {
            IrisLanguage.selections().selectPlayer(germanPlayer, "de_DE").get(5, TimeUnit.SECONDS);
            String german = LanguageAudience.call(germanPlayer, () -> IrisLanguage.plain(IrisMessages.COMMAND_UNKNOWN));
            String other = LanguageAudience.call(otherPlayer, () -> IrisLanguage.plain(IrisMessages.COMMAND_UNKNOWN));
            assertFalse("Unknown Iris command".equals(german));
            assertEquals("Unknown Iris command", other);
            assertEquals("en_US", IrisLanguage.activeLocale());
            IrisLanguage.selections().clearPlayer(germanPlayer).get(5, TimeUnit.SECONDS);
            assertEquals("Unknown Iris command", LanguageAudience.call(germanPlayer, () -> IrisLanguage.plain(IrisMessages.COMMAND_UNKNOWN)));
        } finally {
            IrisLanguage.shutdown();
        }
    }

    @Test
    public void hotloadRefreshesDefaultAndPersonalLanguagesWithoutChangingSelections() throws Exception {
        IrisSettings previous = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        UUID frenchPlayer = UUID.randomUUID();
        IrisLanguage.start();
        try {
            IrisLanguage.selections().selectDefault("de_DE").get(5, TimeUnit.SECONDS);
            IrisLanguage.selections().selectPlayer(frenchPlayer, "fr_FR").get(5, TimeUnit.SECONDS);
            String german = "\"iris.command.unknown\" = \"Deutsch aktualisiert\"\n";
            File germanFile = writeLanguage("de_DE", german);
            assertTrue(IrisLanguage.reloadOverride(germanFile, german));
            assertEquals("Deutsch aktualisiert", IrisLanguage.plain(IrisMessages.COMMAND_UNKNOWN));

            String french = "\"iris.command.unknown\" = \"Francais actualise\"\n";
            File frenchFile = writeLanguage("fr_FR", french);
            assertTrue(IrisLanguage.reloadOverride(frenchFile, french));

            assertEquals("Francais actualise", LanguageAudience.call(frenchPlayer,
                    () -> IrisLanguage.plain(IrisMessages.COMMAND_UNKNOWN)));
            assertEquals("Deutsch aktualisiert", IrisLanguage.plain(IrisMessages.COMMAND_UNKNOWN));
            assertEquals("fr_FR", IrisLanguage.selections().playerLocale(frenchPlayer).orElseThrow());
            assertEquals("de_DE", IrisLanguage.selections().defaultLocale());
            assertEquals("de_DE", IrisSettings.settings.getGeneral().getLanguage());
        } finally {
            IrisLanguage.shutdown();
            IrisSettings.settings = previous;
        }
    }

    @Test
    public void generatedEnglishLanguageRoundTripsTheEntireCatalog() throws Exception {
        Path english = dataFolder.toPath().resolve("languages/en_US.toml");
        assertTrue(Files.isRegularFile(english));
        LocaleOverlay overlay = IrisLanguage.parseDownloadedOverlay(english.toString(), "en_US", Files.readString(english));
        assertEquals(IrisLanguage.catalog().ids(), overlay.values().keySet());
        for (MessageKey key : IrisLanguage.catalog().keys()) {
            assertEquals(key.id(), key.englishValue(), overlay.value(key.id()));
        }
        String rendered = IrisLanguage.plain(
                IrisMessages.COMMAND_PERMISSION_DENIED,
                MessageArgument.untrusted("permission", "iris.all")
        );

        assertEquals("You lack the permission 'iris.all'", rendered);
        assertEquals("en_US", IrisLanguage.activeLocale());
    }

    @Test
    public void guideDescribesEveryRequiredAndOptionalCatalogPlaceholder() {
        Map<String, String> descriptions = IrisLanguageGuide.variableDescriptions();
        for (MessageKey key : IrisLanguage.catalog().keys()) {
            for (String placeholder : key.placeholders()) {
                assertPlaceholderDescription(key.id(), placeholder, descriptions.get(placeholder));
            }
            for (String placeholder : key.optionalPlaceholders()) {
                assertPlaceholderDescription(key.id(), placeholder, descriptions.get(placeholder));
            }
        }
        for (Map.Entry<String, String> entry : descriptions.entrySet()) {
            assertPlaceholderDescription("language guide", entry.getKey(), entry.getValue());
        }
    }

    @Test
    public void generatedEnglishGuideIncludesMeaningsAndPrefixExamples() throws Exception {
        String header = Files.readString(dataFolder.toPath().resolve("languages/en_US.toml"));
        assertTrue(header.startsWith("# === Iris language guide ===\n"));
        for (Map.Entry<String, String> entry : IrisLanguageGuide.variableDescriptions().entrySet()) {
            assertTrue(entry.getKey(), header.contains("#   {" + entry.getKey() + "}  " + entry.getValue()));
        }
        assertTrue(header.contains("set_debug = \"&6[Worlds]&r &aSet debug to: {to}\""));
        assertTrue(header.contains("&6 gold"));
        assertTrue(header.contains("&l bold"));
        assertTrue(header.contains("&r reset"));
        assertTrue(header.contains("{PC}, {SC} and {TC} are not Iris theme variables"));
    }

    @Test
    public void everyTranslatedGuideDescribesItsVariablesAndMatchesThePackagedGuide() throws Exception {
        Pattern definition = Pattern.compile("(?m)^#   \\{([A-Za-z][A-Za-z0-9_]*)}  (\\S[^\\r\\n]*)$");
        for (String locale : VolmitLocales.nonEnglish()) {
            String source = Files.readString(ProjectPaths.moduleFile("src/main/resources/languages/" + locale + ".toml"));
            int bodyStart = source.indexOf("\n[") + 1;
            assertTrue(locale, bodyStart > 0);
            String header = source.substring(0, bodyStart);
            String body = source.substring(bodyStart);
            Map<String, String> descriptions = new LinkedHashMap<>();
            Matcher defined = definition.matcher(header);
            while (defined.find()) {
                assertFalse(locale + ": duplicate " + defined.group(1), descriptions.containsKey(defined.group(1)));
                descriptions.put(defined.group(1), defined.group(2));
            }
            Matcher used = PLACEHOLDER.matcher(body);
            while (used.find()) {
                String placeholder = used.group().substring(1, used.group().length() - 1);
                assertPlaceholderDescription(locale, placeholder, descriptions.get(placeholder));
            }
            assertEquals(locale, IrisLanguageGuide.variableDescriptions().keySet(), descriptions.keySet());
            assertEquals(locale, header.stripTrailing(), IrisLanguageGuide.header(locale).stripTrailing());
            assertTrue(locale, header.contains("# === End Iris language guide ==="));
        }
    }

    @Test
    public void refreshingAnExistingGuidePreservesCustomMessagesAndOtherComments() throws Exception {
        String note = "# Operator's own note\r\n";
        String oldGuide = "# === Iris language guide ===\r\n# Iris — en_US\r\n"
                + "# === Variables ===\r\n#   {permission}  \r\n#   {world}  \r\n"
                + "# === End Iris language guide ===\r\n";
        String body = "\r\n# Keep this explanation\r\n\"iris.command.unknown\" = 'My edited message'\r\n"
                + "[future]\r\nenabled = true\r\nitems = [1, 2]\r\n";
        File english = writeLanguage("en_US", note + oldGuide + body);

        assertTrue(IrisLanguage.reload(dataFolder, "en_US"));

        String refreshed = Files.readString(english.toPath());
        assertEquals(note + IrisLanguageGuide.englishHeader("en_US") + body, refreshed);
        assertEquals("My edited message", IrisLanguage.plain(IrisMessages.COMMAND_UNKNOWN));
        FileTime unchangedTime = FileTime.fromMillis(123_456L);
        Files.setLastModifiedTime(english.toPath(), unchangedTime);
        assertTrue(IrisLanguage.reload(dataFolder, "en_US"));
        assertEquals(refreshed, Files.readString(english.toPath()));
        assertEquals(unchangedTime, Files.getLastModifiedTime(english.toPath()));
    }

    @Test
    public void refreshingATranslatedGuidePreservesItsCustomMessages() throws Exception {
        String oldGuide = "# === Iris language guide ===\n# Iris - de_DE\n"
                + "# === Variablen ===\n#   {permission} {world}\n# === End Iris language guide ===\n";
        String body = "\n[iris.command]\nunknown = 'Eigener Text'\n";
        File german = writeLanguage("de_DE", oldGuide + body);

        assertTrue(IrisLanguage.reload(dataFolder, "de_DE"));

        assertEquals(IrisLanguageGuide.header("de_DE") + body, Files.readString(german.toPath()));
        assertEquals("Eigener Text", IrisLanguage.plain(IrisMessages.COMMAND_UNKNOWN));
    }

    @Test
    public void documentedPrefixExampleUsesItsExistingDebugVariable() throws Exception {
        writeLanguage("en_US", "[iris.bukkit.commandiris]\nset_debug = \"&6[Worlds]&r &aSet debug to: {to}\"\n");
        assertTrue(IrisLanguage.reload(dataFolder, "en_US"));
        MessageKey debug = IrisLanguage.catalog().require("iris.bukkit.commandiris.set_debug");

        assertEquals("\u00a76[Worlds]\u00a7r \u00a7aSet debug to: true",
                IrisLanguage.text(debug, MessageArgument.trusted("to", true)));
        assertEquals("[Worlds] Set debug to: false",
                IrisLanguage.plain(debug, MessageArgument.trusted("to", false)));
    }

    @Test
    public void unreadableEnglishGuideDoesNotBlockSelectingAnotherLanguage() throws Exception {
        Files.write(dataFolder.toPath().resolve("languages/en_US.toml"), new byte[]{(byte) 0xc3, 0x28});
        UUID player = UUID.randomUUID();
        IrisLanguage.start();
        try {
            IrisLanguage.selections().selectPlayer(player, "de_DE").get(5, TimeUnit.SECONDS);

            assertEquals("de_DE", IrisLanguage.selections().playerLocale(player).orElseThrow());
            assertFalse("Unknown Iris command".equals(LanguageAudience.call(player,
                    () -> IrisLanguage.plain(IrisMessages.COMMAND_UNKNOWN))));
        } finally {
            IrisLanguage.shutdown();
        }
    }

    @Test
    public void appliesLanguageFileAndFallsBackPerKey() throws Exception {
        writeLanguage("de_DE", """
                "iris.command.permission_denied" = "Fehlende Berechtigung: {permission}"
                """);

        assertTrue(IrisLanguage.reload(dataFolder, "de_DE"));
        assertEquals(
                "Fehlende Berechtigung: iris.all",
                IrisLanguage.plain(
                        IrisMessages.COMMAND_PERMISSION_DENIED,
                        MessageArgument.untrusted("permission", "iris.all")
                )
        );
        assertEquals("Unknown Iris command", IrisLanguage.plain(IrisMessages.COMMAND_UNKNOWN));
    }

    @Test
    public void downloadableLocalesMatchSharedManifestAndCoverEntireCatalog() throws Exception {
        assertEquals(17, VolmitLocales.nonEnglish().size());
        for (String locale : VolmitLocales.nonEnglish()) {
            LocaleOverlay overlay = loadSourceOverlay(locale);
            assertEquals(locale, overlay.locale());
            assertEquals(IrisLanguage.catalog().ids(), overlay.values().keySet());

            LocalizationValidationResult validation = LocalizationValidator.validate(
                    IrisLanguage.catalog(),
                    List.of(overlay)
            );
            assertTrue(locale + " errors: " + validation.errors(), validation.errors().isEmpty());
            assertTrue(locale + " warnings: " + validation.warnings(), validation.warnings().isEmpty());

            int translated = 0;
            for (MessageKey key : IrisLanguage.catalog().keys()) {
                if (!key.englishValue().equals(overlay.value(key.id()))) {
                    translated++;
                }
                assertValueIntegrity(locale, key.id(), key.englishValue(), overlay.value(key.id()));
            }
            assertTrue(locale + " contains too many English placeholder values",
                    translated * 10 >= IrisLanguage.catalog().keys().size() * 7);
        }
    }

    @Test
    public void sourceLocalesExactlyMatchNonEnglishManifest() throws Exception {
        Set<String> expected = VolmitLocales.nonEnglish().stream()
                .map(locale -> locale + ".toml")
                .collect(Collectors.toUnmodifiableSet());

        assertEquals(expected, resourceFiles("languages"));
        assertFalse(expected.contains(VolmitLocales.ENGLISH + ".toml"));
    }

    @Test
    public void downloadableLocalesPreserveReservedWorldNames() throws Exception {
        for (String locale : VolmitLocales.nonEnglish()) {
            LocaleOverlay overlay = loadSourceOverlay(locale);
            TextValue irisName = (TextValue) overlay.value(
                    BukkitCommandMessagesExtended.COMMAND_IRIS_YOU_CANNOT_USE_WORLD_NAME_IRIS_CREATING_WORLDS_AS_IRIS.id()
            );
            TextValue benchmarkName = (TextValue) overlay.value(
                    BukkitCommandMessagesExtended.COMMAND_IRIS_YOU_CANNOT_USE_WORLD_NAME_BENCHMARK_CREATING_WORLDS_AS_IRIS.id()
            );

            assertTrue(locale + " must preserve the reserved iris world name", irisName.template().contains("\"iris\""));
            assertTrue(locale + " must preserve the reserved benchmark world name", benchmarkName.template().contains("\"benchmark\""));
        }
    }

    @Test
    public void installedLocaleLoadsDirectly() {
        assertTrue(IrisLanguage.reload(dataFolder, "de_DE"));
        assertEquals("de_DE", IrisLanguage.activeLocale());
        assertFalse("Unknown Iris command".equals(IrisLanguage.plain(IrisMessages.COMMAND_UNKNOWN)));
    }

    @Test
    public void invalidEntriesUseEnglishWhileValidAndUnknownFileContentIsPreserved() throws Exception {
        writeLanguage("de_DE", """
                "iris.command.permission_denied" = "Erlaubnis {permission} fehlt"
                """);
        assertTrue(IrisLanguage.reload(dataFolder, "de_DE"));

        String raw = """
                "iris.command.permission_denied" = "Erlaubnis fehlt"
                "iris.command.unknown" = "Unbekannter Iris-Befehl"
                "iris.command.not_real" = "Unbekannt"
                """;
        File language = writeLanguage("de_DE", raw);

        assertTrue(IrisLanguage.reload(dataFolder, "de_DE"));
        assertEquals(
                "You lack the permission 'iris.all'",
                IrisLanguage.plain(
                        IrisMessages.COMMAND_PERMISSION_DENIED,
                        MessageArgument.untrusted("permission", "iris.all")
                )
        );
        assertEquals("Unbekannter Iris-Befehl", IrisLanguage.plain(IrisMessages.COMMAND_UNKNOWN));
        assertEquals(raw, Files.readString(language.toPath()));
    }

    @Test
    public void unreadableTomlUsesEnglishWithoutReplacingTheFile() throws Exception {
        assertTrue(IrisLanguage.reload(dataFolder, "de_DE"));
        String raw = "[unterminated";
        File language = writeLanguage("de_DE", raw);

        assertTrue(IrisLanguage.reload(dataFolder, "de_DE"));
        assertEquals("Unknown Iris command", IrisLanguage.plain(IrisMessages.COMMAND_UNKNOWN));
        assertEquals(raw, Files.readString(language.toPath()));
    }

    @Test
    public void englishEditsSurviveReloadAndMissingEnglishIsCreatedForAnotherLocale() throws Exception {
        String edited = "\"iris.command.unknown\" = \"My Iris command\"\n";
        File english = writeLanguage("en_US", edited);
        assertTrue(IrisLanguage.reload(dataFolder, "en_US"));
        assertEquals("My Iris command", IrisLanguage.plain(IrisMessages.COMMAND_UNKNOWN));
        assertEquals(edited, Files.readString(english.toPath()));

        Files.delete(english.toPath());
        assertTrue(IrisLanguage.reload(dataFolder, "de_DE"));
        assertTrue(Files.isRegularFile(english.toPath()));
        assertEquals("de_DE", IrisLanguage.activeLocale());
    }

    @Test
    public void rejectedCandidateLocaleLeavesPreviousSettingsAndLanguageActive() {
        IrisSettings previous = IrisSettings.settings;
        IrisSettings live = new IrisSettings();
        live.getGeneral().setLanguage("en_US");
        IrisSettings.settings = live;
        try {
            boolean applied = IrisSettings.applyHotloadSnapshot(
                    "{\"general\":{\"language\":\"../invalid\"}}",
                    IrisLanguage::reload
            );

            assertFalse(applied);
            assertSame(live, IrisSettings.settings);
            assertEquals("en_US", IrisLanguage.activeLocale());
        } finally {
            IrisSettings.settings = previous;
        }
    }

    @Test
    public void untrustedArgumentsCannotInjectLegacyOrMiniMessageFormatting() throws Exception {
        writeLanguage("de_DE", """
                "iris.command.permission_denied" = "&aWert: {permission}"
                """);
        assertTrue(IrisLanguage.reload(dataFolder, "de_DE"));

        String rendered = IrisLanguage.text(
                IrisMessages.COMMAND_PERMISSION_DENIED,
                MessageArgument.untrusted("permission", "&c<red>Bad\u00a74Name")
        );

        assertTrue(rendered.startsWith("\u00a7aWert: "));
        assertTrue(rendered.endsWith("＆c‹red›BadName"));
        assertFalse(rendered.contains("\u00a7c"));
        assertFalse(rendered.contains("\u00a74"));
    }

    @Test
    public void argumentValuesCannotCascadeIntoLaterPlaceholderSentinels() {
        String rendered = IrisLanguage.plain(
                IrisMessages.COMMAND_RELOAD_FAILED,
                MessageArgument.untrusted("locale", "\uE0001\uE001"),
                MessageArgument.untrusted("activeLocale", "en_US")
        );

        assertEquals(
                "Settings were reloaded, but locale \uE0001\uE001 was rejected; continuing with en_US.",
                rendered
        );
    }

    @Test
    public void lookupsUseImmutableSnapshotWithoutReadingTheLanguageFileAgain() throws Exception {
        File language = writeLanguage("de_DE", """
                "iris.command.unknown" = "Unbekannter Iris-Befehl"
                """);
        assertTrue(IrisLanguage.reload(dataFolder, "de_DE"));
        Files.delete(language.toPath());

        assertEquals("Unbekannter Iris-Befehl", IrisLanguage.plain(IrisMessages.COMMAND_UNKNOWN));
    }

    @Test
    public void appliesLineAndPluralOverlayShapes() throws Exception {
        writeLanguage("de_DE", """
                "iris.bukkit.runtime.commanddeveloper.stage_world_generation_update_warning" = [
                      "Sicherung erstellen.",
                      "Vorhandene Chunks bleiben erhalten.",
                      "Neue Chunks verwenden das neue Paket.",
                      "Die Grenze bleibt gespeichert.",
                      "Den vollständigen Verlauf sichern.",
                      "Neustart bestätigen.",
                      "Welt {world}, Paket {pack}"
                ]
                ["iris.bukkit.runtime.commandpack.more_warning_s"]
                one = "Noch {count} Warnung."
                other = "Noch {count} Warnungen."
                """);

        assertTrue(IrisLanguage.reload(dataFolder, "de_DE"));
        assertEquals(
                "Sicherung erstellen.\n"
                        + "Vorhandene Chunks bleiben erhalten.\n"
                        + "Neue Chunks verwenden das neue Paket.\n"
                        + "Die Grenze bleibt gespeichert.\n"
                        + "Den vollständigen Verlauf sichern.\n"
                        + "Neustart bestätigen.\n"
                        + "Welt world, Paket pack",
                IrisLanguage.plain(
                        BukkitRuntimeMessages.COMMAND_DEVELOPER_UPDATE_WORLD_WARNING,
                        MessageArgument.untrusted("world", "world"),
                        MessageArgument.untrusted("pack", "pack")
                )
        );
        assertEquals(
                "Noch 1 Warnung.",
                IrisLanguage.plain(
                        BukkitRuntimeMessages.COMMAND_PACK_MORE_WARNING_S,
                        MessageArgument.trusted("count", 1)
                )
        );
        assertEquals(
                "Noch 3 Warnungen.",
                IrisLanguage.plain(
                        BukkitRuntimeMessages.COMMAND_PACK_MORE_WARNING_S,
                        MessageArgument.trusted("count", 3)
                )
        );
    }

    private File writeLanguage(String locale, String toml) throws Exception {
        File file = new File(dataFolder, "languages/" + locale + ".toml");
        Files.createDirectories(file.toPath().getParent());
        Files.writeString(file.toPath(), toml, StandardCharsets.UTF_8);
        return file;
    }

    private void assertPlaceholderDescription(String key, String placeholder, String description) {
        String context = key + " placeholder {" + placeholder + "}";
        assertNotNull(context + " needs a definition", description);
        assertFalse(context + " definition must not be blank", description.isBlank());
        assertFalse(context + " definition must explain the variable", placeholder.equalsIgnoreCase(description.strip()));
        assertFalse(context + " definition must explain the variable", ("{" + placeholder + "}").equals(description.strip()));
    }

    private void assertValueIntegrity(String locale, String key, MessageValue english, MessageValue translated) {
        if (english instanceof TextValue englishText && translated instanceof TextValue translatedText) {
            assertTemplateIntegrity(locale, key, englishText.template(), translatedText.template());
            return;
        }
        if (english instanceof LinesValue englishLines && translated instanceof LinesValue translatedLines) {
            assertEquals(locale + ": " + key + " line count", englishLines.lines().size(), translatedLines.lines().size());
            for (int index = 0; index < englishLines.lines().size(); index++) {
                assertTemplateIntegrity(
                        locale,
                        key + "[" + index + "]",
                        englishLines.lines().get(index),
                        translatedLines.lines().get(index)
                );
            }
            return;
        }
        if (english instanceof PluralValue englishPlural && translated instanceof PluralValue translatedPlural) {
            assertEquals(locale + ": " + key + " plural forms", englishPlural.forms().keySet(), translatedPlural.forms().keySet());
            for (String form : englishPlural.forms().keySet()) {
                assertTemplateIntegrity(
                        locale,
                        key + "." + form,
                        englishPlural.forms().get(form),
                        translatedPlural.forms().get(form)
                );
            }
            return;
        }
        throw new AssertionError(locale + ": " + key + " has mismatched message value types");
    }

    private void assertTemplateIntegrity(String locale, String key, String english, String translated) {
        String context = locale + ": " + key;
        assertEquals(context + " color codes", matches(COLOR_CODE, english), matches(COLOR_CODE, translated));
        for (int index = 0; index < STRUCTURAL_CHARACTERS.length(); index++) {
            char character = STRUCTURAL_CHARACTERS.charAt(index);
            assertEquals(
                    context + " structural character " + character,
                    countCharacter(english, character),
                    countCharacter(translated, character)
            );
        }
        for (Pattern pattern : REQUIRED_LITERAL_PATTERNS) {
            Map<String, Integer> required = frequencies(matches(pattern, english));
            for (Map.Entry<String, Integer> entry : required.entrySet()) {
                assertTrue(
                        context + " lost literal " + entry.getKey(),
                        countLiteral(translated, entry.getKey()) >= entry.getValue()
                );
            }
        }
        assertFalse(context + " contains translation marker debris", MARKER_DEBRIS.matcher(translated).find());
        assertFalse(context + " contains an encoded ampersand", translated.contains("&amp;"));
        assertFalse(context + " contains a replacement character", translated.contains("�"));
        assertFalse(context + " contains mojibake", MOJIBAKE.matcher(translated).find());
        assertFalse(context + " contains a control character", CONTROL_CHARACTER.matcher(translated).find());
        Pattern forbiddenArtifacts = FORBIDDEN_TRANSLATION_ARTIFACTS.get(locale);
        if (forbiddenArtifacts != null) {
            assertFalse(context + " contains a known translation artifact", forbiddenArtifacts.matcher(translated).find());
        }
        assertTechnicalTermIntegrity(locale, context, english, translated);
        assertTrue(
                context + " is pathologically longer than English",
                translated.length() <= Math.max(120, english.length() * 4 + 60)
        );
        assertFalse(
                context + " contains pathological repetition",
                hasPathologicalRepetition(translated) && !hasPathologicalRepetition(english)
        );
    }

    private void assertTechnicalTermIntegrity(String locale, String context, String english, String translated) {
        if (!locale.equals("es_ES") && !locale.equals("fr_FR")) {
            return;
        }

        String source = PLACEHOLDER.matcher(english).replaceAll("");
        String target = PLACEHOLDER.matcher(translated).replaceAll("");
        for (String productName : IRIS_PRODUCT_NAMES) {
            if (source.contains(productName)) {
                assertTrue(context + " must preserve product name " + productName, target.contains(productName));
            }
        }
        if (containsWord(source, "chunks?")) {
            assertTrue(context + " must preserve the Minecraft term chunk", containsWord(target, "chunks?"));
            assertFalse(context + " mistranslates chunk", artifact(locale, target, "pedazos?|trozos?|porciones?|tontos?|idiotas?|gorros?", "morceaux?|choux?"));
        }
        if (containsWord(source, "mantle")) {
            assertTrue(context + " must preserve Mantle", target.contains("Mantle"));
            assertFalse(context + " mistranslates Mantle", artifact(locale, target, "mantos?|manteles?", "manteaux?"));
        }
        String sourceWithoutDataPack = source.replaceAll("(?iu)\\bdata\\s+packs?\\b", "datapack");
        if (containsWord(sourceWithoutDataPack, "packs?")) {
            assertTrue(context + " must preserve the Iris term pack", containsWord(target, "packs?"));
            assertFalse(context + " mistranslates pack", artifact(locale, target, "paquetes?|embalajes?|envases?", "paquets?|boîtes?|boites?|emballages?|colis"));
        }
        if (containsWord(source, "vanilla")) {
            assertTrue(context + " must preserve the Minecraft term vanilla", containsWord(target, "vanilla"));
            assertFalse(context + " mistranslates vanilla", artifact(locale, target, "vainilla", "vanille"));
        }
        if (containsWord(source, "studio")) {
            assertTrue(context + " must preserve Studio", containsWord(target, "Studio"));
        }
        if (containsWord(source, "pastes?")) {
            assertFalse(context + " mistranslates paste", artifact(locale, target, "pastas?|sabores?", "pâtes?|saveurs?"));
        }
        if (containsWord(source, "unloads?|unloaded|unloading")) {
            assertFalse(context + " confuses unload with download", artifact(locale, target, "descarg(?:ar|a|ado|ando)", "télécharg(?:er|é|ement)"));
        }
        if (containsWord(source, "downloads?|downloaded|downloading")) {
            assertFalse(context + " confuses download with unload", artifact(locale, target, "retir(?:ar|ado|ando).{0,20}memoria", "décharg(?:er|é|ement)"));
        }
        if (containsWord(source, "spawns?|spawned|spawning")) {
            assertFalse(context + " mistranslates spawn", artifact(locale, target, "desov(?:ar|a|ado)|escup(?:ir|e|ido)", "fray(?:er|é|age)"));
        }
        if (containsWord(source, "benchmarks?|benchmarked|benchmarking")) {
            assertTrue(context + " must preserve benchmark", containsWord(target, "benchmarks?"));
        }
        if (locale.equals("fr_FR") && containsWord(source, "generators?")) {
            assertFalse(context + " uses the electrical sense of generator", containsWord(target, "groupes?\\s+électrogènes?"));
        }
        if (locale.equals("fr_FR") && containsWord(source, "caves?")) {
            assertFalse(context + " uses the cellar sense of cave", containsWord(target, "caves?"));
        }
        if (containsWord(source, "saves?|saved|saving")) {
            assertFalse(context + " uses the rescue sense of save", artifact(locale, target, "salv(?:ar|a|ado|ando)", "sauv(?:er|é)"));
        }
    }

    private boolean artifact(String locale, String value, String spanish, String french) {
        return containsWord(value, locale.equals("es_ES") ? spanish : french);
    }

    private boolean containsWord(String value, String expression) {
        return Pattern.compile("(?iuU)\\b(?:" + expression + ")\\b").matcher(value).find();
    }

    private boolean hasPathologicalRepetition(String value) {
        String withoutPlaceholders = PLACEHOLDER.matcher(value).replaceAll("");
        List<String> words = new ArrayList<>();
        Matcher matcher = WORD.matcher(withoutPlaceholders);
        while (matcher.find()) {
            words.add(matcher.group().toLowerCase(Locale.ROOT));
        }
        for (int index = 0; index + 2 < words.size(); index++) {
            if (words.get(index).equals(words.get(index + 1))
                    && words.get(index).equals(words.get(index + 2))) {
                return true;
            }
        }
        for (int size = 2; size <= 4; size++) {
            for (int index = 0; index + size * 3 <= words.size(); index++) {
                if (words.subList(index, index + size).equals(words.subList(index + size, index + size * 2))
                        && words.subList(index, index + size).equals(words.subList(index + size * 2, index + size * 3))) {
                    return true;
                }
            }
        }
        return false;
    }

    private Set<String> resourceFiles(String directory) throws Exception {
        URL resource = IrisLanguageTest.class.getClassLoader().getResource(directory);
        assertNotNull("Missing resource directory: " + directory, resource);
        assertEquals("file", resource.getProtocol());
        try (Stream<Path> paths = Files.list(Path.of(resource.toURI()))) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    private List<String> matches(Pattern pattern, String value) {
        List<String> matches = new ArrayList<>();
        Matcher matcher = pattern.matcher(value);
        while (matcher.find()) {
            matches.add(matcher.group());
        }
        return matches;
    }

    private Map<String, Integer> frequencies(List<String> values) {
        Map<String, Integer> frequencies = new LinkedHashMap<>();
        for (String value : values) {
            frequencies.merge(value, 1, Integer::sum);
        }
        return frequencies;
    }

    private int countCharacter(String value, char character) {
        int count = 0;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == character) {
                count++;
            }
        }
        return count;
    }

    private int countLiteral(String value, String literal) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(literal, index)) >= 0) {
            count++;
            index += literal.length();
        }
        return count;
    }
}
