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

package com.volmit.iris.core.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipFile;

final class BlockTextures {
    private static final String DEFAULT_NAMESPACE = "minecraft";
    private static final int MAX_MODEL_PARENTS = 16;
    private static final int MAX_TEXTURE_REFERENCES = 16;
    static final int NO_TINT_INDEX = -1;
    private static final Object SHARED_LOCK = new Object();
    private static CompletableFuture<BlockTextures> shared;

    private final ZipFile clientJar;
    private final MinecraftBiomeColors biomeColors;
    private final Map<String, Optional<ResolvedTexture>> resolvedTextures = new ConcurrentHashMap<>();
    private final Map<String, Optional<BlockModel>> resolvedModels = new ConcurrentHashMap<>();
    private final Map<String, Optional<BlockTexture>> faceTextures = new ConcurrentHashMap<>();
    private final BlockModelBuilder modelBuilder = new BlockModelBuilder(this);

    private BlockTextures(ZipFile clientJar, MinecraftBiomeColors biomeColors) {
        this.clientJar = clientJar;
        this.biomeColors = biomeColors;
    }

    static CompletableFuture<BlockTextures> shared() {
        synchronized (SHARED_LOCK) {
            if (shared == null || shared.isCompletedExceptionally()) {
                shared = MinecraftClientJar.open().thenApply(BlockTextures::load);
            }
            return shared;
        }
    }

    private static BlockTextures load(ZipFile clientJar) {
        try {
            return new BlockTextures(clientJar, MinecraftBiomeColors.load(clientJar));
        } catch (IOException error) {
            throw new IllegalStateException("Could not read Minecraft's biome colour maps", error);
        }
    }

    MinecraftBiomeColors biomeColors() {
        return biomeColors;
    }

    /**
     * What the block looks like from above. Plants and other side-on sprites become their average colour, because
     * their sprite would read as stripes across a stack of ground blocks.
     */
    Optional<BlockTexture> topTexture(BlockData block) {
        return resolve(block).map(resolved -> resolved.isSideOnSprite() ? resolved.texture().flattened() : resolved.texture());
    }

    /**
     * Like {@link #topTexture}, but plants keep their sprite, so single decorations on the ground stay recognisable.
     * Two block tall plants give their bottom half, the part standing on the ground.
     */
    Optional<BlockTexture> iconTexture(BlockData block) {
        BlockData standing = block instanceof Bisected ? withHalf(block, Bisected.Half.BOTTOM) : block;
        return resolve(standing).map(ResolvedTexture::texture);
    }

    /**
     * The sprite of a two block tall plant's top half, or nothing for any other block.
     */
    Optional<BlockTexture> upperHalfIcon(BlockData block) {
        if (!(block instanceof Bisected)) {
            return Optional.empty();
        }
        return resolve(withHalf(block, Bisected.Half.TOP)).filter(ResolvedTexture::isSideOnSprite).map(ResolvedTexture::texture);
    }

    /**
     * What the block looks like from every side, for drawing it in 3D.
     */
    Optional<BlockModel> model(BlockData block) {
        return resolvedModels.computeIfAbsent(block.getAsString(false), modelBuilder::build);
    }

    private static BlockData withHalf(BlockData block, Bisected.Half half) {
        BlockData copy = block.clone();
        ((Bisected) copy).setHalf(half);
        return copy;
    }

    // Blockstate files match on every property, so defaults the pack left out have to be spelled out too.
    private Optional<ResolvedTexture> resolve(BlockData block) {
        return resolvedTextures.computeIfAbsent(block.getAsString(false), this::resolveTopTexture);
    }

    private Optional<ResolvedTexture> resolveTopTexture(String blockState) {
        try {
            BlockStateId state = BlockStateId.parse(blockState);
            JsonObject variant = variantOf(state);
            if (variant == null || !variant.has("model")) {
                return Optional.empty();
            }
            ModelFace face = readModel(variant.get("model").getAsString()).visibleFace(faceSeenFromAbove(variant));
            if (face == null) {
                return Optional.empty();
            }
            return readTexture(state, face).map(texture -> new ResolvedTexture(texture, face.isSideOnSprite()));
        } catch (IOException | RuntimeException error) {
            return Optional.empty();
        }
    }

