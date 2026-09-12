package art.arcane.iris.studio.view;

import art.arcane.iris.generation.image.IrisImage;
import art.arcane.iris.generation.image.IrisImageMap;
import art.arcane.iris.generation.image.IrisImageMapOrigin;
import art.arcane.iris.generation.image.IrisImageMapMask;
import art.arcane.iris.generation.image.IrisImageMapMaskOperation;
import art.arcane.iris.generation.image.IrisImageMapRotation;
import art.arcane.iris.generation.image.IrisImageMapType;
import art.arcane.iris.generation.image.IrisImageMapUnknownColor;
import art.arcane.iris.world.IrisWorldBoundary;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class ImageMapStudioModel {
    private static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{6}");
    private static final Pattern KEY_CHARACTER = Pattern.compile("[^a-z0-9/._-]");

    private ImageMapStudioModel() {
    }

    public static SourceMetadata inspect(Path path, BufferedImage image, String format) {
        return inspect(path, image, format, "not inspected", 1D, 1D);
    }

    public static SourceMetadata inspect(
            Path path,
            BufferedImage image,
            String format,
            String colorProfile,
            double minimumAlpha,
            double maximumAlpha
    ) {
        IrisImage irisImage = new IrisImage(image, format);
        return new SourceMetadata(
                path.toAbsolutePath().normalize(),
                irisImage.getFormat(),
                irisImage.getWidth(),
                irisImage.getHeight(),
                (long) irisImage.getWidth() * irisImage.getHeight(),
                irisImage.getColorMode().name(),
                irisImage.getColorComponentCount(),
                irisImage.getChannelCount(),
                irisImage.getBitDepth(),
                irisImage.hasAlpha(),
                minimumAlpha,
                maximumAlpha,
                colorProfile
        );
    }

    public static KMap<String, String> legend(List<LegendRow> rows) {
        KMap<String, String> colors = new KMap<>();
        for (LegendRow row : rows) {
            String color = row.color() == null ? "" : row.color().trim().toUpperCase(Locale.ROOT);
            String target = row.target() == null ? "" : row.target().trim();
            if (color.isEmpty() && target.isEmpty()) {
                continue;
            }
            if (!COLOR.matcher(color).matches()) {
                throw new IllegalArgumentException("Legend color must use #RRGGBB syntax: " + color);
            }
            if (target.isEmpty()) {
                throw new IllegalArgumentException("Legend target must not be blank for " + color);
            }
            String previous = colors.put(color, target);
            if (previous != null) {
                throw new IllegalArgumentException("Legend contains duplicate color " + color);
            }
        }
        return colors;
    }

    public static List<LegendRow> legendRows(IrisImageMap definition) {
        List<LegendRow> rows = new ArrayList<>();
        if (definition.getColors() == null) {
            return rows;
        }
        for (Map.Entry<String, String> entry : definition.getColors().entrySet()) {
            rows.add(new LegendRow(entry.getKey().toUpperCase(Locale.ROOT), entry.getValue()));
        }
        rows.sort(Comparator.comparing(LegendRow::color));
        return rows;
    }

    public static IrisImageMap normalizeTypeSettings(IrisImageMap definition) {
        IrisImageMapType type = definition.getType();
        if (type == null) {
            return definition;
        }
        IrisImageMap defaults = new IrisImageMap();
        switch (type) {
            case GRAYSCALE_HEIGHT, RGB_HEIGHT -> definition
                    .setThreshold(defaults.getThreshold())
                    .setFalloff(defaults.getFalloff())
                    .setColorTolerance(defaults.getColorTolerance())
                    .setUnknownColor(IrisImageMapUnknownColor.ERROR)
                    .setColors(new KMap<>());
            case COLOR_MAP -> definition
                    .setMinimumHeight(defaults.getMinimumHeight())
                    .setMaximumHeight(defaults.getMaximumHeight())
                    .setVerticalOffset(defaults.getVerticalOffset())
                    .setClamp(defaults.isClamp())
                    .setInverted(defaults.isInverted())
                    .setCurveExponent(defaults.getCurveExponent())
                    .setSmoothingRadius(defaults.getSmoothingRadius())
                    .setThreshold(defaults.getThreshold())
                    .setFalloff(defaults.getFalloff());
            case BINARY_MASK, GRAYSCALE_MASK, ALPHA_MASK -> {
                definition
                        .setMinimumHeight(defaults.getMinimumHeight())
                        .setMaximumHeight(defaults.getMaximumHeight())
                        .setVerticalOffset(defaults.getVerticalOffset())
                        .setClamp(defaults.isClamp())
                        .setColorTolerance(defaults.getColorTolerance())
                        .setUnknownColor(IrisImageMapUnknownColor.ERROR)
                        .setColors(new KMap<>());
                if (type != IrisImageMapType.BINARY_MASK) {
                    definition
                            .setThreshold(defaults.getThreshold())
                            .setFalloff(defaults.getFalloff());
                }
            }
        }
        return definition;
    }

    public static KList<IrisImageMapMask> masks(List<MaskRow> rows) {
        KList<IrisImageMapMask> masks = new KList<>();
        for (MaskRow row : rows) {
            masks.add(new IrisImageMapMask()
                    .setMap(row.map())
                    .setOperation(row.operation())
                    .setInverted(row.inverted())
                    .setThreshold(row.threshold())
                    .setFalloff(row.falloff()));
        }
        return masks;
    }

    public static MaskRow maskRow(
            String mapValue,
            Object operationValue,
            boolean inverted,
            Object thresholdValue,
            Object falloffValue
    ) {
        String map = mapValue == null ? "" : mapValue.trim();
        if (map.isBlank()) {
            throw new IllegalArgumentException("Composed mask binding key must not be blank");
        }
        IrisImageMapMaskOperation operation;
        try {
            operation = IrisImageMapMaskOperation.valueOf(
                    String.valueOf(operationValue).trim().toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown mask operation for '" + map + "'", exception);
        }
        double threshold = unitValue(thresholdValue, "Mask threshold for '" + map + "'");
        double falloff = unitValue(falloffValue, "Mask falloff for '" + map + "'");
        return new MaskRow(map, operation, inverted, threshold, falloff);
    }

    public static List<MaskRow> maskRows(List<IrisImageMapMask> masks) {
        List<MaskRow> rows = new ArrayList<>();
        if (masks == null) {
            return rows;
        }
        for (IrisImageMapMask mask : masks) {
            if (mask != null) {
                rows.add(new MaskRow(
                        mask.getMap(),
                        mask.getOperation(),
                        mask.isInverted(),
                        mask.getThreshold(),
                        mask.getFalloff()
                ));
            }
        }
        return rows;
    }

    public static String safeKey(String fileName) {
        String normalized = fileName == null ? "image-map" : fileName.trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".png")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        normalized = KEY_CHARACTER.matcher(normalized).replaceAll("-");
        while (normalized.contains("--")) {
            normalized = normalized.replace("--", "-");
        }
        normalized = normalized.replaceAll("^[./_-]+|[./_-]+$", "");
        return normalized.isBlank() ? "image-map" : normalized;
    }

    public static List<String> warnings(
            IrisImageMap definition,
            int sourceWidth,
            int sourceHeight,
            IrisWorldBoundary boundary
    ) {
        List<String> warnings = new ArrayList<>();
        double blocksPerPixel = definition.getBlocksPerPixel();
        if (Double.isFinite(blocksPerPixel)) {
            if (blocksPerPixel < 1D) {
                warnings.add("Sub-block source scale will discard detail during block generation.");
            } else if (blocksPerPixel > 512D) {
                warnings.add("Each source pixel spans more than one Minecraft region (512 blocks).");
            }
        }
        long worldWidth = Math.round(sourceWidth * blocksPerPixel);
        long worldHeight = Math.round(sourceHeight * blocksPerPixel);
        if (worldWidth < 16L || worldHeight < 16L) {
            warnings.add("The transformed source covers less than one complete chunk on at least one axis.");
        }
        if (boundary != null) {
            Point2D.Double[] polygon = sourceWorldCorners(definition, sourceWidth, sourceHeight);
            Point2D.Double[] borderCorners = new Point2D.Double[]{
                    new Point2D.Double(boundary.minimumX(), boundary.minimumZ()),
                    new Point2D.Double(boundary.maximumX(), boundary.minimumZ()),
                    new Point2D.Double(boundary.maximumX(), boundary.maximumZ()),
                    new Point2D.Double(boundary.minimumX(), boundary.maximumZ())
            };
            for (Point2D.Double corner : borderCorners) {
                if (!contains(polygon, corner)) {
                    warnings.add("The configured world boundary is not fully covered by the source image.");
                    break;
                }
            }
            double minimumX = Double.POSITIVE_INFINITY;
            double maximumX = Double.NEGATIVE_INFINITY;
            double minimumZ = Double.POSITIVE_INFINITY;
            double maximumZ = Double.NEGATIVE_INFINITY;
            boolean outsideBoundary = false;
            for (Point2D.Double corner : polygon) {
                minimumX = Math.min(minimumX, corner.x);
                maximumX = Math.max(maximumX, corner.x);
                minimumZ = Math.min(minimumZ, corner.y);
                maximumZ = Math.max(maximumZ, corner.y);
                if (corner.x < boundary.minimumX() || corner.x > boundary.maximumX()
                        || corner.y < boundary.minimumZ() || corner.y > boundary.maximumZ()) {
                    outsideBoundary = true;
                }
            }
            if (outsideBoundary) {
                warnings.add("The source image extends outside the world boundary; mapped content there is not playable.");
            }
            double footprintWidth = maximumX - minimumX;
            double footprintHeight = maximumZ - minimumZ;
            if (footprintWidth > boundary.getSize() * 2D || footprintHeight > boundary.getSize() * 2D) {
                warnings.add("The transformed source is substantially larger than the playable boundary.");
            } else if (footprintWidth < boundary.getSize() * 0.5D
                    || footprintHeight < boundary.getSize() * 0.5D) {
                warnings.add("The transformed source is substantially smaller than the playable boundary.");
            }
        }
        return List.copyOf(warnings);
    }

    public static Point2D.Double[] sourceWorldCorners(IrisImageMap definition, int width, int height) {
        return new Point2D.Double[]{
                sourceToWorld(definition, 0D, 0D),
                sourceToWorld(definition, width, 0D),
                sourceToWorld(definition, width, height),
                sourceToWorld(definition, 0D, height)
        };
    }

    public static Point2D.Double sourceToWorld(IrisImageMap definition, double sourceX, double sourceZ) {
        IrisImageMapOrigin origin = definition.getOrigin();
        IrisImageMapOrigin sourceOrigin = definition.getSourceOrigin();
        double x = sourceX - sourceOrigin.getX();
        double z = sourceZ - sourceOrigin.getZ();
        if (definition.isMirrorX()) {
            x = -x;
        }
        if (definition.isMirrorZ()) {
            z = -z;
        }
        Point2D.Double rotated = rotateForward(x, z, definition.getRotation());
        return new Point2D.Double(
                origin.getX() + (rotated.x * definition.getBlocksPerPixel()),
                origin.getZ() + (rotated.y * definition.getBlocksPerPixel())
        );
    }

    public static int targetColor(String target) {
        if (target == null) {
            return 0xFF252936;
        }
        int hash = target.hashCode();
        int red = 72 + Math.floorMod(hash, 152);
        int green = 72 + Math.floorMod(hash >>> 8, 152);
        int blue = 72 + Math.floorMod(hash >>> 16, 152);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    public static int heightColor(double height, double minimum, double maximum) {
        double range = maximum - minimum;
        double normalized = range == 0D ? 0.5D : Math.max(0D, Math.min(1D, (height - minimum) / range));
        double red;
        double green;
        double blue;
        if (normalized < 0.5D) {
            double factor = normalized * 2D;
            red = 38D + (45D * factor);
            green = 77D + (111D * factor);
            blue = 132D - (42D * factor);
        } else {
            double factor = (normalized - 0.5D) * 2D;
            red = 83D + (169D * factor);
            green = 188D + (58D * factor);
            blue = 90D + (150D * factor);
        }
        return 0xFF000000 | ((int) red << 16) | ((int) green << 8) | (int) blue;
    }

    private static Point2D.Double rotateForward(double x, double z, IrisImageMapRotation rotation) {
        return switch (rotation) {
            case DEG_0 -> new Point2D.Double(x, z);
            case DEG_90 -> new Point2D.Double(-z, x);
            case DEG_180 -> new Point2D.Double(-x, -z);
            case DEG_270 -> new Point2D.Double(z, -x);
        };
    }

    private static boolean contains(Point2D.Double[] polygon, Point2D.Double point) {
        boolean inside = false;
        int previous = polygon.length - 1;
        for (int index = 0; index < polygon.length; index++) {
            Point2D.Double current = polygon[index];
            Point2D.Double before = polygon[previous];
            if (Line2D.ptSegDist(before.x, before.y, current.x, current.y, point.x, point.y) <= 0.0000001D) {
                return true;
            }
            boolean crosses = (current.y > point.y) != (before.y > point.y);
            if (crosses) {
                double intersection = ((before.x - current.x) * (point.y - current.y)
                        / (before.y - current.y)) + current.x;
                if (point.x < intersection) {
                    inside = !inside;
                }
            }
            previous = index;
        }
        return inside;
    }

    private static double unitValue(Object value, String name) {
        double number;
        try {
            number = Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be a number", exception);
        }
        if (!Double.isFinite(number) || number < 0D || number > 1D) {
            throw new IllegalArgumentException(name + " must be finite and within 0..1");
        }
        return number;
    }

    public record SourceMetadata(
            Path path,
            String format,
            int width,
            int height,
            long pixels,
            String colorMode,
            int colorComponents,
            int channels,
            int bitDepth,
            boolean alpha,
            double minimumAlpha,
            double maximumAlpha,
            String colorProfile
    ) {
        public String summary() {
            String alphaSummary = alpha
                    ? String.format(Locale.ROOT, "yes (%.1f%%..%.1f%%)", minimumAlpha * 100D, maximumAlpha * 100D)
                    : "no";
            return format.toUpperCase(Locale.ROOT) + "  |  " + width + " × " + height
                    + "  |  " + pixels + " pixels  |  " + colorMode
                    + "  |  " + bitDepth + "-bit  |  " + channels + " channel(s)  |  alpha "
                    + alphaSummary + "  |  profile " + colorProfile;
        }
    }

    public record LegendRow(String color, String target) {
    }

    public record MaskRow(
            String map,
            IrisImageMapMaskOperation operation,
            boolean inverted,
            double threshold,
            double falloff
    ) {
    }
}
