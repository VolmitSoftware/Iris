package art.arcane.iris.localization;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IrisLanguageGuideTest {
    private static final String ENGLISH_GUIDE = """
            # Iris — en_US
            #
            # === File editing ===
            # plugins/Iris/languages/en_US.toml
            # Edit this file directly. Local changes are preserved; missing or invalid entries use built-in English.
            # /iris language server edit en_US
            #
            # === Prefix ===
            # Prefixes belong to individual messages or the platform display.
            #
            # === Formatting ===
            # Colors and styles: &0-&f, &k-&r.
            # RGB colors: &#RRGGBB, &xRRGGBB, &x&R&R&G&G&B&B, [RRGGBB].
            # Put a backslash before & or [ to display it literally.
            # Keep the placeholders already present in each message.
            #
            # === Variables ===
            # Keep variable names unchanged and use only the variables belonging to each message.
            #   {permission}
            #   {world}
            """;
    private static final String GERMAN_GUIDE = """
            # Iris - de_DE
            #
            # === Datei bearbeiten ===
            # languages/de_DE.toml
            # Diese Datei kann direkt bearbeitet werden. Lokale Änderungen bleiben erhalten; fehlende oder ungültige Einträge verwenden das eingebaute Englisch.
            # /iris language server edit de_DE
            #

            # === Präfix ===
            # Präfixe stehen in einzelnen Nachrichten oder werden von der jeweiligen Server- oder Client-Oberfläche ergänzt.
            #
            # === Formatierung ===
            # Farben: &0-&f, &k-&r, &#RRGGBB, &xRRGGBB, &x&R&R&G&G&B&B und [RRGGBB].
            # Ein vorangestellter Rückwärtsschrägstrich zeigt & oder [ wörtlich an.
            #

            # === Variablen ===
            # Variablennamen unverändert lassen und nur die Variablen der jeweiligen Nachricht verwenden.
            #   {permission} {world}
            """;

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void refreshesKnownEnglishProseAcrossLineEndingsAndCatalogSizes() throws Exception {
        String note = "# Local operator note\r\n";
        String body = "\r\n# Keep this explanation\r\n[iris.command]\r\nunknown = 'Custom answer'\r\n"
                + "[extra]\r\nenabled = true\r\n";
        File file = temporary.newFile("en_US.toml");
        Files.writeString(file.toPath(), note + ENGLISH_GUIDE.replace("\n", "\r\n") + body);

        IrisLanguageGuide.refresh(file, "en_US");

        assertArrayEquals((note + IrisLanguageGuide.englishHeader("en_US") + body).getBytes(StandardCharsets.UTF_8),
                Files.readAllBytes(file.toPath()));
    }

    @Test
    public void refreshesKnownGermanProsePreservingMessageBytes() throws Exception {
        String body = "\n# Eigene Erklärung\n[iris.command]\nunknown = 'Eigene Nachricht'\n";
        File file = temporary.newFile("de_DE.toml");
        Files.writeString(file.toPath(), GERMAN_GUIDE + body);

        IrisLanguageGuide.refresh(file, "de_DE");

        assertArrayEquals((IrisLanguageGuide.header("de_DE") + body).getBytes(StandardCharsets.UTF_8),
                Files.readAllBytes(file.toPath()));
    }

    @Test
    public void leavesCustomCommentsInsideAnUnmarkedGuideUntouched() throws Exception {
        String custom = ENGLISH_GUIDE.replace("# === Formatting ===", "# Operator's custom explanation\n# === Formatting ===")
                + "\n[iris.command]\nunknown = 'Custom answer'\n";
        File file = temporary.newFile("en_US.toml");
        Files.writeString(file.toPath(), custom);
        FileTime originalTime = FileTime.fromMillis(123_456L);
        Files.setLastModifiedTime(file.toPath(), originalTime);

        IrisLanguageGuide.refresh(file, "en_US");

        assertArrayEquals(custom.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(file.toPath()));
        assertEquals(originalTime, Files.getLastModifiedTime(file.toPath()));
    }

    @Test
    public void leavesChangedGuideProseAndUnknownUnmarkedGuidesUntouched() {
        String changed = GERMAN_GUIDE.replace("# languages/de_DE.toml", "# My chosen file location")
                + "\n[iris.command]\nunknown = 'Custom answer'\n";
        String unknown = "# Iris - custom\n# Custom instructions\n#   {world}\n[iris.command]\nunknown = 'Custom answer'\n";

        assertEquals(changed, IrisLanguageGuide.replaceHeader(changed, IrisLanguageGuide.englishHeader("de_DE"), "de_DE"));
        assertEquals(unknown, IrisLanguageGuide.replaceHeader(unknown, IrisLanguageGuide.englishHeader("custom"), "custom"));
    }

    @Test
    public void englishLocationIsRelativeToThePlatformDataFolder() {
        String header = IrisLanguageGuide.englishHeader("en_US");

        assertTrue(header.contains("# languages/en_US.toml\n"));
        assertFalse(header.contains("# plugins/Iris/languages/en_US.toml"));
    }
}
