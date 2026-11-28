package art.arcane.iris.pack.loading;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class IrisDataCloseLoadedUnderTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void closesOnlyLoadersInsideTheWorldFolder() throws IOException {
        Path dimensions = temporaryFolder.newFolder("dimensions").toPath();
        Path world = dimensions.resolve("studio");
        Path epochPack = createPack(world.resolve("iris/generation/epochs/a/pack"));
        Path prefixSiblingPack = createPack(dimensions.resolve("studio-other/iris/pack"));
        Path sourcePack = createPack(temporaryFolder.newFolder("packs").toPath().resolve("source"));
        IrisData epochData = IrisData.get(epochPack.toFile());
        IrisData prefixSiblingData = IrisData.get(prefixSiblingPack.toFile());
        IrisData sourceData = IrisData.get(sourcePack.toFile());

        try {
            IrisData.closeLoadedUnder(world.toFile());

            assertTrue(IrisData.getLoaded(epochPack.toFile()).isEmpty());
            assertTrue(epochData.isClosed());
            assertSame(prefixSiblingData, IrisData.getLoaded(prefixSiblingPack.toFile()).orElse(null));
            assertSame(sourceData, IrisData.getLoaded(sourcePack.toFile()).orElse(null));
        } finally {
            IrisData.getLoaded(epochPack.toFile()).ifPresent(IrisData::close);
            IrisData.getLoaded(prefixSiblingPack.toFile()).ifPresent(IrisData::close);
            IrisData.getLoaded(sourcePack.toFile()).ifPresent(IrisData::close);
        }
    }

    private static Path createPack(Path pack) throws IOException {
        Files.createDirectories(pack.resolve("dimensions"));
        Files.writeString(pack.resolve("dimensions/" + pack.getFileName() + ".json"), "{}");
        return pack;
    }
}
