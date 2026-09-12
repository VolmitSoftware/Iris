package art.arcane.iris.generation.image;

import art.arcane.volmlib.util.collection.KMap;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class IrisImageMapCompiler {
    public static final int MINIMUM_DIMENSION = 1;
    public static final int MAXIMUM_DIMENSION = 16_384;
    public static final long MAXIMUM_PIXELS = 16_777_216L;

    private static final Pattern COLOR_PATTERN = Pattern.compile("#[0-9a-fA-F]{6}");

    private IrisImageMapCompiler() {
    }

    public static CompiledIrisImageMap compile(IrisImageMap definition, IrisImage image) {
        if (definition == null) {
            throw new IrisImageMapValidationException("Image-map definition is required");
        }
        if (image == null) {
            throw new IrisImageMapValidationException("Decoded image source is required");
        }

        List<String> diagnostics = validate(definition, image);
        if (!diagnostics.isEmpty()) {
            throw new IrisImageMapValidationException(diagnostics);
        }

        try {
            SourceDigest sourceDigest = hashSource(image);
            IrisImageMapSourceMetadata metadata = new IrisImageMapSourceMetadata(
                    image.getFormat(),
                    image.getColorMode(),
                    image.getWidth(),
                    image.getHeight(),
                    image.getColorComponentCount(),
                    image.getChannelCount(),
                    image.getBitDepth(),
                    image.hasAlpha(),
                    sourceDigest.minimumAlpha(),
                    sourceDigest.maximumAlpha(),
                    sourceDigest.hash()
            );
            IrisImageMap snapshot = snapshot(definition);
            CompiledIrisImageMap.FloatTiles scalarValues = null;
            CompiledIrisImageMap.IntTiles targetIndices = null;
            String[] targets = null;
            int clippedPixelCount = 0;
            int unknownColorPixelCount = 0;

            if (definition.getType() == IrisImageMapType.COLOR_MAP) {
                CompiledTargets compiledTargets = compileTargets(definition, image);
                targetIndices = compiledTargets.indices();
                targets = compiledTargets.targets();
                unknownColorPixelCount = compiledTargets.unknownColorPixelCount();
            } else {
                scalarValues = compileScalars(definition, image);
                if (definition.getSmoothingRadius() > 0) {
                    smooth(scalarValues, image.getWidth(), image.getHeight(), definition.getSmoothingRadius());
                }
                clippedPixelCount = countClippedPixels(
                        definition, scalarValues, Math.multiplyExact(image.getWidth(), image.getHeight())
                );
            }

            String contentHash = hashContent(snapshot, metadata);
            String fallbackTarget = definition.getFallbackTarget() == null || definition.getFallbackTarget().isBlank()
                    ? null
                    : definition.getFallbackTarget();
            CompiledIrisImageMap.SourceTransform sourceTransform = compileSourceTransform(definition);
            CompiledIrisImageMap.State state = new CompiledIrisImageMap.State(
                    snapshot,
                    metadata,
                    definition.getType(),
                    scalarValues,
                    targetIndices,
                    targets,
                    sourceTransform,
                    definition.getSampling(),
                    definition.getOutOfBounds(),
                    definition.getFallbackValue(),
                    fallbackTarget,
                    definition.getMinimumHeight(),
                    definition.getMaximumHeight(),
                    definition.getVerticalOffset(),
                    definition.isClamp(),
                    clippedPixelCount,
                    unknownColorPixelCount,
                    contentHash
            );
            return new CompiledIrisImageMap(state);
        } catch (IrisImageMapValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IrisImageMapValidationException(
                    "Failed to compile image map '" + definition.getSource() + "': " + exception.getMessage(),
                    exception
            );
        }
    }

    private static List<String> validate(IrisImageMap definition, IrisImage image) {
        List<String> diagnostics = new ArrayList<>();
        if (definition.getSource() == null || definition.getSource().isBlank()) {
            diagnostics.add("Image-map source must not be blank");
        }
        validateSource(image, diagnostics);
        validateCoordinates(definition, diagnostics);
        validateScalarSettings(definition, diagnostics);
        validateType(definition, image, diagnostics);
        validateLegend(definition, diagnostics);
        return diagnostics;
    }

    private static void validateSource(IrisImage image, List<String> diagnostics) {
        if (!"png".equals(image.getFormat())) {
            diagnostics.add("Image format must be PNG, got " + image.getFormat());
        }
        int width = image.getWidth();
        int height = image.getHeight();
        if (width < MINIMUM_DIMENSION || width > MAXIMUM_DIMENSION) {
            diagnostics.add("Image width must be " + MINIMUM_DIMENSION + ".." + MAXIMUM_DIMENSION + ", got " + width);
        }
        if (height < MINIMUM_DIMENSION || height > MAXIMUM_DIMENSION) {
            diagnostics.add("Image height must be " + MINIMUM_DIMENSION + ".." + MAXIMUM_DIMENSION + ", got " + height);
        }
        long pixels = (long) width * height;
        if (pixels > MAXIMUM_PIXELS) {
            diagnostics.add("Image contains " + pixels + " pixels; maximum is " + MAXIMUM_PIXELS);
        }
        if (image.getColorMode() == IrisImageColorMode.INDEXED) {
            diagnostics.add("Indexed PNG images are unsupported; convert the source to grayscale, RGB, or RGBA");
        } else if (image.getColorMode() == IrisImageColorMode.UNSUPPORTED) {
            diagnostics.add("Unsupported PNG color mode with " + image.getColorComponentCount()
                    + " color components and " + image.getChannelCount() + " channels");
        }
    }

    private static void validateCoordinates(IrisImageMap definition, List<String> diagnostics) {
        if (!finiteAtLeast(definition.getBlocksPerPixel(), IrisImageMap.MINIMUM_SCALE)) {
            diagnostics.add("blocksPerPixel must be finite and at least " + IrisImageMap.MINIMUM_SCALE);
        }
        IrisImageMapOrigin origin = definition.getOrigin();
        if (origin == null || !Double.isFinite(origin.getX()) || !Double.isFinite(origin.getZ())) {
            diagnostics.add("origin X and Z must be finite");
        }
        IrisImageMapOrigin sourceOrigin = definition.getSourceOrigin();
        if (sourceOrigin == null || !Double.isFinite(sourceOrigin.getX()) || !Double.isFinite(sourceOrigin.getZ())) {
            diagnostics.add("sourceOrigin X and Z must be finite");
        }
        if (definition.getRotation() == null) {
            diagnostics.add("rotation is required");
        }
        if (definition.getSampling() == null) {
            diagnostics.add("sampling is required");
        }
        if (definition.getOutOfBounds() == null) {
            diagnostics.add("outOfBounds is required");
        }
    }

    private static void validateScalarSettings(IrisImageMap definition, List<String> diagnostics) {
        if (!range(definition.getFallbackValue(), 0D, 1D)) {
            diagnostics.add("fallbackValue must be finite and within 0..1");
        }
        if (!Double.isFinite(definition.getMinimumHeight()) || !Double.isFinite(definition.getMaximumHeight())) {
            diagnostics.add("minimumHeight and maximumHeight must be finite");
        } else if (definition.getMaximumHeight() < definition.getMinimumHeight()) {
            diagnostics.add("maximumHeight must be greater than or equal to minimumHeight");
        }
        if (!Double.isFinite(definition.getVerticalOffset())) {
            diagnostics.add("verticalOffset must be finite");
        }
        if (!finiteAtLeast(definition.getCurveExponent(), IrisImageMap.MINIMUM_SCALE)) {
            diagnostics.add("curveExponent must be finite and at least " + IrisImageMap.MINIMUM_SCALE);
        }
        if (definition.getSmoothingRadius() < 0 || definition.getSmoothingRadius() > 32) {
            diagnostics.add("smoothingRadius must be within 0..32");
        }
        if (!range(definition.getThreshold(), 0D, 1D)) {
            diagnostics.add("threshold must be finite and within 0..1");
        }
        if (!range(definition.getFalloff(), 0D, 1D)) {
            diagnostics.add("falloff must be finite and within 0..1");
        }
        if (!range(definition.getColorTolerance(), 0D, IrisImageMap.MAXIMUM_COLOR_TOLERANCE)) {
            diagnostics.add("colorTolerance must be finite and within 0.."
                    + IrisImageMap.MAXIMUM_COLOR_TOLERANCE);
        }
        if (definition.getAlpha() == null) {
            diagnostics.add("alpha policy is required");
        }
        if (definition.getUnknownColor() == null) {
            diagnostics.add("unknownColor policy is required");
        }
    }

    private static void validateType(IrisImageMap definition, IrisImage image, List<String> diagnostics) {
        IrisImageMapType type = definition.getType();
        if (type == null) {
            diagnostics.add("Image-map type is required");
            return;
        }

        IrisImageColorMode colorMode = image.getColorMode();
        int bitDepth = image.getBitDepth();
        switch (type) {
            case GRAYSCALE_HEIGHT, BINARY_MASK, GRAYSCALE_MASK -> {
                if (colorMode != IrisImageColorMode.GRAYSCALE) {
                    diagnostics.add(type + " requires a grayscale PNG, got " + colorMode);
                }
                if (bitDepth != 8 && bitDepth != 16) {
                    diagnostics.add(type + " requires 8-bit or 16-bit grayscale samples, got " + bitDepth + "-bit");
                }
            }
            case RGB_HEIGHT, COLOR_MAP -> {
                if (colorMode != IrisImageColorMode.RGB && colorMode != IrisImageColorMode.RGBA) {
                    diagnostics.add(type + " requires an RGB or RGBA PNG, got " + colorMode);
                }
                if (bitDepth != 8) {
                    diagnostics.add(type + " requires canonical 8-bit RGB channels, got " + bitDepth + "-bit");
                }
            }
            case ALPHA_MASK -> {
                if (colorMode != IrisImageColorMode.RGBA || !image.hasAlpha()) {
                    diagnostics.add("ALPHA_MASK requires an 8-bit RGBA PNG");
                }
                if (bitDepth != 8) {
                    diagnostics.add("ALPHA_MASK requires an 8-bit alpha channel, got " + bitDepth + "-bit color channels");
                }
                if (definition.getAlpha() != null && definition.getAlpha() != IrisImageMapAlpha.IGNORE) {
                    diagnostics.add("ALPHA_MASK requires alpha=IGNORE because alpha is the map data");
                }
            }
        }

        if ((type == IrisImageMapType.COLOR_MAP || type == IrisImageMapType.BINARY_MASK)
                && definition.getSampling() != null
                && definition.getSampling() != IrisImageMapSampling.NEAREST) {
            diagnostics.add(type + " requires NEAREST sampling");
        }
        if (type == IrisImageMapType.COLOR_MAP && definition.getSmoothingRadius() != 0) {
            diagnostics.add("COLOR_MAP does not support numeric smoothing");
        }
    }

    private static void validateLegend(IrisImageMap definition, List<String> diagnostics) {
        KMap<String, String> colors = definition.getColors();
        if (definition.getType() != IrisImageMapType.COLOR_MAP) {
            if (colors != null && !colors.isEmpty()) {
                diagnostics.add("colors may only be set for COLOR_MAP definitions");
            }
            if (definition.getColorTolerance() != 0D) {
                diagnostics.add("colorTolerance may only be set for COLOR_MAP definitions");
            }
            return;
        }
        if (colors == null || colors.isEmpty()) {
            diagnostics.add("COLOR_MAP requires at least one #RRGGBB legend entry");
            return;
        }

        Map<Integer, String> decoded = new HashMap<>();
        for (Map.Entry<String, String> entry : colors.entrySet()) {
            String encoded = entry.getKey();
            String target = entry.getValue();
            if (encoded == null || !COLOR_PATTERN.matcher(encoded).matches()) {
                diagnostics.add("Color legend key must use exact #RRGGBB syntax, got " + encoded);
                continue;
            }
            if (target == null || target.isBlank()) {
                diagnostics.add("Color legend target for " + encoded + " must not be blank");
            }
            int rgb = Integer.parseInt(encoded.substring(1), 16);
            String previous = decoded.putIfAbsent(rgb, encoded);
            if (previous != null) {
                diagnostics.add("Color legend duplicates raw color " + previous + " as " + encoded);
            }
        }

        boolean fallbackRequired = definition.getOutOfBounds() == IrisImageMapOutOfBounds.FALLBACK
                || definition.getUnknownColor() == IrisImageMapUnknownColor.FALLBACK
                || definition.getAlpha() == IrisImageMapAlpha.TRANSPARENT_IS_FALLBACK
                || definition.getAlpha() == IrisImageMapAlpha.MASK;
        if (fallbackRequired && (definition.getFallbackTarget() == null || definition.getFallbackTarget().isBlank())) {
            diagnostics.add("fallbackTarget is required by the configured color-map fallback policy");
        }
    }

    private static CompiledIrisImageMap.FloatTiles compileScalars(IrisImageMap definition, IrisImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        CompiledIrisImageMap.FloatTiles values = new CompiledIrisImageMap.FloatTiles(
                Math.multiplyExact(width, height)
        );
        for (int sourceZ = 0; sourceZ < height; sourceZ++) {
            for (int sourceX = 0; sourceX < width; sourceX++) {
                double alpha = image.getAlphaNormalized(sourceX, sourceZ);
                validateAlphaPixel(definition, sourceX, sourceZ, alpha);
                double value;
                if (definition.getAlpha() == IrisImageMapAlpha.TRANSPARENT_IS_FALLBACK && alpha == 0D) {
                    value = definition.getFallbackValue();
                } else {
                    value = rawScalar(definition.getType(), image, sourceX, sourceZ);
                    if (definition.isInverted()) {
                        value = 1D - value;
                    }
                    value = Math.pow(clamp01(value), definition.getCurveExponent());
                    if (definition.getType() == IrisImageMapType.BINARY_MASK) {
                        value = threshold(value, definition.getThreshold(), definition.getFalloff());
                    }
                    if (definition.getAlpha() == IrisImageMapAlpha.MASK) {
                        value *= alpha;
                    }
                }
                values.set((sourceZ * width) + sourceX, (float) clamp01(value));
            }
        }
        return values;
    }

    private static double rawScalar(IrisImageMapType type, IrisImage image, int sourceX, int sourceZ) {
        return switch (type) {
            case GRAYSCALE_HEIGHT, BINARY_MASK, GRAYSCALE_MASK -> image.getBandNormalized(sourceX, sourceZ, 0);
            case RGB_HEIGHT -> {
                int red = image.getBandSample(sourceX, sourceZ, 0);
                int green = image.getBandSample(sourceX, sourceZ, 1);
                int blue = image.getBandSample(sourceX, sourceZ, 2);
                int encoded = (red << 16) | (green << 8) | blue;
                yield encoded / 16_777_215D;
            }
            case ALPHA_MASK -> image.getAlphaNormalized(sourceX, sourceZ);
            case COLOR_MAP -> throw new IrisImageMapValidationException("COLOR_MAP does not produce scalar pixels");
        };
    }

    private static CompiledTargets compileTargets(IrisImageMap definition, IrisImage image) {
        Legend legend = compileLegend(definition);
        int width = image.getWidth();
        int height = image.getHeight();
        CompiledIrisImageMap.IntTiles indices = new CompiledIrisImageMap.IntTiles(
                Math.multiplyExact(width, height)
        );
        int unknownColorPixelCount = 0;
        for (int sourceZ = 0; sourceZ < height; sourceZ++) {
            for (int sourceX = 0; sourceX < width; sourceX++) {
                double alpha = image.getAlphaNormalized(sourceX, sourceZ);
                validateAlphaPixel(definition, sourceX, sourceZ, alpha);
                int targetIndex;
                if (definition.getAlpha() == IrisImageMapAlpha.TRANSPARENT_IS_FALLBACK && alpha == 0D) {
                    targetIndex = legend.fallbackIndex();
                } else if (definition.getAlpha() == IrisImageMapAlpha.MASK && alpha < 1D) {
                    if (alpha == 0D) {
                        targetIndex = legend.fallbackIndex();
                    } else {
                        throw new IrisImageMapValidationException(
                                "COLOR_MAP alpha=MASK requires binary alpha; pixel " + sourceX + "," + sourceZ
                                        + " has alpha " + alpha
                        );
                    }
                } else {
                    int rgb = image.getRawRgb8(sourceX, sourceZ);
                    TargetMatch match = matchTarget(definition, legend, rgb, sourceX, sourceZ);
                    targetIndex = match.targetIndex();
                    if (match.unknown()) {
                        unknownColorPixelCount++;
                    }
                }
                indices.set((sourceZ * width) + sourceX, targetIndex);
            }
        }
        return new CompiledTargets(indices, legend.targets(), unknownColorPixelCount);
    }

    private static Legend compileLegend(IrisImageMap definition) {
        List<MutableLegendEntry> entries = new ArrayList<>();
        for (Map.Entry<String, String> entry : definition.getColors().entrySet()) {
            int rgb = Integer.parseInt(entry.getKey().substring(1), 16);
            entries.add(new MutableLegendEntry(rgb, entry.getKey().toUpperCase(), entry.getValue()));
        }
        entries.sort(Comparator.comparingInt(MutableLegendEntry::rgb).thenComparing(MutableLegendEntry::target));

        LinkedHashMap<String, Integer> targetIndexes = new LinkedHashMap<>();
        Map<Integer, LegendEntry> exact = new HashMap<>();
        List<LegendEntry> compiled = new ArrayList<>();
        for (MutableLegendEntry entry : entries) {
            int targetIndex = targetIndexes.computeIfAbsent(entry.target(), ignored -> targetIndexes.size());
            LegendEntry compiledEntry = new LegendEntry(entry.rgb(), entry.encoded(), entry.target(), targetIndex);
            exact.put(entry.rgb(), compiledEntry);
            compiled.add(compiledEntry);
        }

        int fallbackIndex = -1;
        if (definition.getFallbackTarget() != null && !definition.getFallbackTarget().isBlank()) {
            fallbackIndex = targetIndexes.computeIfAbsent(
                    definition.getFallbackTarget(),
                    ignored -> targetIndexes.size()
            );
        }
        String[] targets = new String[targetIndexes.size()];
        for (Map.Entry<String, Integer> entry : targetIndexes.entrySet()) {
            targets[entry.getValue()] = entry.getKey();
        }
        return new Legend(List.copyOf(compiled), Map.copyOf(exact), targets, fallbackIndex);
    }

    private static TargetMatch matchTarget(
            IrisImageMap definition,
            Legend legend,
            int rgb,
            int sourceX,
            int sourceZ
    ) {
        LegendEntry exact = legend.exact().get(rgb);
        if (exact != null) {
            return new TargetMatch(exact.targetIndex(), false);
        }

        double toleranceSquared = definition.getColorTolerance() * definition.getColorTolerance();
        LegendEntry matched = null;
        List<String> ambiguous = new ArrayList<>();
        if (toleranceSquared > 0D) {
            for (LegendEntry candidate : legend.entries()) {
                if (colorDistanceSquared(rgb, candidate.rgb()) <= toleranceSquared) {
                    if (matched == null) {
                        matched = candidate;
                    }
                    ambiguous.add(candidate.encoded());
                }
            }
        }
        if (ambiguous.size() > 1) {
            throw new IrisImageMapValidationException(
                    "Color " + encodeColor(rgb) + " at " + sourceX + "," + sourceZ
                            + " ambiguously matches " + String.join(", ", ambiguous)
            );
        }
        if (matched != null) {
            return new TargetMatch(matched.targetIndex(), false);
        }

        int targetIndex = switch (definition.getUnknownColor()) {
            case ERROR -> throw new IrisImageMapValidationException(
                    "Color " + encodeColor(rgb) + " at " + sourceX + "," + sourceZ + " is absent from the legend"
            );
            case FALLBACK -> legend.fallbackIndex();
            case IGNORE -> -1;
        };
        return new TargetMatch(targetIndex, true);
    }

    private static int countClippedPixels(
            IrisImageMap definition,
            CompiledIrisImageMap.FloatTiles values,
            int size
    ) {
        if (!definition.isClamp()
                || (definition.getType() != IrisImageMapType.GRAYSCALE_HEIGHT
                && definition.getType() != IrisImageMapType.RGB_HEIGHT)) {
            return 0;
        }
        double minimum = definition.getMinimumHeight();
        double maximum = definition.getMaximumHeight();
        double range = maximum - minimum;
        double offset = definition.getVerticalOffset();
        int clipped = 0;
        for (int index = 0; index < size; index++) {
            double height = minimum + (values.get(index) * range) + offset;
            if (height < minimum || height > maximum) {
                clipped++;
            }
        }
        return clipped;
    }

    private static void validateAlphaPixel(IrisImageMap definition, int sourceX, int sourceZ, double alpha) {
        if (definition.getAlpha() == IrisImageMapAlpha.ERROR && alpha < 1D) {
            throw new IrisImageMapValidationException(
                    "Transparent pixel at " + sourceX + "," + sourceZ + " violates alpha=ERROR"
            );
        }
    }

    private static double threshold(double value, double threshold, double falloff) {
        if (falloff == 0D) {
            return value >= threshold ? 1D : 0D;
        }
        return clamp01((value - threshold) / falloff);
    }

    private static void smooth(CompiledIrisImageMap.FloatTiles values, int width, int height, int radius) {
        float[] line = new float[Math.max(width, height)];
        int diameter = (radius * 2) + 1;
        for (int sourceZ = 0; sourceZ < height; sourceZ++) {
            int rowOffset = sourceZ * width;
            for (int sourceX = 0; sourceX < width; sourceX++) {
                line[sourceX] = values.get(rowOffset + sourceX);
            }
            double sum = 0D;
            for (int offset = -radius; offset <= radius; offset++) {
                int sourceX = Math.max(0, Math.min(width - 1, offset));
                sum += line[sourceX];
            }
            for (int sourceX = 0; sourceX < width; sourceX++) {
                values.set(rowOffset + sourceX, (float) (sum / diameter));
                int leavingX = Math.max(0, Math.min(width - 1, sourceX - radius));
                int enteringX = Math.max(0, Math.min(width - 1, sourceX + radius + 1));
                sum += line[enteringX] - line[leavingX];
            }
        }

        for (int sourceX = 0; sourceX < width; sourceX++) {
            for (int sourceZ = 0; sourceZ < height; sourceZ++) {
                line[sourceZ] = values.get((sourceZ * width) + sourceX);
            }
            double sum = 0D;
            for (int offset = -radius; offset <= radius; offset++) {
                int sourceZ = Math.max(0, Math.min(height - 1, offset));
                sum += line[sourceZ];
            }
            for (int sourceZ = 0; sourceZ < height; sourceZ++) {
                values.set((sourceZ * width) + sourceX, (float) (sum / diameter));
                int leavingZ = Math.max(0, Math.min(height - 1, sourceZ - radius));
                int enteringZ = Math.max(0, Math.min(height - 1, sourceZ + radius + 1));
                sum += line[enteringZ] - line[leavingZ];
            }
        }
    }

    private static SourceDigest hashSource(IrisImage image) {
        HashAccumulator hash = new HashAccumulator();
        hash.addString(image.getFormat());
        hash.addString(image.getColorMode().name());
        hash.addInt(image.getWidth());
        hash.addInt(image.getHeight());
        hash.addInt(image.getColorComponentCount());
        hash.addInt(image.getChannelCount());
        hash.addInt(image.getBitDepth());
        hash.addBoolean(image.hasAlpha());
        double minimumAlpha = 1D;
        double maximumAlpha = image.hasAlpha() ? 0D : 1D;
        for (int sourceZ = 0; sourceZ < image.getHeight(); sourceZ++) {
            for (int sourceX = 0; sourceX < image.getWidth(); sourceX++) {
                double alpha = image.getAlphaNormalized(sourceX, sourceZ);
                minimumAlpha = Math.min(minimumAlpha, alpha);
                maximumAlpha = Math.max(maximumAlpha, alpha);
                for (int band = 0; band < image.getChannelCount(); band++) {
                    hash.addInt(image.getBandSample(sourceX, sourceZ, band));
                }
            }
        }
        return new SourceDigest(hash.finish(), minimumAlpha, maximumAlpha);
    }

    private static String hashContent(IrisImageMap definition, IrisImageMapSourceMetadata metadata) {
        HashAccumulator hash = new HashAccumulator();
        hash.addString(metadata.decodedContentHash());
        hash.addString(definition.getSource());
        hash.addString(definition.getType().name());
        hash.addDouble(definition.getBlocksPerPixel());
        hash.addDouble(definition.getOrigin().getX());
        hash.addDouble(definition.getOrigin().getZ());
        hash.addDouble(definition.getSourceOrigin().getX());
        hash.addDouble(definition.getSourceOrigin().getZ());
        hash.addString(definition.getRotation().name());
        hash.addBoolean(definition.isMirrorX());
        hash.addBoolean(definition.isMirrorZ());
        hash.addString(definition.getSampling().name());
        hash.addString(definition.getOutOfBounds().name());
        hash.addDouble(definition.getFallbackValue());
        hash.addString(definition.getFallbackTarget());
        hash.addString(definition.getAlpha().name());
        hash.addDouble(definition.getMinimumHeight());
        hash.addDouble(definition.getMaximumHeight());
        hash.addDouble(definition.getVerticalOffset());
        hash.addBoolean(definition.isClamp());
        hash.addBoolean(definition.isInverted());
        hash.addDouble(definition.getCurveExponent());
        hash.addInt(definition.getSmoothingRadius());
        hash.addDouble(definition.getThreshold());
        hash.addDouble(definition.getFalloff());
        hash.addDouble(definition.getColorTolerance());
        hash.addString(definition.getUnknownColor().name());

        KMap<String, String> configuredColors = definition.getColors();
        List<Map.Entry<String, String>> colors = configuredColors == null
                ? new ArrayList<>()
                : new ArrayList<>(configuredColors.entrySet());
        colors.sort(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER));
        hash.addInt(colors.size());
        for (Map.Entry<String, String> entry : colors) {
            hash.addString(entry.getKey().toUpperCase());
            hash.addString(entry.getValue());
        }
        return hash.finish();
    }

    private static CompiledIrisImageMap.SourceTransform compileSourceTransform(IrisImageMap definition) {
        double sourceXFromWorldX;
        double sourceXFromWorldZ;
        double sourceZFromWorldX;
        double sourceZFromWorldZ;
        switch (definition.getRotation()) {
            case DEG_0 -> {
                sourceXFromWorldX = 1D;
                sourceXFromWorldZ = 0D;
                sourceZFromWorldX = 0D;
                sourceZFromWorldZ = 1D;
            }
            case DEG_90 -> {
                sourceXFromWorldX = 0D;
                sourceXFromWorldZ = 1D;
                sourceZFromWorldX = -1D;
                sourceZFromWorldZ = 0D;
            }
            case DEG_180 -> {
                sourceXFromWorldX = -1D;
                sourceXFromWorldZ = 0D;
                sourceZFromWorldX = 0D;
                sourceZFromWorldZ = -1D;
            }
            case DEG_270 -> {
                sourceXFromWorldX = 0D;
                sourceXFromWorldZ = -1D;
                sourceZFromWorldX = 1D;
                sourceZFromWorldZ = 0D;
            }
            default -> throw new IrisImageMapValidationException(
                    "Unsupported image-map rotation " + definition.getRotation()
            );
        }
        if (definition.isMirrorX()) {
            sourceXFromWorldX = -sourceXFromWorldX;
            sourceXFromWorldZ = -sourceXFromWorldZ;
        }
        if (definition.isMirrorZ()) {
            sourceZFromWorldX = -sourceZFromWorldX;
            sourceZFromWorldZ = -sourceZFromWorldZ;
        }
        double inverseScale = 1D / definition.getBlocksPerPixel();
        sourceXFromWorldX *= inverseScale;
        sourceXFromWorldZ *= inverseScale;
        sourceZFromWorldX *= inverseScale;
        sourceZFromWorldZ *= inverseScale;
        double sourceXOffset = definition.getSourceOrigin().getX()
                - (sourceXFromWorldX * definition.getOrigin().getX())
                - (sourceXFromWorldZ * definition.getOrigin().getZ());
        double sourceZOffset = definition.getSourceOrigin().getZ()
                - (sourceZFromWorldX * definition.getOrigin().getX())
                - (sourceZFromWorldZ * definition.getOrigin().getZ());
        if (!Double.isFinite(sourceXFromWorldX)
                || !Double.isFinite(sourceXFromWorldZ)
                || !Double.isFinite(sourceXOffset)
                || !Double.isFinite(sourceZFromWorldX)
                || !Double.isFinite(sourceZFromWorldZ)
                || !Double.isFinite(sourceZOffset)) {
            throw new IrisImageMapValidationException("Image-map coordinate transform must remain finite");
        }
        return new CompiledIrisImageMap.SourceTransform(
                sourceXFromWorldX,
                sourceXFromWorldZ,
                sourceXOffset,
                sourceZFromWorldX,
                sourceZFromWorldZ,
                sourceZOffset
        );
    }

    private static IrisImageMap snapshot(IrisImageMap definition) {
        KMap<String, String> configuredColors = definition.getColors();
        KMap<String, String> colors = configuredColors == null ? new KMap<>() : new KMap<>(configuredColors);
        return new IrisImageMap()
                .setSource(definition.getSource())
                .setType(definition.getType())
                .setBlocksPerPixel(definition.getBlocksPerPixel())
                .setOrigin(new IrisImageMapOrigin(definition.getOrigin().getX(), definition.getOrigin().getZ()))
                .setSourceOrigin(new IrisImageMapOrigin(
                        definition.getSourceOrigin().getX(),
                        definition.getSourceOrigin().getZ()
                ))
                .setRotation(definition.getRotation())
                .setMirrorX(definition.isMirrorX())
                .setMirrorZ(definition.isMirrorZ())
                .setSampling(definition.getSampling())
                .setOutOfBounds(definition.getOutOfBounds())
                .setFallbackValue(definition.getFallbackValue())
                .setFallbackTarget(definition.getFallbackTarget())
                .setAlpha(definition.getAlpha())
                .setMinimumHeight(definition.getMinimumHeight())
                .setMaximumHeight(definition.getMaximumHeight())
                .setVerticalOffset(definition.getVerticalOffset())
                .setClamp(definition.isClamp())
                .setInverted(definition.isInverted())
                .setCurveExponent(definition.getCurveExponent())
                .setSmoothingRadius(definition.getSmoothingRadius())
                .setThreshold(definition.getThreshold())
                .setFalloff(definition.getFalloff())
                .setColorTolerance(definition.getColorTolerance())
                .setUnknownColor(definition.getUnknownColor())
                .setColors(colors);
    }

    private static double colorDistanceSquared(int first, int second) {
        int red = ((first >>> 16) & 0xFF) - ((second >>> 16) & 0xFF);
        int green = ((first >>> 8) & 0xFF) - ((second >>> 8) & 0xFF);
        int blue = (first & 0xFF) - (second & 0xFF);
        return (red * red) + (green * green) + (blue * blue);
    }

    private static String encodeColor(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    private static boolean finiteAtLeast(double value, double minimum) {
        return Double.isFinite(value) && value >= minimum;
    }

    private static boolean range(double value, double minimum, double maximum) {
        return Double.isFinite(value) && value >= minimum && value <= maximum;
    }

    private static double clamp01(double value) {
        return Math.max(0D, Math.min(1D, value));
    }

    private record MutableLegendEntry(int rgb, String encoded, String target) {
    }

    private record LegendEntry(int rgb, String encoded, String target, int targetIndex) {
    }

    private record Legend(
            List<LegendEntry> entries,
            Map<Integer, LegendEntry> exact,
            String[] targets,
            int fallbackIndex
    ) {
    }

    private record CompiledTargets(
            CompiledIrisImageMap.IntTiles indices,
            String[] targets,
            int unknownColorPixelCount
    ) {
    }

    private record TargetMatch(int targetIndex, boolean unknown) {
    }

    private record SourceDigest(String hash, double minimumAlpha, double maximumAlpha) {
    }

    private static final class HashAccumulator {
        private final MessageDigest digest;

        private HashAccumulator() {
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is unavailable", exception);
            }
        }

        private void addBoolean(boolean value) {
            digest.update((byte) (value ? 1 : 0));
        }

        private void addInt(int value) {
            digest.update((byte) (value >>> 24));
            digest.update((byte) (value >>> 16));
            digest.update((byte) (value >>> 8));
            digest.update((byte) value);
        }

        private void addLong(long value) {
            digest.update((byte) (value >>> 56));
            digest.update((byte) (value >>> 48));
            digest.update((byte) (value >>> 40));
            digest.update((byte) (value >>> 32));
            digest.update((byte) (value >>> 24));
            digest.update((byte) (value >>> 16));
            digest.update((byte) (value >>> 8));
            digest.update((byte) value);
        }

        private void addDouble(double value) {
            addLong(Double.doubleToLongBits(value));
        }

        private void addString(String value) {
            if (value == null) {
                addInt(-1);
                return;
            }
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            addInt(bytes.length);
            digest.update(bytes);
        }

        private String finish() {
            return HexFormat.of().formatHex(digest.digest());
        }
    }
}
