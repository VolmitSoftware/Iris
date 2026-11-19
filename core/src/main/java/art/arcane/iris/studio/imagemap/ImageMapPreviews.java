/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.studio.imagemap;

import art.arcane.iris.generation.image.CompiledIrisImageMap;
import art.arcane.iris.generation.image.IrisImageMap;
import art.arcane.iris.generation.image.IrisImageMapOrigin;
import art.arcane.iris.generation.image.IrisImageMapRotation;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.studio.workspace.ImageMapStudioExporter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

/** The pack's image-map presets as the studio lists, describes and previews them over the world area they cover. */
public final class ImageMapPreviews {
    private static final int BACKGROUND_COLOR = 0x252936;
    private static final int MAP_ERROR_COLOR = 0xFF3DA8;
    private static final int SMALLEST_PREVIEW_PIXELS = 64;
    private static final int LARGEST_PREVIEW_PIXELS = 2048;

    private ImageMapPreviews() {
    }

    public record Entry(String key, String name, String group, int variants) {
    }

    /** @param bounds the world area the map covers: minimum X, minimum Z, maximum X, maximum Z */
    public record Details(String key, String type, String source, int width, int height, double blocksPerPixel, double[] bounds,
                          List<String> facts) {
    }

    public static List<Entry> entries(Engine engine) {
        String[] keys = engine.getData().getImageMapLoader().getPossibleKeys();
        if (keys == null) {
            return List.of();
        }
        return Arrays.stream(keys).sorted().map(key -> new Entry(key, key.substring(key.lastIndexOf('/') + 1), folderOf(key), 1)).toList();
    }

    public static Details details(Engine engine, String key) throws IOException {
        Loaded loaded = load(engine, key);
        IrisImageMap definition = loaded.definition();
        List<String> facts = new ArrayList<>();
        facts.add("Source " + definition.getSource() + ".png, " + loaded.compiled().getSourceWidth() + " × " + loaded.compiled().getSourceHeight() + " pixels");
        facts.add(definition.getBlocksPerPixel() + " blocks per pixel");
        if (definition.getType().name().endsWith("HEIGHT")) {
            facts.add("Heights " + definition.getMinimumHeight() + " to " + definition.getMaximumHeight());
        }
        if (loaded.compiled().getClippedPixelCount() > 0) {
            facts.add(loaded.compiled().getClippedPixelCount() + " pixels clipped to the height range");
        }
        if (loaded.compiled().getUnknownColorPixelCount() > 0) {
            facts.add(loaded.compiled().getUnknownColorPixelCount() + " pixels of a colour the legend does not name");
        }
        return new Details(key, readable(definition.getType().name()), definition.getSource(), loaded.compiled().getSourceWidth(),
                loaded.compiled().getSourceHeight(), definition.getBlocksPerPixel(), worldBounds(loaded), List.copyOf(facts));
    }

