package art.arcane.iris.core.project;

import art.arcane.iris.core.IrisSettings;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

public class SchemaFilesTest {
    private static final String SCHEMA_TEXT = "{\"type\": \"object\"}";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private SchemaTestPlatform platform;
    private IrisSettings previousSettings;

    @Before
    public void bindPlatform() throws Exception {
        platform = SchemaTestPlatform.bind(temporaryFolder.newFolder("platform"));
        previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
    }

    @After
    public void restorePlatform() {
        IrisSettings.settings = previousSettings;
        platform.close();
    }

    @Test
    public void immediateWriteReturnsTheTextItWrote() throws Exception {
        File schemaFile = new File(temporaryFolder.getRoot(), ".iris/schema/biome-schema.json");

        String text = SchemaFiles.write(schemaFile, () -> SCHEMA_TEXT, true).join();

        assertEquals(SCHEMA_TEXT, text);
        assertEquals(SCHEMA_TEXT, Files.readString(schemaFile.toPath(), StandardCharsets.UTF_8).stripTrailing());
    }

    @Test
    public void immediateWriteThrowsWhenTheSchemaCannotBeBuilt() {
        File schemaFile = new File(temporaryFolder.getRoot(), "broken-schema.json");

        assertThrows(IllegalStateException.class, () -> SchemaFiles.write(schemaFile, () -> {
            throw new IllegalStateException("broken");
        }, true));
        assertFalse(schemaFile.exists());
    }

    @Test
    public void backgroundWriteReturnsTheTextItWrote() throws Exception {
        File schemaFile = new File(temporaryFolder.getRoot(), "region-schema.json");

        String text = SchemaFiles.write(schemaFile, () -> SCHEMA_TEXT, false).join();

        assertEquals(SCHEMA_TEXT, text);
        assertEquals(SCHEMA_TEXT, Files.readString(schemaFile.toPath(), StandardCharsets.UTF_8).stripTrailing());
    }

    @Test
    public void failedBackgroundWriteHashesAsEmptyText() {
        File schemaFile = new File(temporaryFolder.getRoot(), "broken-schema.json");

        String text = SchemaFiles.write(schemaFile, () -> {
            throw new IllegalStateException("broken");
        }, false).join();

        assertEquals(SchemaFiles.FAILED_SCHEMA_TEXT, text);
    }

    @Test
    public void awaitTextsKeepsOrderAndMapsFailuresToEmptyText() {
        CompletableFuture<String> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("broken"));

        List<String> texts = SchemaFiles.awaitTexts(List.of(CompletableFuture.completedFuture("a"), failed, CompletableFuture.completedFuture("c")));

        assertEquals(List.of("a", SchemaFiles.FAILED_SCHEMA_TEXT, "c"), texts);
    }
}