    private JsonObject variantOf(BlockStateId state) throws IOException {
        JsonObject definition = readJson(assetPath(state.namespace() + ":" + state.name(), "blockstates", ".json"));
        return definition == null ? null : pickVariant(definition, state.properties());
    }

    private static int rotationOf(JsonObject variant, String axis) {
        return variant.has(axis) ? Math.floorMod(variant.get(axis).getAsInt(), 360) : 0;
    }

    /**
     * One model face's texture with the block's tint, shared by every block state that uses it.
     */
    Optional<BlockTexture> faceTexture(BlockStateId state, String texture, int tintIndex) {
        int fixedTint = MinecraftBlockTints.fixedTint(state.name(), state.properties(), tintIndex);
        BiomeTint biomeTint = MinecraftBlockTints.biomeTint(state.name(), tintIndex).orElse(null);
        return faceTextures.computeIfAbsent(texture + "|" + fixedTint + "|" + biomeTint, key -> {
            try {
                return readTexture(state, new ModelFace(List.of(texture), tintIndex, false));
            } catch (IOException | RuntimeException error) {
                return Optional.empty();
            }
        });
    }

    /**
     * A texture drawn on a block entity, like a chest, whole and tinted with a fixed colour.
     */
    Optional<BlockTexture> entityTexture(String texture, int tint) {
        return faceTextures.computeIfAbsent(texture + "|entity|" + tint, key -> {
            try {
                BufferedImage image = readImage(assetPath(texture, "textures", ".png"));
                return image == null ? Optional.empty() : Optional.of(BlockTexture.fromWholeImage(image, tint));
            } catch (IOException | RuntimeException error) {
                return Optional.empty();
            }
        });
    }

    private Optional<BlockTexture> readTexture(BlockStateId state, ModelFace face) throws IOException {
        BufferedImage image = readLayeredImage(face.textures());
        if (image == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(BlockTexture.fromImage(
                    image,
                    MinecraftBlockTints.fixedTint(state.name(), state.properties(), face.tintIndex()),
                    MinecraftBlockTints.biomeTint(state.name(), face.tintIndex()).orElse(null)
            ));
        } catch (IllegalArgumentException invisibleTexture) {
            return Optional.empty();
        }
    }

    // A block rotated on its x axis, like a sideways log, shows a side face on top. Blockstates turn the model
    // against the right-hand rule, so 90 degrees brings the south side up.
    private static String faceSeenFromAbove(JsonObject variant) {
        return switch (rotationOf(variant, "x")) {
            case 90 -> "south";
            case 270 -> "north";
            case 180 -> "down";
            default -> "up";
        };
    }

    ModelDefinition readModel(String modelReference) throws IOException {
        Map<String, String> textures = new HashMap<>();
        JsonArray elements = null;
        String current = modelReference;
        for (int depth = 0; current != null && depth < MAX_MODEL_PARENTS; depth++) {
            JsonObject model = readJson(assetPath(current, "models", ".json"));
            if (model == null) {
                break;
            }
            if (model.has("textures")) {
                for (Map.Entry<String, JsonElement> texture : model.getAsJsonObject("textures").entrySet()) {
                    textures.putIfAbsent(texture.getKey(), spriteName(texture.getValue()));
                }
            }
            if (elements == null && model.has("elements")) {
                elements = model.getAsJsonArray("elements");
            }
            current = model.has("parent") ? model.get("parent").getAsString() : null;
        }
        return new ModelDefinition(textures, elements);
    }

    // Since 1.21.? a texture can also be an object naming its sprite, like glass's {"sprite": ..., "force_translucent": true}.
    private static String spriteName(JsonElement texture) {
        return texture.isJsonObject() ? texture.getAsJsonObject().get("sprite").getAsString() : texture.getAsString();
    }

    // Some plants draw a second sprite over the first, like the glowing fireflies on a firefly bush.
    private static List<String> spriteLayers(JsonArray elements, Map<String, String> textures) {
        Set<String> layers = new LinkedHashSet<>();
        for (JsonElement element : elements) {
            JsonObject faces = element.getAsJsonObject().getAsJsonObject("faces");
            if (faces == null) {
                continue;
            }
            for (Map.Entry<String, JsonElement> face : faces.entrySet()) {
                String texture = dereference(face.getValue().getAsJsonObject().get("texture").getAsString(), textures);
                if (texture != null) {
                    layers.add(texture);
                }
            }
        }
        return List.copyOf(layers);
    }

