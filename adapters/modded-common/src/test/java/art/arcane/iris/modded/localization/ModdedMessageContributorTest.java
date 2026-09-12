package art.arcane.iris.modded.localization;

import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.localization.IrisMessages;
import art.arcane.iris.testsupport.ProjectPaths;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.VolmitLocales;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ModdedMessageContributorTest {
    @Test
    public void loaderDiscoversModdedAndClientMessages() {
        for (MessageKey key : new ModdedMessageContributor().keys()) {
            assertNotNull(key.id(), IrisLanguage.catalog().key(key.id()));
            assertEquals(key.id(), key.englishValue(), IrisLanguage.catalog().require(key.id()).englishValue());
        }
        assertEquals("A pregeneration task is already running. Stop it first with /iris pregen stop.",
                IrisLanguage.plain(IrisMessages.PREGEN_ALREADY_RUNNING));
    }

    @Test
    public void completeLoaderCatalogStillMatchesEveryDownloadableLocale() throws Exception {
        Path sourceRoot = ProjectPaths.repositoryFile("core/src/main/resources/languages");
        for (String locale : VolmitLocales.nonEnglish()) {
            JsonObject document = JsonParser.parseString(Files.readString(sourceRoot.resolve(locale + ".json")))
                    .getAsJsonObject();
            assertEquals(locale, IrisLanguage.catalog().ids(), document.getAsJsonObject("messages").keySet());
        }
    }
}
