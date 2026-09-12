package art.arcane.iris.studio.workspace;

import art.arcane.iris.generation.image.IrisImageMapValidationException;
import art.arcane.iris.generation.image.IrisImage;
import art.arcane.iris.generation.image.IrisImageMap;
import art.arcane.iris.generation.image.IrisImageMapApplication;
import art.arcane.iris.generation.image.IrisImageMapOutOfBounds;
import art.arcane.iris.generation.image.IrisImageMapSampling;
import art.arcane.iris.generation.image.IrisImageMapType;
import art.arcane.volmlib.util.collection.KMap;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

public class ImageMapStudioExporterTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void previewUsesTheRuntimeCompiler() throws Exception {
        File source = temporaryFolder.newFile("height.png");
        BufferedImage image = new BufferedImage(2, 1, BufferedImage.TYPE_BYTE_GRAY);
        image.getRaster().setSample(0, 0, 0, 0);
        image.getRaster().setSample(1, 0, 0, 255);
        ImageIO.write(image, "png", source);
        IrisImageMap definition = new IrisImageMap()
                .setSource("height")
                .setType(IrisImageMapType.GRAYSCALE_HEIGHT)
                .setMinimumHeight(-64D)
                .setMaximumHeight(320D);

        ImageMapStudioExporter.PreviewResult preview = ImageMapStudioExporter.preview(
                source.toPath(), definition
        );

