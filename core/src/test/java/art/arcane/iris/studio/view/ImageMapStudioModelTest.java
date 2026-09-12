package art.arcane.iris.studio.view;

import art.arcane.iris.generation.image.IrisImageMap;
import art.arcane.iris.generation.image.IrisImageMapOrigin;
import art.arcane.iris.generation.image.IrisImageMapMask;
import art.arcane.iris.generation.image.IrisImageMapMaskOperation;
import art.arcane.iris.generation.image.IrisImageMapRotation;
import art.arcane.iris.generation.image.IrisImageMapType;
import art.arcane.iris.world.IrisWorldBoundary;
import art.arcane.iris.world.IrisWorldBoundaryCenter;
import art.arcane.volmlib.util.collection.KMap;
import org.junit.Test;

import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ImageMapStudioModelTest {
    private static final double EPSILON = 0.000001D;

    @Test
    public void inspectsCanonicalSixteenBitGrayscaleMetadata() {
        BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_USHORT_GRAY);

        ImageMapStudioModel.SourceMetadata metadata = ImageMapStudioModel.inspect(
                Path.of("source.png"), image, "png"
        );

        assertEquals(3, metadata.width());
        assertEquals(2, metadata.height());
        assertEquals(6L, metadata.pixels());
        assertEquals("png", metadata.format());
        assertEquals("GRAYSCALE", metadata.colorMode());
        assertEquals(16, metadata.bitDepth());
        assertFalse(metadata.alpha());
        assertTrue(metadata.summary().contains("PNG"));
        assertTrue(metadata.summary().contains("profile not inspected"));
    }

    @Test
    public void normalizesResourceKeysAndRejectsAmbiguousLegendRows() {
        assertEquals("terrain/my-map", ImageMapStudioModel.safeKey(" Terrain/My Map.PNG "));
        assertEquals("image-map", ImageMapStudioModel.safeKey(".."));

        KMap<String, String> legend = ImageMapStudioModel.legend(List.of(
                new ImageMapStudioModel.LegendRow("#00aaFF", "minecraft:water")
        ));
        assertEquals("minecraft:water", legend.get("#00AAFF"));
        assertThrows(IllegalArgumentException.class, () -> ImageMapStudioModel.legend(List.of(
                new ImageMapStudioModel.LegendRow("#000000", "minecraft:stone"),
                new ImageMapStudioModel.LegendRow("#000000", "minecraft:deepslate")
        )));
        assertThrows(IllegalArgumentException.class, () -> ImageMapStudioModel.legend(List.of(
                new ImageMapStudioModel.LegendRow("000000", "minecraft:stone")
        )));
    }

    @Test
    public void mapsSourceCoordinatesThroughMirrorRotationScaleAndOrigins() {
        IrisImageMap definition = new IrisImageMap()
                .setBlocksPerPixel(2D)
                .setOrigin(new IrisImageMapOrigin(10D, 20D))
                .setSourceOrigin(new IrisImageMapOrigin(1D, 1D))
                .setMirrorX(true)
                .setRotation(IrisImageMapRotation.DEG_90);

        Point2D.Double world = ImageMapStudioModel.sourceToWorld(definition, 2D, 1D);

        assertEquals(10D, world.x, EPSILON);
        assertEquals(18D, world.y, EPSILON);
    }

    @Test
    public void reportsScaleAndBoundaryCoverageWarnings() {
        IrisImageMap definition = new IrisImageMap()
                .setBlocksPerPixel(0.5D)
                .setOrigin(new IrisImageMapOrigin())
                .setSourceOrigin(new IrisImageMapOrigin());
        IrisWorldBoundary boundary = new IrisWorldBoundary()
                .setCenter(new IrisWorldBoundaryCenter(2D, 2D))
                .setSize(20D);

        List<String> warnings = ImageMapStudioModel.warnings(definition, 4, 4, boundary);

        assertTrue(warnings.stream().anyMatch(value -> value.contains("Sub-block")));
        assertTrue(warnings.stream().anyMatch(value -> value.contains("complete chunk")));
        assertTrue(warnings.stream().anyMatch(value -> value.contains("not fully covered")));
        assertTrue(warnings.stream().anyMatch(value -> value.contains("substantially smaller")));
    }

    @Test
    public void acceptsAnExactSourceAndWorldBoundaryFit() {
        IrisImageMap definition = new IrisImageMap()
                .setBlocksPerPixel(1D)
                .setOrigin(new IrisImageMapOrigin())
                .setSourceOrigin(new IrisImageMapOrigin());
        IrisWorldBoundary boundary = new IrisWorldBoundary()
                .setCenter(new IrisWorldBoundaryCenter(8D, 8D))
                .setSize(16D);

        List<String> warnings = ImageMapStudioModel.warnings(definition, 16, 16, boundary);

        assertTrue(warnings.isEmpty());
    }

    @Test
    public void clearsSettingsThatAreInvalidForTheSelectedType() {
        IrisImageMap height = new IrisImageMap()
                .setType(IrisImageMapType.GRAYSCALE_HEIGHT)
                .setColorTolerance(12D)
                .setColors(new KMap<>(Map.of("#000000", "iris:test")));
        IrisImageMap color = new IrisImageMap()
                .setType(IrisImageMapType.COLOR_MAP)
                .setInverted(true)
                .setCurveExponent(2D)
                .setSmoothingRadius(4)
                .setColors(new KMap<>(Map.of("#000000", "iris:test")));
        IrisImageMap mask = new IrisImageMap()
                .setType(IrisImageMapType.GRAYSCALE_MASK)
                .setMinimumHeight(12D)
                .setMaximumHeight(42D)
                .setThreshold(0.7D)
                .setFalloff(0.2D)
                .setColors(new KMap<>(Map.of("#000000", "iris:test")));

        ImageMapStudioModel.normalizeTypeSettings(height);
        ImageMapStudioModel.normalizeTypeSettings(color);
        ImageMapStudioModel.normalizeTypeSettings(mask);

        assertEquals(0D, height.getColorTolerance(), 0D);
        assertTrue(height.getColors().isEmpty());
        assertFalse(color.isInverted());
        assertEquals(1D, color.getCurveExponent(), 0D);
        assertEquals(0, color.getSmoothingRadius());
        assertEquals("iris:test", color.getColors().get("#000000"));
        assertEquals(-64D, mask.getMinimumHeight(), 0D);
        assertEquals(320D, mask.getMaximumHeight(), 0D);
        assertEquals(0.5D, mask.getThreshold(), 0D);
        assertEquals(0D, mask.getFalloff(), 0D);
        assertTrue(mask.getColors().isEmpty());
    }

    @Test
    public void parsesOrderedComposedMaskRowsWithUnitRangeValidation() {
        List<ImageMapStudioModel.MaskRow> rows = List.of(
                ImageMapStudioModel.maskRow("land", "multiply", false, "0", "1"),
                ImageMapStudioModel.maskRow("roads", IrisImageMapMaskOperation.SUBTRACT, true, 0.5D, 0.25D)
        );

        List<IrisImageMapMask> masks = ImageMapStudioModel.masks(rows);

        assertEquals(2, masks.size());
        assertEquals("land", masks.get(0).getMap());
        assertEquals(IrisImageMapMaskOperation.MULTIPLY, masks.get(0).getOperation());
        assertEquals(IrisImageMapMaskOperation.SUBTRACT, masks.get(1).getOperation());
        assertTrue(masks.get(1).isInverted());
        assertEquals("roads", ImageMapStudioModel.maskRows(masks).get(1).map());
        assertThrows(IllegalArgumentException.class,
                () -> ImageMapStudioModel.maskRow("bad", "ADD", false, 1.1D, 0D));
    }
}