    private static int firstTintIndex(JsonArray elements) {
        for (JsonElement element : elements) {
            JsonObject faces = element.getAsJsonObject().getAsJsonObject("faces");
            if (faces == null) {
                continue;
            }
            for (Map.Entry<String, JsonElement> face : faces.entrySet()) {
                int tintIndex = tintIndexOf(face.getValue().getAsJsonObject());
                if (tintIndex != NO_TINT_INDEX) {
                    return tintIndex;
                }
            }
        }
        return NO_TINT_INDEX;
    }

    static int tintIndexOf(JsonObject face) {
        return face.has("tintindex") ? face.get("tintindex").getAsInt() : NO_TINT_INDEX;
    }

    private static JsonObject topmostFace(JsonArray elements, String faceName) {
        JsonObject best = null;
        double bestHeight = Double.NEGATIVE_INFINITY;
        double bestArea = -1D;
        for (JsonElement element : elements) {
            JsonObject cuboid = element.getAsJsonObject();
            JsonObject faces = cuboid.getAsJsonObject("faces");
            if (faces == null || !faces.has(faceName)) {
                continue;
            }
            JsonArray from = cuboid.getAsJsonArray("from");
            JsonArray to = cuboid.getAsJsonArray("to");
            double height = to.get(1).getAsDouble();
            double area = (to.get(0).getAsDouble() - from.get(0).getAsDouble()) * (to.get(2).getAsDouble() - from.get(2).getAsDouble());
            if (height > bestHeight || (height == bestHeight && area > bestArea)) {
                best = faces.getAsJsonObject(faceName);
                bestHeight = height;
                bestArea = area;
            }
        }
        return best;
    }

    static String dereference(String reference, Map<String, String> textures) {
        String resolved = reference;
        for (int hop = 0; resolved != null && resolved.startsWith("#") && hop < MAX_TEXTURE_REFERENCES; hop++) {
            resolved = textures.get(resolved.substring(1));
        }
        return resolved == null || resolved.startsWith("#") ? null : resolved;
    }

    private static JsonObject pickVariant(JsonObject definition, Map<String, String> properties) {
        if (definition.has("variants")) {
            for (Map.Entry<String, JsonElement> variant : definition.getAsJsonObject("variants").entrySet()) {
                if (variantMatches(variant.getKey(), properties)) {
                    return firstModel(variant.getValue());
                }
            }
            return null;
        }
        if (definition.has("multipart")) {
            for (JsonElement part : definition.getAsJsonArray("multipart")) {
                JsonObject rule = part.getAsJsonObject();
                if (!rule.has("when") || conditionMatches(rule.getAsJsonObject("when"), properties)) {
                    return firstModel(rule.get("apply"));
                }
            }
        }
        return null;
    }

    static boolean variantMatches(String variantKey, Map<String, String> properties) {
        if (variantKey.isEmpty()) {
            return true;
        }
        for (String condition : variantKey.split(",")) {
            String[] pair = condition.split("=", 2);
            if (pair.length != 2 || !pair[1].equals(properties.get(pair[0]))) {
                return false;
            }
        }
        return true;
    }

    static boolean conditionMatches(JsonObject condition, Map<String, String> properties) {
        if (condition.has("OR")) {
            for (JsonElement option : condition.getAsJsonArray("OR")) {
                if (conditionMatches(option.getAsJsonObject(), properties)) {
                    return true;
                }
            }
            return false;
        }
        if (condition.has("AND")) {
            for (JsonElement requirement : condition.getAsJsonArray("AND")) {
                if (!conditionMatches(requirement.getAsJsonObject(), properties)) {
                    return false;
                }
            }
            return true;
        }
        for (Map.Entry<String, JsonElement> requirement : condition.entrySet()) {
            String actual = properties.get(requirement.getKey());
            boolean anyAllowed = false;
            for (String allowed : requirement.getValue().getAsString().split("\\|")) {
                anyAllowed |= allowed.equals(actual);
            }
            if (!anyAllowed) {
                return false;
            }
        }
        return true;
    }

    static JsonObject firstModel(JsonElement models) {
        if (models.isJsonArray()) {
            JsonArray choices = models.getAsJsonArray();
            return choices.isEmpty() ? null : choices.get(0).getAsJsonObject();
        }
        return models.getAsJsonObject();
    }

