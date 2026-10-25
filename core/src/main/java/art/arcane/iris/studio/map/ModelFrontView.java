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

package art.arcane.iris.studio.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A block model drawn flat as seen from the south, each box's south face with its own part of the texture. Small
 * shapes like sea pickles and candles keep all their parts on one texture, which only makes sense mapped onto the
 * boxes. Tilted boxes are drawn untilted, which is close for the thin crossed planes such shapes use.
 */
final class ModelFrontView {
    private static final double MODEL_UNITS = 16D;
    private static final String FRONT = "south";
    private static final String TOP = "up";
    private static final double MOSTLY_COVERED_SHARE = 0.5D;
    private static final int X = 0;
    private static final int Y = 1;
    private static final int Z = 2;

    /**
     * Reads a texture's image by its sprite name.
     */
    @FunctionalInterface
    interface TextureImages {
        BufferedImage read(String texture) throws IOException;
    }

    private final BufferedImage image;
    private final int tintIndex;

    private ModelFrontView(BufferedImage image, int tintIndex) {
        this.image = image;
        this.tintIndex = tintIndex;
    }

    /**
     * @return the front view, or null when the model has no front faces with a texture
     */
    static ModelFrontView draw(JsonArray elements, Map<String, String> textures, TextureImages images) throws IOException {
        List<FrontFace> faces = frontFacesNearestLast(elements, textures);
        if (faces.isEmpty()) {
            return null;
        }
        Map<String, BufferedImage> loaded = new HashMap<>();
        for (FrontFace face : faces) {
            BufferedImage texture = images.read(face.texture());
            if (texture != null) {
                loaded.put(face.texture(), texture);
            }
        }
        if (loaded.isEmpty()) {
            return null;
        }
        int size = loaded.values().iterator().next().getWidth();
        BufferedImage front = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        int tintIndex = BlockTextures.NO_TINT_INDEX;
        for (FrontFace face : faces) {
            BufferedImage texture = loaded.get(face.texture());
            if (texture != null) {
                paint(face, texture, front);
                tintIndex = tintIndex == BlockTextures.NO_TINT_INDEX ? face.tintIndex() : tintIndex;
            }
        }
        return hasVisiblePixel(front) ? new ModelFrontView(front, tintIndex) : null;
    }

    /**
     * Whether the model's top faces cover most of its spot, like a slab, a carpet or a pressure plate does, rather than
     * being a small thing standing on it.
     */
    static boolean fillsMostOfItsSpotFromAbove(JsonArray elements) {
        int size = (int) MODEL_UNITS;
        return topCoveredPixels(elements, size) >= size * size * MOSTLY_COVERED_SHARE;
    }

    private static int topCoveredPixels(JsonArray elements, int size) {
        boolean[] covered = new boolean[size * size];
        for (JsonElement element : elements) {
            JsonObject box = element.getAsJsonObject();
            JsonObject faces = box.getAsJsonObject("faces");
            if (faces == null || !faces.has(TOP)) {
                continue;
            }
            JsonArray from = box.getAsJsonArray("from");
            JsonArray to = box.getAsJsonArray("to");
            fill(covered, size, pixel(from.get(X), size), pixel(to.get(X), size), pixel(from.get(Z), size), pixel(to.get(Z), size));
        }
        int count = 0;
        for (boolean pixelCovered : covered) {
            count += pixelCovered ? 1 : 0;
        }
        return count;
    }

    BufferedImage image() {
        return image;
    }

    int tintIndex() {
        return tintIndex;
    }

    // Faces further south are nearer the viewer, so they are painted over the ones behind them.
    private static List<FrontFace> frontFacesNearestLast(JsonArray elements, Map<String, String> textures) {
        List<FrontFace> faces = new ArrayList<>();
        for (JsonElement element : elements) {
            JsonObject box = element.getAsJsonObject();
            JsonObject boxFaces = box.getAsJsonObject("faces");
            if (boxFaces == null || !boxFaces.has(FRONT)) {
                continue;
            }
            JsonObject face = boxFaces.getAsJsonObject(FRONT);
            String texture = BlockTextures.dereference(face.get("texture").getAsString(), textures);
            if (texture != null) {
                faces.add(FrontFace.of(box, face, texture));
            }
        }
        faces.sort(Comparator.comparingDouble(FrontFace::depth));
        return faces;
    }

    // Each pixel samples the texture at its centre, along the face's UVs, which run backwards on mirrored faces.
    private static void paint(FrontFace face, BufferedImage texture, BufferedImage front) {
        int size = front.getWidth();
        double scale = size / MODEL_UNITS;
        double left = face.left() * scale;
        double right = face.right() * scale;
        double top = face.top() * scale;
        double bottom = face.bottom() * scale;
        int textureSize = texture.getWidth();
        for (int y = (int) Math.round(top); y < Math.round(bottom); y++) {
            double alongV = (y + 0.5D - top) / (bottom - top);
            int textureY = texel(face.v1() + alongV * (face.v2() - face.v1()), textureSize);
            for (int x = (int) Math.round(left); x < Math.round(right); x++) {
                double alongU = (x + 0.5D - left) / (right - left);
                int textureX = texel(face.u1() + alongU * (face.u2() - face.u1()), textureSize);
                int argb = texture.getRGB(textureX, textureY);
                if (argb >>> 24 != 0 && x >= 0 && y >= 0 && x < size && y < size) {
                    front.setRGB(x, y, argb);
                }
            }
        }
    }

    // Animated textures stack their frames, so the first frame is as tall as the texture is wide.
    private static int texel(double modelUnits, int textureSize) {
        return Math.clamp((int) Math.floor(modelUnits / MODEL_UNITS * textureSize), 0, textureSize - 1);
    }

    private static boolean hasVisiblePixel(BufferedImage front) {
        for (int y = 0; y < front.getHeight(); y++) {
            for (int x = 0; x < front.getWidth(); x++) {
                if (front.getRGB(x, y) >>> 24 != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int pixel(JsonElement modelUnits, int size) {
        return (int) Math.round(modelUnits.getAsDouble() / MODEL_UNITS * size);
    }

    private static void fill(boolean[] covered, int size, int fromX, int toX, int fromZ, int toZ) {
        for (int z = Math.max(0, fromZ); z < Math.min(size, toZ); z++) {
            for (int x = Math.max(0, fromX); x < Math.min(size, toX); x++) {
                covered[z * size + x] = true;
            }
        }
    }

    /**
     * A south face in model units, with the image's y running down from the model's top.
     */
    private record FrontFace(String texture, int tintIndex, double left, double right, double top, double bottom, double depth,
                             double u1, double v1, double u2, double v2) {
        static FrontFace of(JsonObject box, JsonObject face, String texture) {
            JsonArray from = box.getAsJsonArray("from");
            JsonArray to = box.getAsJsonArray("to");
            double left = from.get(X).getAsDouble();
            double right = to.get(X).getAsDouble();
            double top = MODEL_UNITS - to.get(Y).getAsDouble();
            double bottom = MODEL_UNITS - from.get(Y).getAsDouble();
            double[] uv = face.has("uv") ? uvOf(face.getAsJsonArray("uv")) : new double[]{left, top, right, bottom};
            return new FrontFace(texture, BlockTextures.tintIndexOf(face), left, right, top, bottom, to.get(Z).getAsDouble(),
                    uv[0], uv[1], uv[2], uv[3]);
        }

        private static double[] uvOf(JsonArray uv) {
            return new double[]{uv.get(0).getAsDouble(), uv.get(1).getAsDouble(), uv.get(2).getAsDouble(), uv.get(3).getAsDouble()};
        }
    }
}
