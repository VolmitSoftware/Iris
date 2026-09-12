package art.arcane.iris.generation.image;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.volmlib.util.collection.KList;
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

public class IrisImageMapRuntimeTest {
    private static final double EPSILON = 0.0001D;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void compilesHeightBindingsAndKeepsReloadSamplingDeterministic() throws Exception {
        File pack = temporaryFolder.newFolder("height-pack");
        writeMap(pack, "terrain", "terrain", grayscale(0, 255), new IrisImageMap()
                .setSource("terrain")
                .setType(IrisImageMapType.GRAYSCALE_HEIGHT)
                .setMinimumHeight(-64D)
                .setMaximumHeight(320D)
                .setOutOfBounds(IrisImageMapOutOfBounds.CLAMP));
        IrisDimension dimension = new IrisDimension();
        dimension.getImageMaps().add(new IrisImageMapBinding()
                .setKey("terrain")
                .setMap("terrain")
                .setApplication(IrisImageMapApplication.TERRAIN_HEIGHT));

        Sample first = sample(pack, dimension);
        Sample second = sample(pack, dimension);

        assertEquals(0D, first.minimum(), EPSILON);
        assertEquals(384D, first.maximum(), EPSILON);
        assertEquals(first.minimum(), second.minimum(), 0D);
        assertEquals(first.maximum(), second.maximum(), 0D);
        assertEquals(first.hash(), second.hash());
    }

    @Test
    public void composesNamedMasksInDeclarationOrder() throws Exception {
        File pack = temporaryFolder.newFolder("mask-pack");
        writeMap(pack, "terrain", "terrain", grayscale(255), new IrisImageMap()
                .setSource("terrain")
                .setType(IrisImageMapType.GRAYSCALE_HEIGHT)
                .setMinimumHeight(-64D)
                .setMaximumHeight(320D));
        writeMap(pack, "weight", "weight", grayscale(128), new IrisImageMap()
                .setSource("weight")
                .setType(IrisImageMapType.GRAYSCALE_MASK));
        IrisImageMapMask mask = new IrisImageMapMask()
                .setMap("weight")
                .setOperation(IrisImageMapMaskOperation.MULTIPLY)
                .setThreshold(0D)
                .setFalloff(1D);
        IrisDimension dimension = new IrisDimension();
        dimension.setImageMaps(new KList<>());
        dimension.getImageMaps().add(new IrisImageMapBinding()
                .setKey("terrain")
                .setMap("terrain")
                .setApplication(IrisImageMapApplication.TERRAIN_HEIGHT)
                .setMasks(new KList<>(mask)));
        dimension.getImageMaps().add(new IrisImageMapBinding()
                .setKey("weight")
                .setMap("weight")
                .setApplication(IrisImageMapApplication.MASK));

        IrisData data = IrisData.openDatapackCompiler(pack);
        try {
            IrisImageMapRuntime runtime = IrisImageMapRuntime.compile(data, dimension, -64);
            double weight = 128D / 255D;
            double expected = 10D + ((384D - 10D) * weight);
            assertEquals(expected, runtime.sampleTerrainHeight(0D, 0D, 10D), EPSILON);
        } finally {
            data.close();
        }
    }

    @Test
    public void unloadsDecodedImageWhenApplicationValidationFails() throws Exception {
        File pack = temporaryFolder.newFolder("invalid-application-pack");
        writeMap(pack, "terrain", "terrain", grayscale(255), new IrisImageMap()
                .setSource("terrain")
                .setType(IrisImageMapType.GRAYSCALE_HEIGHT));
        IrisDimension dimension = new IrisDimension();
        dimension.getImageMaps().add(new IrisImageMapBinding()
                .setKey("terrain")
                .setMap("terrain")
                .setApplication(IrisImageMapApplication.BIOME));

        IrisData data = IrisData.openDatapackCompiler(pack);
        try {
            assertThrows(IrisImageMapValidationException.class,
                    () -> IrisImageMapRuntime.compile(data, dimension, -64));
            assertEquals(0L, data.getImageLoader().getSize());
        } finally {
            data.close();
        }
    }

    private Sample sample(File pack, IrisDimension dimension) {
        IrisData data = IrisData.openDatapackCompiler(pack);
        try {
            IrisImageMapRuntime runtime = IrisImageMapRuntime.compile(data, dimension, -64);
            CompiledIrisImageMap compiled = runtime.getCompiled("terrain");
            return new Sample(
                    runtime.sampleTerrainHeight(0D, 0D, 25D),
                    runtime.sampleTerrainHeight(1D, 0D, 25D),
                    compiled.getContentHash()
            );
        } finally {
            data.close();
        }
    }

    private void writeMap(
            File pack,
            String mapKey,
            String imageKey,
            BufferedImage image,
            IrisImageMap map
    ) throws Exception {
        File images = new File(pack, "images");
        File maps = new File(pack, "image-maps");
        Files.createDirectories(images.toPath());
        Files.createDirectories(maps.toPath());
        ImageIO.write(image, "png", new File(images, imageKey + ".png"));
        Files.writeString(
                new File(maps, mapKey + ".json").toPath(),
                new Gson().toJson(map),
                StandardCharsets.UTF_8
        );
    }

    private BufferedImage grayscale(int... samples) {
        BufferedImage image = new BufferedImage(samples.length, 1, BufferedImage.TYPE_BYTE_GRAY);
        for (int x = 0; x < samples.length; x++) {
            image.getRaster().setSample(x, 0, 0, samples[x]);
        }
        return image;
    }

    private record Sample(double minimum, double maximum, String hash) {
    }
}