        assertEquals(2, preview.compiled().getSourceWidth());
        assertEquals(-64D, preview.compiled().sampleHeight(0D, 0D), 0D);
        assertEquals(320D, preview.compiled().sampleHeight(1D, 0D), 0D);
        assertFalse(preview.colorProfile().isBlank());
    }

    @Test
    public void inspectsSourceMetadataWithoutACompatibleSemanticType() throws Exception {
        File source = temporaryFolder.newFile("inspect-rgb.png");
        BufferedImage image = new BufferedImage(2, 3, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0x40FF0000);
        image.setRGB(1, 2, 0xFFFF0000);
        ImageIO.write(image, "png", source);

        ImageMapStudioExporter.SourceInspection inspection = ImageMapStudioExporter.inspectSource(source.toPath());
        IrisImage inspected = new IrisImage(inspection.source(), inspection.format());

        assertEquals(2, inspected.getWidth());
        assertEquals(3, inspected.getHeight());
        assertTrue(inspected.hasAlpha());
        assertTrue(inspection.minimumAlpha() < 1D);
        assertEquals(1D, inspection.maximumAlpha(), 0D);
    }

    @Test
    public void readsEmbeddedPngColorProfileMetadata() throws Exception {
        File source = temporaryFolder.newFile("profile.png");
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image, "png", source);
        byte[] original = Files.readAllBytes(source.toPath());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(original, 0, 33);
        output.write(pngChunk("sRGB", new byte[]{0}));
        output.write(original, 33, original.length - 33);
        Files.write(source.toPath(), output.toByteArray());

        ImageMapStudioExporter.SourceInspection inspection = ImageMapStudioExporter.inspectSource(source.toPath());

        assertTrue(inspection.colorProfile().startsWith("sRGB:"));
    }

    @Test
    public void rejectsOversizedPngDimensionsBeforePixelDecode() throws Exception {
        File source = temporaryFolder.newFile("oversized.png");
        writeHeaderOnlyPng(source, 16_385, 1);

        IrisImageMapValidationException failure = assertThrows(
                IrisImageMapValidationException.class,
                () -> ImageMapStudioExporter.inspectSource(source.toPath())
        );

        assertTrue(failure.getMessage().contains("Image width must be 1..16384"));
    }

    @Test
    public void rejectsLossyInputsBeforeProjectMutation() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        File source = temporaryFolder.newFile("height.jpg");
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image, "jpg", source);
        ImageMapStudioExporter.ExportRequest request = new ImageMapStudioExporter.ExportRequest(
                pack,
                "overworld",
                "terrain",
                IrisImageMapApplication.TERRAIN_HEIGHT,
                "terrain",
                "terrain",
                new IrisImageMap().setType(IrisImageMapType.RGB_HEIGHT),
                source.toPath(),
                List.of()
        );

        assertThrows(IOException.class, () -> ImageMapStudioExporter.export(request));
        assertFalse(new File(pack, "images").exists());
        assertFalse(new File(pack, "image-maps").exists());
    }

    @Test
    public void rejectsIncompatibleDefinitionsBeforeProjectMutation() throws Exception {
        File pack = temporaryFolder.newFolder("invalid-pack");
        File source = temporaryFolder.newFile("map.png");
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image, "png", source);
        IrisImageMap definition = new IrisImageMap()
                .setType(IrisImageMapType.COLOR_MAP)
                .setSampling(IrisImageMapSampling.BILINEAR);
        ImageMapStudioExporter.ExportRequest request = new ImageMapStudioExporter.ExportRequest(
                pack,
                "overworld",
                "biomes",
                IrisImageMapApplication.BIOME,
                "biomes",
                "biomes",
                definition,
                source.toPath(),
                List.of()
        );

        assertThrows(IrisImageMapValidationException.class, () -> ImageMapStudioExporter.export(request));
        assertFalse(new File(pack, "images").exists());
        assertFalse(new File(pack, "image-maps").exists());
    }

    @Test
    public void exportsAndReloadsAValidatedRuntimeBinding() throws Exception {
        File pack = minimalPack("export-pack");
        File source = temporaryFolder.newFile("export-height.png");
        BufferedImage image = new BufferedImage(2, 1, BufferedImage.TYPE_BYTE_GRAY);
        image.getRaster().setSample(1, 0, 0, 255);
        ImageIO.write(image, "png", source);
        IrisImageMap definition = new IrisImageMap()
                .setType(IrisImageMapType.GRAYSCALE_HEIGHT)
                .setOutOfBounds(IrisImageMapOutOfBounds.CLAMP);
        ImageMapStudioExporter.ExportRequest request = new ImageMapStudioExporter.ExportRequest(
                pack,
                "main",
                "terrain",
                IrisImageMapApplication.TERRAIN_HEIGHT,
                "terrain",
                "terrain",
                definition,
                source.toPath(),
                List.of()
        );

        ImageMapStudioExporter.ExportResult result = ImageMapStudioExporter.export(request);

        assertTrue(Files.isRegularFile(result.imageFile()));
        assertTrue(Files.isRegularFile(result.imageMapFile()));
        assertTrue(Files.readString(result.dimensionFile()).contains("\"key\": \"terrain\""));
        assertEquals(64, result.contentHash().length());
    }

    @Test
    public void restoresEveryExistingTargetWhenPublishedRuntimeValidationFails() throws Exception {
        File pack = minimalPack("rollback-pack");
        File source = temporaryFolder.newFile("rollback-color.png");
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0xFFFF0000);
        ImageIO.write(image, "png", source);
        File imageTarget = write(pack, "images/biomes.png", "old-image");
        File mapTarget = write(pack, "image-maps/biomes.json", "old-map");
        String originalDimension = Files.readString(new File(pack, "dimensions/main.json").toPath());
        IrisImageMap definition = new IrisImageMap()
                .setType(IrisImageMapType.COLOR_MAP)
                .setColors(new KMap<>(Map.of("#FF0000", "iris:missing")));
        ImageMapStudioExporter.ExportRequest request = new ImageMapStudioExporter.ExportRequest(
                pack,
                "main",
                "biomes",
                IrisImageMapApplication.BIOME,
                "biomes",
                "biomes",
                definition,
                source.toPath(),
                List.of()
        );

        assertThrows(RuntimeException.class, () -> ImageMapStudioExporter.export(request));

        assertEquals("old-image", Files.readString(imageTarget.toPath()));
        assertEquals("old-map", Files.readString(mapTarget.toPath()));
        assertEquals(originalDimension, Files.readString(new File(pack, "dimensions/main.json").toPath()));
    }

    private File minimalPack(String name) throws Exception {
        File pack = temporaryFolder.newFolder(name);
        write(pack, "dimensions/main.json", "{\"regions\":[\"region\"]}");
        write(pack, "regions/region.json", "{\"landBiomes\":[\"biome\"]}");
        write(pack, "biomes/biome.json", "{\"name\":\"Biome\"}");
        return pack;
    }

    private File write(File root, String relative, String content) throws Exception {
        File target = new File(root, relative);
        Files.createDirectories(target.toPath().getParent());
        Files.writeString(target.toPath(), content, StandardCharsets.UTF_8);
        return target;
    }

    private void writeHeaderOnlyPng(File target, int width, int height) throws Exception {
        ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
        try (DataOutputStream header = new DataOutputStream(headerBytes)) {
            header.writeInt(width);
            header.writeInt(height);
            header.writeByte(8);
            header.writeByte(2);
            header.writeByte(0);
            header.writeByte(0);
            header.writeByte(0);
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        png.write(new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});
        png.write(pngChunk("IHDR", headerBytes.toByteArray()));
        png.write(pngChunk("IEND", new byte[0]));
        Files.write(target.toPath(), png.toByteArray());
    }

    private byte[] pngChunk(String type, byte[] data) throws Exception {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 checksum = new CRC32();
        checksum.update(typeBytes);
        checksum.update(data);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(data.length);
            output.write(typeBytes);
            output.write(data);
            output.writeInt((int) checksum.getValue());
        }
        return bytes.toByteArray();
    }
}
