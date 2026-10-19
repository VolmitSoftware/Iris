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
import java.util.EnumMap;
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
    private static final int NO_TINT_INDEX = -1;
    private static final Object SHARED_LOCK = new Object();
    private static CompletableFuture<BlockTextures> shared;

    private final ZipFile clientJar;
    private final MinecraftBiomeColors biomeColors;
    private final Map<String, Optional<ResolvedTexture>> resolvedTextures = new ConcurrentHashMap<>();
    private final Map<String, Optional<BlockModel>> resolvedModels = new ConcurrentHashMap<>();

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
        return resolvedModels.computeIfAbsent(block.getAsString(false), this::resolveModel);
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

    private Optional<BlockModel> resolveModel(String blockState) {
        try {
            BlockStateId state = BlockStateId.parse(blockState);
            JsonObject variant = variantOf(state);
            if (variant == null || !variant.has("model")) {
                return Optional.empty();
            }
            ModelDefinition model = readModel(variant.get("model").getAsString());
            if (model.isSprite()) {
                return readTexture(state, model.spriteFace()).map(BlockModel::cross);
            }
            int rotationX = rotationOf(variant, "x");
            int rotationY = rotationOf(variant, "y");
            Map<CubeFace, BlockTexture> faces = new EnumMap<>(CubeFace.class);
            for (CubeFace face : CubeFace.values()) {
                ModelFace modelFace = model.sideFace(face.beforeRotation(rotationX, rotationY).modelName());
                if (modelFace != null) {
                    readTexture(state, modelFace).ifPresent(texture -> faces.put(face, texture));
                }
            }
            return faces.isEmpty() ? Optional.empty() : Optional.of(BlockModel.box(model.box().rotated(rotationX, rotationY), faces));
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

    // A block rotated on its x axis, like a sideways log, shows a side face on top.
    private static String faceSeenFromAbove(JsonObject variant) {
        return switch (rotationOf(variant, "x")) {
            case 90, 270 -> "north";
            case 180 -> "down";
            default -> "up";
        };
    }

    private ModelDefinition readModel(String modelReference) throws IOException {
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
                    textures.putIfAbsent(texture.getKey(), texture.getValue().getAsString());
                }
            }
            if (elements == null && model.has("elements")) {
                elements = model.getAsJsonArray("elements");
            }
            current = model.has("parent") ? model.get("parent").getAsString() : null;
        }
        return new ModelDefinition(textures, elements);
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

    private static int tintIndexOf(JsonObject face) {
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

    private static String dereference(String reference, Map<String, String> textures) {
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

    private static boolean variantMatches(String variantKey, Map<String, String> properties) {
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

    private static boolean conditionMatches(JsonObject condition, Map<String, String> properties) {
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

    private static JsonObject firstModel(JsonElement models) {
        if (models.isJsonArray()) {
            JsonArray choices = models.getAsJsonArray();
            return choices.isEmpty() ? null : choices.get(0).getAsJsonObject();
        }
        return models.getAsJsonObject();
    }

    private static String assetPath(String resource, String folder, String extension) {
        int separator = resource.indexOf(':');
        String namespace = separator < 0 ? DEFAULT_NAMESPACE : resource.substring(0, separator);
        String path = separator < 0 ? resource : resource.substring(separator + 1);
        return "assets/" + namespace + "/" + folder + "/" + path + extension;
    }

    private JsonObject readJson(String path) throws IOException {
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
    private record ModelDefinition(Map<String, String> textures, JsonArray elements) {
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

        // Plants have only upright planes, so nothing of them faces up or down.
        boolean isSprite() {
            return elements != null
                    && topmostFace(elements, CubeFace.UP.modelName()) == null
                    && topmostFace(elements, CubeFace.DOWN.modelName()) == null
                    && !spriteLayers(elements, textures).isEmpty();
        }

        ModelFace spriteFace() {
            return new ModelFace(spriteLayers(elements, textures), firstTintIndex(elements), true);
        }

        private ModelFace particleFace() {
            String particle = dereference("#particle", textures);
            return particle == null ? null : new ModelFace(List.of(particle), NO_TINT_INDEX, false);
        }

        // One box, like a slab or a carpet, keeps its shape; several, like stairs, become a full block.
        BlockModel.Box box() {
            if (elements == null || elements.size() != 1) {
                return BlockModel.Box.FULL;
            }
            JsonObject element = elements.get(0).getAsJsonObject();
            return BlockModel.Box.ofModelElement(coordinates(element.getAsJsonArray("from")), coordinates(element.getAsJsonArray("to")));
        }

        private static double[] coordinates(JsonArray values) {
            return new double[]{values.get(0).getAsDouble(), values.get(1).getAsDouble(), values.get(2).getAsDouble()};
        }
    }

    private record ResolvedTexture(BlockTexture texture, boolean isSideOnSprite) {
    }

    private record BlockStateId(String namespace, String name, Map<String, String> properties) {
        private static BlockStateId parse(String blockState) {
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
    record BlockTexture(int size, int[] pixels, boolean[] opaque, int averageColor, BiomeTint biomeTint) {
        private static final int OPAQUE_ALPHA = 128;
        private static final double GAP_SHADE = 0.55D;

        // Animated textures stack their frames vertically, so only the first square frame is used.
        static BlockTexture fromImage(BufferedImage image, int fixedTint, BiomeTint biomeTint) {
            int size = image.getWidth();
            int[] pixels = new int[size * size];
            boolean[] opaque = new boolean[pixels.length];
            long red = 0;
            long green = 0;
            long blue = 0;
            int opaqueCount = 0;
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    int argb = image.getRGB(x, Math.min(z, image.getHeight() - 1));
                    int index = z * size + x;
                    if ((argb >>> 24) < OPAQUE_ALPHA) {
                        continue;
                    }
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
            return new BlockTexture(size, pixels, opaque, average, biomeTint);
        }

        BlockTexture flattened() {
            int[] flat = new int[pixels.length];
            Arrays.fill(flat, averageColor);
            boolean[] allOpaque = new boolean[pixels.length];
            Arrays.fill(allOpaque, true);
            return new BlockTexture(size, flat, allOpaque, averageColor, biomeTint);
        }

        BlockTexture tinted(int tint) {
            int[] tintedPixels = new int[pixels.length];
            for (int index = 0; index < pixels.length; index++) {
                tintedPixels[index] = multiply(pixels[index], tint);
            }
            return new BlockTexture(size, tintedPixels, opaque, multiply(averageColor, tint), null);
        }

        int texelAt(double worldX, double worldZ) {
            return pixels[texelIndex(worldX, worldZ)];
        }

        boolean isOpaqueAt(double worldX, double worldZ) {
            return opaque[texelIndex(worldX, worldZ)];
        }

        private int texelIndex(double worldX, double worldZ) {
            int x = Math.min(size - 1, (int) ((worldX - Math.floor(worldX)) * size));
            int z = Math.min(size - 1, (int) ((worldZ - Math.floor(worldZ)) * size));
            return z * size + x;
        }

        BufferedImage toImage() {
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
            image.setRGB(0, 0, size, size, pixels, 0, size);
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
