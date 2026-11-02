package art.arcane.iris.localization;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class IrisLanguageGuideCacheTest {
    private static final String LOCALE = "de_DE";
    private static final String GUIDE = "# === Iris language guide ===\n# Deutsche Anleitung\n# === End Iris language guide ===\n";
    private static final String BODY = "\n[iris.command]\nunknown = 'Unbekannt'\n";

    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void unbundledGuideComesFromAnUpstreamCopyOnlyWhenItMatchesTheBuildIndex() throws Exception {
        Path cache = temporary.newFolder(".guides").toPath();
        try (URLClassLoader resources = indexOnly(GUIDE)) {
            assertNull(IrisLanguageGuide.header(LOCALE, cache, resources));
            assertFalse(IrisLanguageGuide.cacheUpstream(cache, LOCALE, GUIDE.replace("Deutsche", "Alte") + BODY, resources));
            assertNull(IrisLanguageGuide.header(LOCALE, cache, resources));
            assertTrue(IrisLanguageGuide.cacheUpstream(cache, LOCALE, GUIDE.replace("\n", "\r\n") + BODY, resources));
            assertEquals(GUIDE, IrisLanguageGuide.header(LOCALE, cache, resources));
        }
    }

    @Test
    public void refreshLeavesATranslatedGuideAloneWhenThisBuildsGuideIsUnavailable() throws Exception {
        File languages = temporary.newFolder("languages");
        File file = new File(languages, LOCALE + ".toml");
        String original = "# === Iris language guide ===\n# Eigene Anleitung\n# === End Iris language guide ===\n" + BODY;
        Files.writeString(file.toPath(), original, StandardCharsets.UTF_8);
        try (URLClassLoader resources = indexOnly(GUIDE)) {
            assertFalse(IrisLanguageGuide.refresh(file, LOCALE, resources));
            assertTrue(IrisLanguageGuide.fetchable(LOCALE, resources));
            assertEquals(original, Files.readString(file.toPath(), StandardCharsets.UTF_8));

            assertTrue(IrisLanguageGuide.cacheUpstream(IrisLanguageGuide.guideCache(file), LOCALE, GUIDE + BODY, resources));
            assertTrue(IrisLanguageGuide.refresh(file, LOCALE, resources));
            assertEquals(GUIDE + BODY, Files.readString(file.toPath(), StandardCharsets.UTF_8));
        }
    }

    @Test
    public void englishNeverNeedsADownload() throws Exception {
        try (URLClassLoader resources = new URLClassLoader(new URL[0], null)) {
            assertEquals(IrisLanguageGuide.englishHeader("en_US"), IrisLanguageGuide.header("en_US", null, resources));
            assertFalse(IrisLanguageGuide.fetchable("en_US", resources));
            assertFalse(IrisLanguageGuide.fetchable(LOCALE, resources));
        }
    }

    @Test
    public void extractedHeaderFollowsTheBuildRule() {
        assertEquals(GUIDE, IrisLanguageGuide.extractHeader("\n" + GUIDE + "   \n[iris]\nkey = 'value'\n# trailing\n").stripLeading());
        assertEquals("\n", IrisLanguageGuide.extractHeader("[iris]\nkey = 'value'\n"));
    }

    private URLClassLoader indexOnly(String guide) throws IOException {
        Path root = temporary.newFolder().toPath();
        Path index = root.resolve("iris-language-guides/index.properties");
        Files.createDirectories(index.getParent());
        Files.writeString(index, LOCALE + "=" + sha256(guide) + "\n", StandardCharsets.UTF_8);
        return new URLClassLoader(new URL[]{root.toUri().toURL()}, null);
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }
}