    static String assetPath(String resource, String folder, String extension) {
        int separator = resource.indexOf(':');
        String namespace = separator < 0 ? DEFAULT_NAMESPACE : resource.substring(0, separator);
        String path = separator < 0 ? resource : resource.substring(separator + 1);
        return "assets/" + namespace + "/" + folder + "/" + path + extension;
    }

    JsonObject readJson(String path) throws IOException {
        return ClientJarFiles.readJson(clientJar, path);
    }

    // Each layer is drawn over the ones before it, using only its first frame when it is animated.
    private BufferedImage readLayeredImage(List<String> layers) throws IOException {
        BufferedImage bottom = readImage(assetPath(layers.get(0), "textures", ".png"));
        if (bottom == null || layers.size() == 1) {
            return bottom;
        }
        int size = bottom.getWidth();
        BufferedImage combined = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D canvas = combined.createGraphics();
        try {
            for (String layer : layers) {
                BufferedImage image = readImage(assetPath(layer, "textures", ".png"));
                if (image != null) {
                    int frameSize = image.getWidth();
                    canvas.drawImage(image, 0, 0, size, size, 0, 0, frameSize, frameSize, null);
                }
            }
        } finally {
            canvas.dispose();
        }
        return combined;
    }

    private BufferedImage readImage(String path) throws IOException {
        return ClientJarFiles.readImage(clientJar, path);
    }

    // Plants and other blocks without a top face only have a side-on sprite, which would read as stripes from above.
    private record ModelFace(List<String> textures, int tintIndex, boolean isSideOnSprite) {
    }

    /**
     * A block model with its parents' textures merged in. Models drawn in code, like chests, have no elements.
     */
    record ModelDefinition(Map<String, String> textures, JsonArray elements) {
        ModelFace visibleFace(String faceName) {
            ModelFace face = sideFace(faceName);
            if (face != null) {
                return face;
            }
            return elements != null && !spriteLayers(elements, textures).isEmpty() ? spriteFace() : particleFace();
        }

        // The face's texture on the model's topmost box, the particle texture for models without boxes, or null when
        // the model leaves that side open.
        ModelFace sideFace(String faceName) {
            if (elements == null) {
                return particleFace();
            }
            JsonObject face = topmostFace(elements, faceName);
            if (face == null) {
                return null;
            }
            String texture = dereference(face.get("texture").getAsString(), textures);
            return texture == null ? null : new ModelFace(List.of(texture), tintIndexOf(face), false);
        }

        ModelFace spriteFace() {
            return new ModelFace(spriteLayers(elements, textures), firstTintIndex(elements), true);
        }

        private ModelFace particleFace() {
            String particle = dereference("#particle", textures);
            return particle == null ? null : new ModelFace(List.of(particle), NO_TINT_INDEX, false);
        }

    }

    private record ResolvedTexture(BlockTexture texture, boolean isSideOnSprite) {
    }

    record BlockStateId(String namespace, String name, Map<String, String> properties) {
        static BlockStateId parse(String blockState) {
            int propertiesStart = blockState.indexOf('[');
            String id = propertiesStart < 0 ? blockState : blockState.substring(0, propertiesStart);
            int separator = id.indexOf(':');
            String namespace = separator < 0 ? DEFAULT_NAMESPACE : id.substring(0, separator);
            String name = separator < 0 ? id : id.substring(separator + 1);
            Map<String, String> properties = new HashMap<>();
            if (propertiesStart >= 0) {
                String body = blockState.substring(propertiesStart + 1, blockState.length() - 1);
                for (String pair : body.split(",")) {
                    String[] keyValue = pair.split("=", 2);
                    if (keyValue.length == 2) {
                        properties.put(keyValue[0], keyValue[1]);
                    }
                }
            }
            return new BlockStateId(namespace, name, properties);
        }
    }

