package art.arcane.iris.generation.image;

import art.arcane.volmlib.util.collection.KMap;
import org.junit.Test;

import java.awt.image.BufferedImage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class IrisImageMapCompilerTest {
    private static final double EPSILON = 0.000001D;

    @Test
    public void decodesEightAndSixteenBitGrayscaleEndpoints() {
        CompiledIrisImageMap eightBit = compile(
                scalarDefinition(IrisImageMapType.GRAYSCALE_HEIGHT),
                grayscale8(new int[][]{{0, 255}})
        );
        CompiledIrisImageMap sixteenBit = compile(
                scalarDefinition(IrisImageMapType.GRAYSCALE_HEIGHT),
                grayscale16(new int[][]{{0, 32768, 65535}})
        );

        assertEquals(0D, eightBit.sampleNormalized(0D, 0D), EPSILON);
        assertEquals(1D, eightBit.sampleNormalized(1D, 0D), EPSILON);
        assertEquals(0D, sixteenBit.sampleNormalized(0D, 0D), EPSILON);
        assertEquals(32768D / 65535D, sixteenBit.sampleNormalized(1D, 0D), EPSILON);
        assertEquals(1D, sixteenBit.sampleNormalized(2D, 0D), EPSILON);
    }

    @Test
    public void decodesCanonicalRgbHeightAndRawChannels() {
        BufferedImage image = rgb(new int[][]{{0x000000, 0x123456, 0xFFFFFF}});
        CompiledIrisImageMap compiled = compile(scalarDefinition(IrisImageMapType.RGB_HEIGHT), image);

        assertEquals(0D, compiled.sampleNormalized(0D, 0D), EPSILON);
        assertEquals(0x123456 / 16_777_215D, compiled.sampleNormalized(1D, 0D), EPSILON);
        assertEquals(1D, compiled.sampleNormalized(2D, 0D), EPSILON);
        assertEquals(0x12, new IrisImage(image).getBandSample(1, 0, 0));
        assertEquals(0x34, new IrisImage(image).getBandSample(1, 0, 1));
        assertEquals(0x56, new IrisImage(image).getBandSample(1, 0, 2));
    }

    @Test
    public void freezesDecodedValuesAndHashAtCompileTime() {
        BufferedImage image = grayscale8(new int[][]{{64}});
        CompiledIrisImageMap compiled = compile(
                scalarDefinition(IrisImageMapType.GRAYSCALE_HEIGHT),
                image
        );
        double initialValue = compiled.sampleNormalized(0D, 0D);
        String initialHash = compiled.contentHash();

        image.getRaster().setSample(0, 0, 0, 255);

        assertEquals(initialValue, compiled.sampleNormalized(0D, 0D), 0D);
        assertEquals(initialHash, compiled.contentHash());
        CompiledIrisImageMap changed = compile(
                scalarDefinition(IrisImageMapType.GRAYSCALE_HEIGHT),
                image
        );
        assertNotEquals(initialHash, changed.contentHash());
    }

    @Test
    public void mapsOriginsScaleNegativeCoordinatesAndContainmentWithFloorSemantics() {
        IrisImageMap definition = scalarDefinition(IrisImageMapType.GRAYSCALE_HEIGHT)
                .setBlocksPerPixel(2D)
                .setOrigin(new IrisImageMapOrigin(10D, 20D))
                .setSourceOrigin(new IrisImageMapOrigin(1D, 0D))
                .setFallbackValue(0.75D);
        CompiledIrisImageMap compiled = compile(definition, grayscale8(new int[][]{{0, 128, 255}}));

        assertEquals(0D, compiled.sampleNormalized(8D, 20D), EPSILON);
        assertEquals(128D / 255D, compiled.sampleNormalized(10D, 20D), EPSILON);
        assertEquals(1D, compiled.sampleNormalized(12D, 20D), EPSILON);
        assertEquals(0.75D, compiled.sampleNormalized(7.999D, 20D), EPSILON);
        assertTrue(compiled.containsWorld(8D, 20D));
        assertFalse(compiled.containsWorld(7.999D, 20D));
        assertFalse(compiled.containsWorld(Double.POSITIVE_INFINITY, 20D));
    }

    @Test
    public void mapsEveryQuarterTurnAndMirrorsAroundSourceOrigin() {
        BufferedImage image = grayscale8(new int[][]{
                {0, 0, 0},
                {0, 0, 255},
                {0, 0, 0}
        });
        IrisImageMap definition = scalarDefinition(IrisImageMapType.GRAYSCALE_HEIGHT)
                .setSourceOrigin(new IrisImageMapOrigin(1D, 1D));

        assertEquals(1D, compile(definition.setRotation(IrisImageMapRotation.DEG_0), image)
                .sampleNormalized(1D, 0D), EPSILON);
        assertEquals(1D, compile(definition.setRotation(IrisImageMapRotation.DEG_90), image)
                .sampleNormalized(0D, 1D), EPSILON);
        assertEquals(1D, compile(definition.setRotation(IrisImageMapRotation.DEG_180), image)
                .sampleNormalized(-1D, 0D), EPSILON);
        assertEquals(1D, compile(definition.setRotation(IrisImageMapRotation.DEG_270), image)
                .sampleNormalized(0D, -1D), EPSILON);
        assertEquals(1D, compile(definition
                        .setRotation(IrisImageMapRotation.DEG_90)
                        .setMirrorX(true), image)
                .sampleNormalized(0D, -1D), EPSILON);

        BufferedImage zImage = grayscale8(new int[][]{
                {0, 0, 0},
                {0, 0, 0},
                {0, 255, 0}
        });
        assertEquals(1D, compile(definition
                        .setRotation(IrisImageMapRotation.DEG_0)
                        .setMirrorX(false)
                        .setMirrorZ(true), zImage)
                .sampleNormalized(0D, -1D), EPSILON);
    }

    @Test
    public void appliesEveryOutOfBoundsMode() {
        BufferedImage image = grayscale8(new int[][]{{0, 255}});
        IrisImageMap definition = scalarDefinition(IrisImageMapType.GRAYSCALE_HEIGHT).setFallbackValue(0.25D);

        assertEquals(0.25D, compile(definition.setOutOfBounds(IrisImageMapOutOfBounds.FALLBACK), image)
                .sampleNormalized(-1D, 0D), EPSILON);
        assertEquals(0D, compile(definition.setOutOfBounds(IrisImageMapOutOfBounds.CLAMP), image)
                .sampleNormalized(-1D, 0D), EPSILON);
        assertEquals(1D, compile(definition.setOutOfBounds(IrisImageMapOutOfBounds.REPEAT), image)
                .sampleNormalized(-1D, 0D), EPSILON);
        assertEquals(1D, compile(definition.setOutOfBounds(IrisImageMapOutOfBounds.MIRROR), image)
                .sampleNormalized(-2D, 0D), EPSILON);
        assertEquals(1D, compile(definition, image).sampleNormalized(2D, 0D), EPSILON);
        CompiledIrisImageMap error = compile(definition.setOutOfBounds(IrisImageMapOutOfBounds.ERROR), image);
        assertThrows(IrisImageMapValidationException.class, () -> error.sampleNormalized(-1D, 0D));
    }

    @Test
    public void samplesBilinearAndBicubicNumericMaps() {
        IrisImageMap bilinearDefinition = scalarDefinition(IrisImageMapType.GRAYSCALE_HEIGHT)
                .setSampling(IrisImageMapSampling.BILINEAR)
                .setOutOfBounds(IrisImageMapOutOfBounds.CLAMP);
        CompiledIrisImageMap bilinear = compile(bilinearDefinition, grayscale8(new int[][]{
                {0, 255},
                {255, 0}
        }));
        assertEquals(0.5D, bilinear.sampleNormalized(0.5D, 0.5D), EPSILON);

        IrisImageMap bicubicDefinition = scalarDefinition(IrisImageMapType.GRAYSCALE_HEIGHT)
                .setSampling(IrisImageMapSampling.BICUBIC)
                .setOutOfBounds(IrisImageMapOutOfBounds.CLAMP);
        CompiledIrisImageMap bicubic = compile(
                bicubicDefinition,
                grayscale8(new int[][]{{0, 85, 170, 255}})
        );
        assertEquals(0.5D, bicubic.sampleNormalized(1.5D, 0D), EPSILON);
    }

    @Test
    public void reportsExactErrorSamplingKernelCoverage() {
        IrisImageMap bilinearDefinition = scalarDefinition(IrisImageMapType.GRAYSCALE_HEIGHT)
                .setSampling(IrisImageMapSampling.BILINEAR)
                .setOutOfBounds(IrisImageMapOutOfBounds.ERROR);
        CompiledIrisImageMap bilinear = compile(bilinearDefinition, grayscale8(new int[][]{
                {0, 255},
                {255, 0}
        }));

        assertTrue(bilinear.containsWorldForSampling(0.5D, 0.5D));
        assertTrue(bilinear.containsWorldForSampling(1D, 1D));
        assertFalse(bilinear.containsWorldForSampling(1.5D, 0.5D));
        assertThrows(IrisImageMapValidationException.class, () -> bilinear.sampleNormalized(1.5D, 0.5D));

        IrisImageMap bicubicDefinition = scalarDefinition(IrisImageMapType.GRAYSCALE_HEIGHT)
                .setSampling(IrisImageMapSampling.BICUBIC)
                .setOutOfBounds(IrisImageMapOutOfBounds.ERROR);
        CompiledIrisImageMap bicubic = compile(bicubicDefinition, grayscale8(new int[][]{
                {0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0},
                {0, 0, 255, 0, 0},
                {0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0}
        }));

        assertTrue(bicubic.containsWorldForSampling(0D, 0D));
        assertTrue(bicubic.containsWorldForSampling(1.5D, 1.5D));
        assertFalse(bicubic.containsWorldForSampling(0.5D, 1.5D));
        assertFalse(bicubic.containsWorldForSampling(3.5D, 1.5D));
        assertThrows(IrisImageMapValidationException.class, () -> bicubic.sampleNormalized(0.5D, 1.5D));
    }

    @Test
    public void lightweightValidationViewRetainsCoverageWithoutDecodedValues() {
        IrisImageMap definition = scalarDefinition(IrisImageMapType.GRAYSCALE_HEIGHT)
                .setSampling(IrisImageMapSampling.BILINEAR)
                .setOutOfBounds(IrisImageMapOutOfBounds.ERROR);
        CompiledIrisImageMap compiled = compile(definition, grayscale8(new int[][]{
                {0, 255},
                {255, 0}
        }));
        CompiledIrisImageMap validationView = compiled.withoutDecodedValues();

        assertEquals(compiled.contentHash(), validationView.contentHash());
        assertEquals(compiled.getSourceMetadata(), validationView.getSourceMetadata());
        assertEquals(compiled.containsWorldForSampling(0.5D, 0.5D),
                validationView.containsWorldForSampling(0.5D, 0.5D));
        assertEquals(compiled.containsWorldForSampling(1.5D, 0.5D),
                validationView.containsWorldForSampling(1.5D, 0.5D));
        assertThrows(IrisImageMapValidationException.class, () -> validationView.sampleNormalized(0D, 0D));
    }

    @Test
    public void appliesLoadTimeSmoothingCurveAndHeightClamp() {
        IrisImageMap smoothedDefinition = scalarDefinition(IrisImageMapType.GRAYSCALE_HEIGHT)
                .setSmoothingRadius(1)
                .setOutOfBounds(IrisImageMapOutOfBounds.CLAMP);
        CompiledIrisImageMap smoothed = compile(smoothedDefinition, grayscale8(new int[][]{{0, 255, 0}}));
        assertEquals(1D / 3D, smoothed.sampleNormalized(0D, 0D), EPSILON);
        assertEquals(1D / 3D, smoothed.sampleNormalized(1D, 0D), EPSILON);
        assertEquals(1D / 3D, smoothed.sampleNormalized(2D, 0D), EPSILON);

        IrisImageMap curvedDefinition = scalarDefinition(IrisImageMapType.GRAYSCALE_HEIGHT)
                .setCurveExponent(2D)
                .setMinimumHeight(10D)
                .setMaximumHeight(20D)
                .setVerticalOffset(5D)
                .setClamp(true);
        CompiledIrisImageMap curved = compile(curvedDefinition, grayscale8(new int[][]{{128, 255}}));
        double curvedValue = Math.pow(128D / 255D, 2D);
        assertEquals(curvedValue, curved.sampleNormalized(0D, 0D), EPSILON);
        assertEquals(15D + (curvedValue * 10D), curved.sampleHeight(0D, 0D), EPSILON);
        assertEquals(20D, curved.sampleHeight(1D, 0D), EPSILON);
        assertEquals(1, curved.getClippedPixelCount());
    }

    @Test
    public void decodesContinuousAndBinaryMasks() {
        BufferedImage image = grayscale8(new int[][]{{64, 128, 192}});
        IrisImageMap continuousDefinition = scalarDefinition(IrisImageMapType.GRAYSCALE_MASK)
                .setInverted(true);
        CompiledIrisImageMap continuous = compile(continuousDefinition, image);
        assertEquals(1D - (64D / 255D), continuous.sampleNormalized(0D, 0D), EPSILON);
        assertThrows(IrisImageMapValidationException.class, () -> continuous.sampleHeight(0D, 0D));

        IrisImageMap binaryDefinition = scalarDefinition(IrisImageMapType.BINARY_MASK)
                .setThreshold(0.25D)
                .setFalloff(0.5D);
        CompiledIrisImageMap binary = compile(binaryDefinition, image);
        assertEquals(((64D / 255D) - 0.25D) / 0.5D, binary.sampleNormalized(0D, 0D), EPSILON);
        assertEquals(((128D / 255D) - 0.25D) / 0.5D, binary.sampleNormalized(1D, 0D), EPSILON);
        assertEquals(1D, binary.sampleNormalized(2D, 0D), EPSILON);
    }

    @Test
    public void appliesNumericAlphaPoliciesAndAlphaMasks() {
        BufferedImage image = rgba(new int[][]{{0xFFFFFFFF, 0x80FFFFFF, 0x00FFFFFF}});
        IrisImageMap definition = scalarDefinition(IrisImageMapType.RGB_HEIGHT);

        assertEquals(1D, compile(definition.setAlpha(IrisImageMapAlpha.IGNORE), image)
                .sampleNormalized(1D, 0D), EPSILON);
        assertEquals(128D / 255D, compile(definition.setAlpha(IrisImageMapAlpha.MASK), image)
                .sampleNormalized(1D, 0D), EPSILON);
        assertEquals(0.25D, compile(definition
                        .setAlpha(IrisImageMapAlpha.TRANSPARENT_IS_FALLBACK)
                        .setFallbackValue(0.25D), image)
                .sampleNormalized(2D, 0D), EPSILON);
        assertThrows(
                IrisImageMapValidationException.class,
                () -> compile(definition.setAlpha(IrisImageMapAlpha.ERROR), image)
        );

        IrisImageMap alphaDefinition = scalarDefinition(IrisImageMapType.ALPHA_MASK)
                .setAlpha(IrisImageMapAlpha.IGNORE);
        CompiledIrisImageMap alpha = compile(alphaDefinition, image);
        assertEquals(1D, alpha.sampleNormalized(0D, 0D), EPSILON);
        assertEquals(128D / 255D, alpha.sampleNormalized(1D, 0D), EPSILON);
        assertEquals(0D, alpha.sampleNormalized(2D, 0D), EPSILON);
    }

    @Test
    public void resolvesExactAndTolerantRawSrgbLegends() {
        KMap<String, String> colors = new KMap<>();
        colors.put("#FF0000", "red");
        colors.put("#0000FF", "blue");
        IrisImageMap definition = colorDefinition(colors)
                .setColorTolerance(2D)
                .setUnknownColor(IrisImageMapUnknownColor.IGNORE);
        CompiledIrisImageMap compiled = compile(
                definition,
                rgb(new int[][]{{0xFF0000, 0xFE0100, 0x0000FF, 0x00FF00}})
        );

        assertEquals("red", compiled.sampleTarget(0D, 0D));
        assertEquals("red", compiled.sampleTarget(1D, 0D));
        assertEquals("blue", compiled.sampleTarget(2D, 0D));
        assertNull(compiled.sampleTarget(3D, 0D));
        assertEquals(1, compiled.getUnknownColorPixelCount());
    }

    @Test
    public void rejectsAmbiguousTolerantLegendMatchesButExactWins() {
        KMap<String, String> colors = new KMap<>();
        colors.put("#FF0000", "high");
        colors.put("#FD0000", "low");
        IrisImageMap definition = colorDefinition(colors).setColorTolerance(1D);

        IrisImageMapValidationException ambiguity = assertThrows(
                IrisImageMapValidationException.class,
                () -> compile(definition, rgb(new int[][]{{0xFE0000}}))
        );
        assertTrue(ambiguity.getMessage().contains("ambiguously matches"));
        assertEquals("high", compile(definition, rgb(new int[][]{{0xFF0000}})).sampleTarget(0D, 0D));
    }

    @Test
    public void appliesColorAlphaFallbackAndRejectsPartialMaskAlpha() {
        KMap<String, String> colors = new KMap<>();
        colors.put("#FF0000", "red");
        IrisImageMap definition = colorDefinition(colors)
                .setAlpha(IrisImageMapAlpha.MASK)
                .setFallbackTarget("fallback");

        CompiledIrisImageMap binary = compile(definition, rgba(new int[][]{{0xFFFF0000, 0x00FF0000}}));
        assertEquals("red", binary.sampleTarget(0D, 0D));
        assertEquals("fallback", binary.sampleTarget(1D, 0D));
        assertThrows(
                IrisImageMapValidationException.class,
                () -> compile(definition, rgba(new int[][]{{0x80FF0000}}))
        );
    }

    @Test
    public void freezesCompiledLegendTargets() {
        KMap<String, String> colors = new KMap<>();
        colors.put("#FF0000", "red");
        IrisImageMap definition = colorDefinition(colors);
        BufferedImage image = rgb(new int[][]{{0xFF0000}});
        CompiledIrisImageMap compiled = compile(definition, image);

        colors.put("#FF0000", "changed");
        image.setRGB(0, 0, 0xFF0000FF);

        assertEquals("red", compiled.sampleTarget(0D, 0D));
    }

    @Test
    public void validatesFormatModeBitDepthAndDimensionsWithTypedDiagnostics() {
        IrisImageMap grayscaleDefinition = scalarDefinition(IrisImageMapType.GRAYSCALE_HEIGHT);
        IrisImageMapValidationException format = assertThrows(
                IrisImageMapValidationException.class,
                () -> CompiledIrisImageMap.compile(
                        grayscaleDefinition,
                        new IrisImage(grayscale8(new int[][]{{0}}), "jpeg")
                )
        );
        assertTrue(format.getDiagnostics().get(0).contains("PNG"));

        IrisImageMapValidationException mode = assertThrows(
                IrisImageMapValidationException.class,
                () -> compile(grayscaleDefinition, rgb(new int[][]{{0}}))
        );
        assertTrue(mode.getMessage().contains("grayscale PNG"));

        BufferedImage indexed = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_INDEXED);
        IrisImageMapValidationException indexedMode = assertThrows(
                IrisImageMapValidationException.class,
                () -> compile(grayscaleDefinition, indexed)
        );
        assertTrue(indexedMode.getMessage().contains("Indexed PNG"));

        BufferedImage lowBitRgb = new BufferedImage(1, 1, BufferedImage.TYPE_USHORT_565_RGB);
        IrisImageMapValidationException bitDepth = assertThrows(
                IrisImageMapValidationException.class,
                () -> compile(scalarDefinition(IrisImageMapType.RGB_HEIGHT), lowBitRgb)
        );
        assertTrue(bitDepth.getMessage().contains("8-bit RGB"));

        BufferedImage tooWide = new BufferedImage(16_385, 1, BufferedImage.TYPE_BYTE_GRAY);
        IrisImageMapValidationException dimensions = assertThrows(
                IrisImageMapValidationException.class,
                () -> compile(grayscaleDefinition, tooWide)
        );
        assertTrue(dimensions.getMessage().contains("16385"));

        BufferedImage tooManyPixels = new ReportedSizeImage(4_097, 4_097);
        IrisImageMapValidationException pixels = assertThrows(
                IrisImageMapValidationException.class,
                () -> compile(grayscaleDefinition, tooManyPixels)
        );
        assertTrue(pixels.getMessage().contains("pixels"));
    }

    @Test
    public void enforcesSchemaNumericBoundsAtRuntime() {
        IrisImageMap tooSmallScale = scalarDefinition(IrisImageMapType.GRAYSCALE_HEIGHT)
                .setBlocksPerPixel(IrisImageMap.MINIMUM_SCALE / 2D);
        IrisImageMapValidationException scale = assertThrows(
                IrisImageMapValidationException.class,
                () -> compile(tooSmallScale, grayscale8(new int[][]{{0}}))
        );
        assertTrue(scale.getMessage().contains("blocksPerPixel must be finite and at least"));

        IrisImageMap minimums = scalarDefinition(IrisImageMapType.GRAYSCALE_HEIGHT)
                .setBlocksPerPixel(IrisImageMap.MINIMUM_SCALE)
                .setCurveExponent(IrisImageMap.MINIMUM_SCALE);
        assertEquals(0D, compile(minimums, grayscale8(new int[][]{{0}}))
                .sampleNormalized(0D, 0D), EPSILON);

        IrisImageMap tooSmallCurve = scalarDefinition(IrisImageMapType.GRAYSCALE_HEIGHT)
                .setCurveExponent(IrisImageMap.MINIMUM_SCALE / 2D);
        IrisImageMapValidationException curve = assertThrows(
                IrisImageMapValidationException.class,
                () -> compile(tooSmallCurve, grayscale8(new int[][]{{0}}))
        );
        assertTrue(curve.getMessage().contains("curveExponent must be finite and at least"));

        KMap<String, String> colors = new KMap<>();
        colors.put("#FF0000", "red");
        IrisImageMap maximumTolerance = colorDefinition(colors)
                .setColorTolerance(IrisImageMap.MAXIMUM_COLOR_TOLERANCE);
        assertEquals("red", compile(maximumTolerance, rgb(new int[][]{{0xFF0000}}))
                .sampleTarget(0D, 0D));

        IrisImageMap excessiveTolerance = colorDefinition(colors)
                .setColorTolerance(IrisImageMap.MAXIMUM_COLOR_TOLERANCE + 0.000001D);
        IrisImageMapValidationException tolerance = assertThrows(
                IrisImageMapValidationException.class,
                () -> compile(excessiveTolerance, rgb(new int[][]{{0xFF0000}}))
        );
        assertTrue(tolerance.getMessage().contains("colorTolerance must be finite and within"));
    }

    @Test
    public void hashesDecodedContentAndCanonicalConfigurationDeterministically() {
        KMap<String, String> firstColors = new KMap<>();
        firstColors.put("#FF0000", "red");
        firstColors.put("#0000FF", "blue");
        KMap<String, String> secondColors = new KMap<>();
        secondColors.put("#0000FF", "blue");
        secondColors.put("#FF0000", "red");
        BufferedImage image = rgb(new int[][]{{0xFF0000}});

        CompiledIrisImageMap first = compile(colorDefinition(firstColors), image);
        CompiledIrisImageMap second = compile(colorDefinition(secondColors), image);
        assertEquals(first.contentHash(), second.contentHash());
        assertEquals(64, first.contentHash().length());
        assertEquals(64, first.getSourceMetadata().decodedContentHash().length());
        assertEquals(1, first.getSourceWidth());
        assertEquals(1, first.getSourceHeight());
        assertEquals(IrisImageMapType.COLOR_MAP, first.getType());

        IrisImageMap changed = colorDefinition(firstColors).setBlocksPerPixel(2D);
        assertNotEquals(first.contentHash(), compile(changed, image).contentHash());
    }

    @Test
    public void storesLargeRectangularMapsAcrossBoundedTileEdges() {
        BufferedImage image = new BufferedImage(1_025, 1_024, BufferedImage.TYPE_BYTE_GRAY);
        image.getRaster().setSample(1, 1_023, 0, 255);

        CompiledIrisImageMap compiled = compile(
                scalarDefinition(IrisImageMapType.GRAYSCALE_HEIGHT), image
        );

        assertEquals(1_025, compiled.getSourceWidth());
        assertEquals(1_024, compiled.getSourceHeight());
        assertEquals(0D, compiled.sampleNormalized(0D, 1_023D), 0D);
        assertEquals(1D, compiled.sampleNormalized(1D, 1_023D), 0D);
    }

    private static CompiledIrisImageMap compile(IrisImageMap definition, BufferedImage image) {
        return CompiledIrisImageMap.compile(definition, new IrisImage(image, "png"));
    }

    private static IrisImageMap scalarDefinition(IrisImageMapType type) {
        return new IrisImageMap()
                .setSource("test")
                .setType(type)
                .setOutOfBounds(IrisImageMapOutOfBounds.FALLBACK);
    }

    private static IrisImageMap colorDefinition(KMap<String, String> colors) {
        return new IrisImageMap()
                .setSource("test")
                .setType(IrisImageMapType.COLOR_MAP)
                .setColors(colors)
                .setOutOfBounds(IrisImageMapOutOfBounds.CLAMP);
    }

    private static BufferedImage grayscale8(int[][] values) {
        int height = values.length;
        int width = values[0].length;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                image.getRaster().setSample(x, z, 0, values[z][x]);
            }
        }
        return image;
    }

    private static BufferedImage grayscale16(int[][] values) {
        int height = values.length;
        int width = values[0].length;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_USHORT_GRAY);
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                image.getRaster().setSample(x, z, 0, values[z][x]);
            }
        }
        return image;
    }

    private static BufferedImage rgb(int[][] values) {
        int height = values.length;
        int width = values[0].length;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, z, 0xFF000000 | values[z][x]);
            }
        }
        return image;
    }

    private static BufferedImage rgba(int[][] values) {
        int height = values.length;
        int width = values[0].length;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, z, values[z][x]);
            }
        }
        return image;
    }

    private static final class ReportedSizeImage extends BufferedImage {
        private final int reportedWidth;
        private final int reportedHeight;

        private ReportedSizeImage(int reportedWidth, int reportedHeight) {
            super(1, 1, BufferedImage.TYPE_BYTE_GRAY);
            this.reportedWidth = reportedWidth;
            this.reportedHeight = reportedHeight;
        }

        @Override
        public int getWidth() {
            return reportedWidth;
        }

        @Override
        public int getHeight() {
            return reportedHeight;
        }
    }
}
