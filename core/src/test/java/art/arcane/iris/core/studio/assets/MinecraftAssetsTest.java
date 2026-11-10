package art.arcane.iris.core.studio.assets;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MinecraftAssetsTest {
    private static final String BLOCKSTATE = "assets/etopia/blockstates/grass_block.json";

    @Rule
    public TemporaryFolder folders = new TemporaryFolder();

    @Test
    public void aPluginsAssetsAreReadFromTheFirstResourcePackThatHasThem() throws IOException {
        Path withoutIt = pack("other.zip", "assets/other/blockstates/x.json", "{}");
        Path withIt = pack("etopia.zip", BLOCKSTATE, "{\"variants\":{}}");

        Optional<byte[]> read = MinecraftAssets.readFromPacks(List.of(withoutIt, withIt), BLOCKSTATE);

        assertEquals("{\"variants\":{}}", new String(read.orElseThrow(), StandardCharsets.UTF_8));
        assertEquals(Optional.empty(), MinecraftAssets.readFromPacks(List.of(withoutIt, withIt), "assets/etopia/models/none.json"));
        assertEquals(Optional.empty(), MinecraftAssets.readFromPacks(List.of(), BLOCKSTATE));
    }

    private Path pack(String name, String entry, String content) throws IOException {
        Path file = folders.getRoot().toPath().resolve(name);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry(entry));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return file;
    }

    @Test
    public void onlyFilesInTheClientsAssetFolderAreServed() {
        assertTrue(MinecraftAssets.isAssetPath("assets/minecraft/textures/block/stone.png"));
        assertFalse(MinecraftAssets.isAssetPath("net/minecraft/client/Main.class"));
        assertFalse(MinecraftAssets.isAssetPath("assets/../META-INF/MANIFEST.MF"));
        assertFalse(MinecraftAssets.isAssetPath("assets//minecraft/models/block/stone.json"));
        assertFalse(MinecraftAssets.isAssetPath("assets\\minecraft\\x.json"));
    }
}