    /** A PNG of the world area the map covers, {@code pixels} across its longer side. */
    public static byte[] preview(Engine engine, String key, int pixels) throws IOException {
        if (pixels < SMALLEST_PREVIEW_PIXELS || pixels > LARGEST_PREVIEW_PIXELS) {
            throw new IllegalArgumentException("A preview is " + SMALLEST_PREVIEW_PIXELS + " to " + LARGEST_PREVIEW_PIXELS + " pixels");
        }
        Loaded loaded = load(engine, key);
        double[] bounds = worldBounds(loaded);
        double spanX = bounds[2] - bounds[0];
        double spanZ = bounds[3] - bounds[1];
        double blocksPerPixel = Math.max(spanX, spanZ) / pixels;
        int width = Math.max(1, (int) Math.round(spanX / blocksPerPixel));
        int height = Math.max(1, (int) Math.round(spanZ / blocksPerPixel));
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                image.setRGB(column, row, colorAt(loaded, bounds[0] + column * blocksPerPixel, bounds[1] + row * blocksPerPixel));
            }
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }

    private static int colorAt(Loaded loaded, double worldX, double worldZ) {
        CompiledIrisImageMap compiled = loaded.compiled();
        if (!compiled.containsWorld(worldX, worldZ)) {
            return BACKGROUND_COLOR;
        }
        try {
            return switch (loaded.definition().getType()) {
                case GRAYSCALE_HEIGHT, RGB_HEIGHT -> heightColor(compiled.sampleHeight(worldX, worldZ),
                        loaded.definition().getMinimumHeight(), loaded.definition().getMaximumHeight());
                case COLOR_MAP -> targetColor(compiled.sampleTarget(worldX, worldZ));
                case BINARY_MASK, GRAYSCALE_MASK, ALPHA_MASK -> grayscale(compiled.sampleNormalized(worldX, worldZ));
            };
        } catch (RuntimeException unsampleable) {
            return MAP_ERROR_COLOR;
        }
    }

    private static Loaded load(Engine engine, String key) throws IOException {
        IrisData data = engine.getData();
        IrisImageMap loaded = data.getImageMapLoader().load(key);
        if (loaded == null) {
            throw new NoSuchElementException("No image map " + key);
        }
        IrisImageMap definition = data.getGson().fromJson(data.getGson().toJson(loaded), IrisImageMap.class);
        File source = data.getImageLoader().findFile(definition.getSource());
        if (source == null || !source.isFile()) {
            throw new NoSuchElementException("The source PNG " + definition.getSource() + " is missing");
        }
        CompiledIrisImageMap compiled = ImageMapStudioExporter.preview(source.toPath(), definition).compiled();
        return new Loaded(definition, compiled);
    }

    private static double[] worldBounds(Loaded loaded) {
        double[][] corners = {
                sourceToWorld(loaded.definition(), 0, 0),
                sourceToWorld(loaded.definition(), loaded.compiled().getSourceWidth(), 0),
                sourceToWorld(loaded.definition(), loaded.compiled().getSourceWidth(), loaded.compiled().getSourceHeight()),
                sourceToWorld(loaded.definition(), 0, loaded.compiled().getSourceHeight())
        };
        double[] bounds = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (double[] corner : corners) {
            bounds[0] = Math.min(bounds[0], corner[0]);
            bounds[1] = Math.min(bounds[1], corner[1]);
            bounds[2] = Math.max(bounds[2], corner[0]);
            bounds[3] = Math.max(bounds[3], corner[1]);
        }
        return bounds;
    }

    private static double[] sourceToWorld(IrisImageMap definition, double sourceX, double sourceZ) {
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
        double[] rotated = rotated(x, z, definition.getRotation());
        return new double[]{origin.getX() + rotated[0] * definition.getBlocksPerPixel(), origin.getZ() + rotated[1] * definition.getBlocksPerPixel()};
    }

    private static double[] rotated(double x, double z, IrisImageMapRotation rotation) {
        return switch (rotation) {
            case DEG_0 -> new double[]{x, z};
            case DEG_90 -> new double[]{-z, x};
            case DEG_180 -> new double[]{-x, -z};
            case DEG_270 -> new double[]{z, -x};
        };
    }

    private static int targetColor(String target) {
        if (target == null) {
            return BACKGROUND_COLOR;
        }
        int hash = target.hashCode();
        int red = 72 + Math.floorMod(hash, 152);
        int green = 72 + Math.floorMod(hash >>> 8, 152);
        int blue = 72 + Math.floorMod(hash >>> 16, 152);
        return (red << 16) | (green << 8) | blue;
    }

    private static int heightColor(double height, double minimum, double maximum) {
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
        return ((int) red << 16) | ((int) green << 8) | (int) blue;
    }

    private static int grayscale(double normalized) {
        int level = (int) Math.round(Math.max(0D, Math.min(1D, normalized)) * 255D);
        return (level << 16) | (level << 8) | level;
    }

    private static String folderOf(String key) {
        int slash = key.lastIndexOf('/');
        return slash < 0 ? "" : key.substring(0, slash);
    }

    private static String readable(String constantName) {
        String lower = constantName.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private record Loaded(IrisImageMap definition, CompiledIrisImageMap compiled) {
    }
}