    // Biome tinted textures keep their grey pixels, because the biome colour changes from column to column.
    // See-through pixels get a darker shade of the average colour, and the opaque mask remembers which they were.
    // Textures with partly see-through pixels, like stained glass, ice and water, keep each pixel's alpha too.
    record BlockTexture(int width, int height, int[] pixels, boolean[] opaque, int[] alphas, int averageColor,
                        BiomeTint biomeTint) {
        private static final double GAP_SHADE = 0.55D;
        private static final int FULL_ALPHA = 255;

        // Animated textures stack their frames vertically, so only the first square frame is used.
        static BlockTexture fromImage(BufferedImage image, int fixedTint, BiomeTint biomeTint) {
            return fromPixels(image, image.getWidth(), Math.min(image.getWidth(), image.getHeight()), fixedTint, biomeTint);
        }

        // Entity textures, like a chest's, are laid out for a model and can be wider than they are tall.
        static BlockTexture fromWholeImage(BufferedImage image, int fixedTint) {
            return fromPixels(image, image.getWidth(), image.getHeight(), fixedTint, null);
        }

        static BlockTexture solid(int color) {
            return new BlockTexture(1, 1, new int[]{color}, new boolean[]{true}, null, color, null);
        }

        private static BlockTexture fromPixels(BufferedImage image, int width, int height, int fixedTint, BiomeTint biomeTint) {
            int[] pixels = new int[width * height];
            boolean[] opaque = new boolean[pixels.length];
            int[] alphas = new int[pixels.length];
            boolean translucent = false;
            long red = 0;
            long green = 0;
            long blue = 0;
            int opaqueCount = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = image.getRGB(x, y);
                    int index = y * width + x;
                    int alpha = argb >>> 24;
                    alphas[index] = alpha;
                    if (alpha == 0) {
                        continue;
                    }
                    translucent |= alpha < FULL_ALPHA;
                    int color = multiply(argb & 0xFFFFFF, fixedTint);
                    pixels[index] = color;
                    opaque[index] = true;
                    red += (color >>> 16) & 0xFF;
                    green += (color >>> 8) & 0xFF;
                    blue += color & 0xFF;
                    opaqueCount++;
                }
            }
            if (opaqueCount == 0) {
                throw new IllegalArgumentException("Texture has no visible pixels");
            }
            int average = (int) (red / opaqueCount) << 16 | (int) (green / opaqueCount) << 8 | (int) (blue / opaqueCount);
            int gapColor = BlockPreviewColors.shade(average, GAP_SHADE);
            for (int index = 0; index < pixels.length; index++) {
                if (!opaque[index]) {
                    pixels[index] = gapColor;
                }
            }
            return new BlockTexture(width, height, pixels, opaque, translucent ? alphas : null, average, biomeTint);
        }

        boolean isTranslucent() {
            return alphas != null;
        }

        boolean isFullyOpaque() {
            if (isTranslucent()) {
                return false;
            }
            for (boolean pixelOpaque : opaque) {
                if (!pixelOpaque) {
                    return false;
                }
            }
            return true;
        }

        BlockTexture flattened() {
            int[] flat = new int[pixels.length];
            Arrays.fill(flat, averageColor);
            boolean[] allOpaque = new boolean[pixels.length];
            Arrays.fill(allOpaque, true);
            return new BlockTexture(width, height, flat, allOpaque, null, averageColor, biomeTint);
        }

        BlockTexture tinted(int tint) {
            int[] tintedPixels = new int[pixels.length];
            for (int index = 0; index < pixels.length; index++) {
                tintedPixels[index] = multiply(pixels[index], tint);
            }
            return new BlockTexture(width, height, tintedPixels, opaque, alphas, multiply(averageColor, tint), null);
        }

        int texelAt(double worldX, double worldZ) {
            return pixels[texelIndex(worldX, worldZ)];
        }

        boolean isOpaqueAt(double worldX, double worldZ) {
            return opaque[texelIndex(worldX, worldZ)];
        }

        private int texelIndex(double worldX, double worldZ) {
            int x = Math.min(width - 1, (int) ((worldX - Math.floor(worldX)) * width));
            int z = Math.min(height - 1, (int) ((worldZ - Math.floor(worldZ)) * height));
            return z * width + x;
        }

        BufferedImage toImage() {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            image.setRGB(0, 0, width, height, pixels, 0, width);
            return image;
        }

        // The GPU multiplies the texture by the tint in floating point and rounds back to 8 bits per channel.
        static int multiply(int color, int tint) {
            return multiplyChannel(color, tint, 16) << 16 | multiplyChannel(color, tint, 8) << 8 | multiplyChannel(color, tint, 0);
        }

        private static int multiplyChannel(int color, int tint, int shift) {
            return (((color >>> shift) & 0xFF) * ((tint >>> shift) & 0xFF) + 127) / 255;
        }
    }
}
