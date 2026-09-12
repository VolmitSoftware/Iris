package art.arcane.iris.generation.noise;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.image.IrisImageMapValidationException;
import art.arcane.iris.generation.image.IrisImageMap;
import art.arcane.iris.generation.image.IrisImageMapOutOfBounds;
import art.arcane.iris.generation.image.IrisImageMapType;
import com.google.gson.Gson;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ImageNoiseTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void samplesScalarImageMapsWithoutRetainingDecodedSources() throws Exception {
        File pack = temporaryFolder.newFolder("scalar-pack");
        BufferedImage image = new BufferedImage(2, 1, BufferedImage.TYPE_BYTE_GRAY);
        image.getRaster().setSample(1, 0, 0, 255);
        writeImage(pack, "height", image);
        writeMap(pack, "height", new IrisImageMap()
                .setSource("height")
                .setType(IrisImageMapType.GRAYSCALE_HEIGHT)
                .setOutOfBounds(IrisImageMapOutOfBounds.CLAMP));

        IrisData data = IrisData.openDatapackCompiler(pack);
        try {
            ImageNoise noise = new ImageNoise(data, "height");

            assertEquals(0D, noise.noise(0D, 0D), 0D);
            assertEquals(1D, noise.noise(1D, 0D), 0D);
            assertEquals(0L, data.getImageLoader().getSize());
        } finally {
            data.close();
        }
    }

    @Test
    public void rejectsColorMapsBeforeLoadingTheirSource() throws Exception {
        File pack = temporaryFolder.newFolder("color-pack");
        writeMap(pack, "colors", new IrisImageMap()
                .setSource("missing")
                .setType(IrisImageMapType.COLOR_MAP));

        IrisData data = IrisData.openDatapackCompiler(pack);
        try {
            IrisImageMapValidationException failure = assertThrows(
                    IrisImageMapValidationException.class,
                    () -> new ImageNoise(data, "colors")
            );

            assertEquals("Generator-style image-map resource 'colors' must produce normalized scalar data",
                    failure.getMessage());
            assertEquals(0L, data.getImageLoader().getSize());
        } finally {
            data.close();
        }
    }

    private void writeImage(File pack, String key, BufferedImage image) throws Exception {
        File folder = new File(pack, "images");
        Files.createDirectories(folder.toPath());
        ImageIO.write(image, "png", new File(folder, key + ".png"));
    }

    private void writeMap(File pack, String key, IrisImageMap definition) throws Exception {
        File folder = new File(pack, "image-maps");
        Files.createDirectories(folder.toPath());
        Files.writeString(
                new File(folder, key + ".json").toPath(),
                new Gson().toJson(definition),
                StandardCharsets.UTF_8
        );
    }
}
