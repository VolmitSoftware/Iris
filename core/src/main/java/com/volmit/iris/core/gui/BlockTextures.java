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
import com.google.gson.JsonParser;
import org.bukkit.block.data.BlockData;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class BlockTextures {
    private static final String DEFAULT_NAMESPACE = "minecraft";
    private static final int MAX_MODEL_PARENTS = 16;
    private static final int MAX_TEXTURE_REFERENCES = 16;
    private static final int GRASS_TINT = 0x91BD59;
    private static final int FOLIAGE_TINT = 0x77AB2F;
    private static final int BIRCH_FOLIAGE_TINT = 0x80A755;
    private static final int SPRUCE_FOLIAGE_TINT = 0x619961;
    private static final int WATER_TINT = 0x3F76E4;
    private static final Object SHARED_LOCK = new Object();
    private static CompletableFuture<BlockTextures> shared;

    private final ZipFile clientJar;
    private final Map<String, Optional<BlockTexture>> topTextures = new ConcurrentHashMap<>();

    private BlockTextures(ZipFile clientJar) {
        this.clientJar = clientJar;
    }

    static CompletableFuture<BlockTextures> shared() {
        synchronized (SHARED_LOCK) {
            if (shared == null || shared.isCompletedExceptionally()) {
                shared = MinecraftClientJar.open().thenApply(BlockTextures::new);
            }
            return shared;
        }
    }

    Optional<BlockTexture> topTexture(BlockData block) {
        return topTextures.computeIfAbsent(block.getAsString(true), this::resolveTopTexture);
    }

    private Optional<BlockTexture> resolveTopTexture(String blockState) {
        try {
            BlockStateId state = BlockStateId.parse(blockState);
            JsonObject definition = readJson(assetPath(state.namespace() + ":" + state.name(), "blockstates", ".json"));
            JsonObject variant = definition == null ? null : pickVariant(definition, state.properties());
            if (variant == null || !variant.has("model")) {
                return Optional.empty();
            }
            ModelFace face = findVisibleFace(variant.get("model").getAsString(), faceSeenFromAbove(variant));
            if (face == null) {
                return Optional.empty();
            }
            BufferedImage image = readImage(assetPath(face.texture(), "textures", ".png"));
            if (image == null) {
                return Optional.empty();
            }
            boolean tinted = face.tinted() || state.name().equals("water");
            return Optional.of(BlockTexture.fromImage(image, tinted ? tintFor(state.name()) : BlockTexture.NO_TINT));
        } catch (IOException | RuntimeException error) {
            return Optional.empty();
        }
    }

    // A block rotated on its x axis, like a sideways log, shows a side face on top.
    private static String faceSeenFromAbove(JsonObject variant) {
        int rotationX = variant.has("x") ? Math.floorMod(variant.get("x").getAsInt(), 360) : 0;
        return switch (rotationX) {
            case 90, 270 -> "north";
            case 180 -> "down";
            default -> "up";
        };
    }

    private ModelFace findVisibleFace(String modelReference, String faceName) throws IOException {
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

        JsonObject face = elements == null ? null : topmostFace(elements, faceName);
        if (face == null) {
            String particle = dereference("#particle", textures);
            return particle == null ? null : new ModelFace(particle, elements != null && anyFaceTinted(elements));
        }
        String texture = dereference(face.get("texture").getAsString(), textures);
        return texture == null ? null : new ModelFace(texture, face.has("tintindex"));
    }

    private static boolean anyFaceTinted(JsonArray elements) {
        for (JsonElement element : elements) {
            JsonObject faces = element.getAsJsonObject().getAsJsonObject("faces");
            if (faces == null) {
                continue;
            }
            for (Map.Entry<String, JsonElement> face : faces.entrySet()) {
                if (face.getValue().getAsJsonObject().has("tintindex")) {
                    return true;
                }
            }
        }
        return false;
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

    private static int tintFor(String blockName) {
        if (blockName.contains("water")) {
            return WATER_TINT;
        }
        if (blockName.equals("birch_leaves")) {
            return BIRCH_FOLIAGE_TINT;
        }
        if (blockName.equals("spruce_leaves")) {
            return SPRUCE_FOLIAGE_TINT;
        }
        if (blockName.contains("leaves") || blockName.contains("vine")) {
            return FOLIAGE_TINT;
        }
        return GRASS_TINT;
    }

    private static String assetPath(String resource, String folder, String extension) {
        int separator = resource.indexOf(':');
        String namespace = separator < 0 ? DEFAULT_NAMESPACE : resource.substring(0, separator);
        String path = separator < 0 ? resource : resource.substring(separator + 1);
        return "assets/" + namespace + "/" + folder + "/" + path + extension;
    }

    private JsonObject readJson(String path) throws IOException {
        ZipEntry entry = clientJar.getEntry(path);
        if (entry == null) {
            return null;
        }
        try (InputStreamReader reader = new InputStreamReader(clientJar.getInputStream(entry), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private BufferedImage readImage(String path) throws IOException {
        ZipEntry entry = clientJar.getEntry(path);
        if (entry == null) {
            return null;
        }
        try (InputStream input = clientJar.getInputStream(entry)) {
            return ImageIO.read(input);
        }
    }

    private record ModelFace(String texture, boolean tinted) {
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

    record BlockTexture(int size, int[] pixels, int averageColor) {
        static final int NO_TINT = 0xFFFFFF;
        private static final int OPAQUE_ALPHA = 128;
        private static final double GAP_SHADE = 0.55D;

        // Animated textures stack their frames vertically, so only the first square frame is used.
        static BlockTexture fromImage(BufferedImage image, int tint) {
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
                    int color = applyTint(argb & 0xFFFFFF, tint);
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
            return new BlockTexture(size, pixels, average);
        }

        int texelAt(double worldX, double worldZ) {
            int x = Math.min(size - 1, (int) ((worldX - Math.floor(worldX)) * size));
            int z = Math.min(size - 1, (int) ((worldZ - Math.floor(worldZ)) * size));
            return pixels[z * size + x];
        }

        BufferedImage toImage() {
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
            image.setRGB(0, 0, size, size, pixels, 0, size);
            return image;
        }

        private static int applyTint(int color, int tint) {
            int red = ((color >>> 16) & 0xFF) * ((tint >>> 16) & 0xFF) / 255;
            int green = ((color >>> 8) & 0xFF) * ((tint >>> 8) & 0xFF) / 255;
            int blue = (color & 0xFF) * (tint & 0xFF) / 255;
            return (red << 16) | (green << 8) | blue;
        }
    }
}
