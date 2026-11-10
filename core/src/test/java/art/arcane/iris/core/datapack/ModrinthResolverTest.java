package art.arcane.iris.core.datapack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ModrinthResolverTest {
    @Test
    public void latestVersionMustMatchServerVersion() {
        JsonArray versions = JsonParser.parseString("""
                [
                  {"id":"new","loaders":["datapack"],"game_versions":["26.3"]},
                  {"id":"match","loaders":["datapack"],"game_versions":["26.2"]}
                ]
                """).getAsJsonArray();

        JsonObject selected = ModrinthResolver.selectLatestDatapackVersion(versions, "26.2");

        assertEquals("match", selected.get("id").getAsString());
    }

    @Test
    public void incompatibleLatestVersionDoesNotFallBack() {
        JsonArray versions = JsonParser.parseString("""
                [{"id":"wrong","loaders":["datapack"],"game_versions":["26.3"]}]
                """).getAsJsonArray();

        assertNull(ModrinthResolver.selectLatestDatapackVersion(versions, "26.2"));
    }

    @Test
    public void malformedVersionElementsAreIgnored() {
        JsonArray versions = JsonParser.parseString("""
                [
                  null,
                  12,
                  {"id":"wrong-loaders","loaders":{},"game_versions":["26.2"]},
                  {"id":"wrong-loader-element","loaders":[{}],"game_versions":["26.2"]},
                  {"id":"wrong-versions","loaders":["datapack"],"game_versions":{}},
                  {"id":"wrong-version-element","loaders":["datapack"],"game_versions":[{}]},
                  {"id":"match","loaders":["datapack"],"game_versions":["26.2"]}
                ]
                """).getAsJsonArray();

        JsonObject selected = ModrinthResolver.selectLatestDatapackVersion(versions, "26.2");

        assertEquals("match", selected.get("id").getAsString());
    }

    @Test
    public void malformedFileElementsAreIgnored() {
        JsonObject version = JsonParser.parseString("""
                {
                  "files": [
                    null,
                    "not-an-object",
                    {"primary": {}, "filename": {}, "url": {}},
                    {"primary": true, "filename": "pack.zip", "url": "https://example.test/pack.zip"}
                  ]
                }
                """).getAsJsonObject();

        JsonObject selected = ModrinthResolver.selectFile(version);

        assertEquals("pack.zip", selected.get("filename").getAsString());
    }

    @Test
    public void malformedFilesCollectionReturnsNoFile() {
        JsonObject version = JsonParser.parseString("{\"files\": {}}").getAsJsonObject();

        assertNull(ModrinthResolver.selectFile(version));
    }

    @Test
    public void directUrlsWithSameFilenameHaveDistinctIdentities() throws Exception {
        ModrinthResolver.ResolvedDatapack first = ModrinthResolver.resolve("https://one.example/files/pack.zip", "26.2");
        ModrinthResolver.ResolvedDatapack second = ModrinthResolver.resolve("https://two.example/files/pack.zip", "26.2");

        assertTrue(first.isDirect());
        assertTrue(second.isDirect());
        assertNotEquals(first.getVersionId(), second.getVersionId());
    }

    @Test
    public void modrinthTextOutsideTheHostDoesNotTriggerApiResolution() throws Exception {
        ModrinthResolver.ResolvedDatapack resolved = ModrinthResolver.resolve(
                "https://example.test/modrinth.com/datapack/not-a-project", "26.2");

        assertTrue(resolved.isDirect());
        assertFalse(resolved.getVersionId().isBlank());
    }

    @Test
    public void fileUrlResolvesAsDirectArchiveWithDecodedFilename() throws Exception {
        Path directory = Files.createTempDirectory("iris-local-resolver");
        Path archive = directory.resolve("local pack.zip");
        try {
            Files.writeString(archive, "archive", StandardCharsets.UTF_8);

            ModrinthResolver.ResolvedDatapack resolved = ModrinthResolver.resolve(
                    archive.toUri().toASCIIString(), "26.2");

            assertTrue(resolved.isDirect());
            assertEquals("local pack.zip", resolved.getFileName());
            assertFalse(resolved.getVersionId().isBlank());
        } finally {
            Files.deleteIfExists(archive);
            Files.deleteIfExists(directory);
        }
    }

    @Test
    public void boundedApiReaderAcceptsTheExactCharacterLimit() throws Exception {
        String response = "1234\n5678";

        assertEquals(response, ModrinthResolver.readBoundedApiResponse(
                new StringReader(response), "test response", response.length()));
    }

    @Test
    public void boundedApiReaderRejectsAnUnbrokenResponseBeforeReadingPastItsFirstBoundedChunk() throws Exception {
        Reader response = new UnbrokenResponseReader();

        try {
            ModrinthResolver.readBoundedApiResponse(response, "test response", 32);
            fail("Expected oversized response rejection");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Oversized response"));
        }
    }

    private static final class UnbrokenResponseReader extends Reader {
        private boolean read;

        @Override
        public int read(char[] buffer, int offset, int length) throws IOException {
            if (read) {
                throw new IOException("Reader continued after the bounded chunk");
            }
            read = true;
            Arrays.fill(buffer, offset, offset + length, 'x');
            return length;
        }

        @Override
        public void close() {
        }
    }
}
