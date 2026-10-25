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
import art.arcane.iris.studio.map.BlockModel.BoxCorners;
import art.arcane.iris.studio.map.BlockModel.Face;
import art.arcane.iris.studio.map.BlockModel.TextureMapping;
import art.arcane.iris.studio.map.BlockTextures.BlockStateId;
import art.arcane.iris.studio.map.BlockTextures.BlockTexture;
import art.arcane.iris.studio.map.BlockTextures.ModelDefinition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads a block's look the way Minecraft does: every model part the blockstate picks, every box with its own texture
 * and UVs, tilted boxes, the blockstate's turn with or without locked textures, and which faces a neighbour hides.
 */
final class BlockModelBuilder {
    private static final double MODEL_UNITS = 16D;
    private static final double BLOCK_CENTER = 0.5D;
    private static final int QUARTER_TURN = 90;

    private final BlockTextures textures;
    private final EntityBlockShapes entityShapes;

    BlockModelBuilder(BlockTextures textures) {
        this.textures = textures;
        this.entityShapes = new EntityBlockShapes(textures);
    }

    Optional<BlockModel> build(String blockState) {
        try {
            BlockStateId state = BlockStateId.parse(blockState);
            List<JsonObject> placements = placementsOf(state);
            if (placements == null) {
                return Optional.empty();
            }
            List<Face> faces = new ArrayList<>();
            for (JsonObject placement : placements) {
                faces.addAll(placedFaces(state, placement));
            }
            faces.addAll(entityShapes.facesAddedTo(state));
            return Optional.of(BlockModel.of(faces));
        } catch (IOException | RuntimeException error) {
            return Optional.empty();
        }
    }

    /**
     * A box with Minecraft's default UVs on each side, for shapes the game draws in code.
     */
    static List<Face> box(Vec3 fromPixels, Vec3 toPixels, BlockTexture texture, boolean culledBySides) {
        List<Face> faces = new ArrayList<>();
        for (CubeFace side : CubeFace.values()) {
            FaceUv uv = FaceUv.byDefault(side, fromPixels, toPixels);
            Face face = boxFace(fromPixels, toPixels, side, texture, uv).withShade(side.shade());
            faces.add(culledBySides ? face.withCullFace(side) : face);
        }
        return faces;
    }

    // A variant names one model; a multipart blockstate, like a fence's, adds a part for every condition that holds.
    private List<JsonObject> placementsOf(BlockStateId state) throws IOException {
        JsonObject definition = textures.readJson(BlockTextures.assetPath(state.namespace() + ":" + state.name(), "blockstates", ".json"));
        if (definition == null) {
            return null;
        }
        List<JsonObject> placements = new ArrayList<>();
        if (definition.has("variants")) {
            for (Map.Entry<String, JsonElement> variant : definition.getAsJsonObject("variants").entrySet()) {
                if (BlockTextures.variantMatches(variant.getKey(), state.properties())) {
                    addPlacement(placements, variant.getValue());
                    break;
                }
            }
        }
        if (definition.has("multipart")) {
            for (JsonElement part : definition.getAsJsonArray("multipart")) {
                JsonObject rule = part.getAsJsonObject();
                if (!rule.has("when") || BlockTextures.conditionMatches(rule.getAsJsonObject("when"), state.properties())) {
                    addPlacement(placements, rule.get("apply"));
                }
            }
        }
        return placements;
    }

    private static void addPlacement(List<JsonObject> placements, JsonElement models) {
        JsonObject first = BlockTextures.firstModel(models);
        if (first != null && first.has("model")) {
            placements.add(first);
        }
    }

    private List<Face> placedFaces(BlockStateId state, JsonObject placement) throws IOException {
        ModelDefinition model = textures.readModel(placement.get("model").getAsString());
        if (model.elements() == null) {
            return entityShapes.facesOf(state, model);
        }
        BlockRotation rotation = BlockRotation.of(placement);
        List<Face> faces = new ArrayList<>();
        for (JsonElement element : model.elements()) {
            addElementFaces(faces, state, model, element.getAsJsonObject(), rotation);
        }
        return faces;
    }

    private void addElementFaces(List<Face> faces, BlockStateId state, ModelDefinition model, JsonObject element, BlockRotation rotation) {
        JsonObject elementFaces = element.getAsJsonObject("faces");
        if (elementFaces == null) {
            return;
        }
        Vec3 from = vector(element.getAsJsonArray("from"));
        Vec3 to = vector(element.getAsJsonArray("to"));
        Transform tilt = tiltOf(element);
        boolean shaded = !element.has("shade") || element.get("shade").getAsBoolean();
        CubeFace shadeSide = element.has("shade_direction_override")
                ? CubeFace.named(element.get("shade_direction_override").getAsString()) : null;
        for (Map.Entry<String, JsonElement> entry : elementFaces.entrySet()) {
            CubeFace side = CubeFace.named(entry.getKey());
            JsonObject faceJson = entry.getValue().getAsJsonObject();
            if (side == null || !faceJson.has("texture")) {
                continue;
            }
            String textureName = BlockTextures.dereference(faceJson.get("texture").getAsString(), model.textures());
            if (textureName == null) {
                continue;
            }
            Optional<BlockTexture> texture = textures.faceTexture(state, textureName, BlockTextures.tintIndexOf(faceJson));
            if (texture.isEmpty()) {
                continue;
            }
            FaceUv uv = FaceUv.of(faceJson, side, from, to);
            if (rotation.locksTextures()) {
                uv = uv.lockedTo(side, rotation);
            }
            Face face = boxFace(from, to, side, texture.get(), uv).transformed(tilt);
            if (faceJson.has("cullface")) {
                face = face.withCullFace(CubeFace.named(faceJson.get("cullface").getAsString()));
            }
            face = face.transformed(rotation.transform());
            faces.add(face.withShade(shadeOf(face, shaded, shadeSide, rotation)));
        }
    }

    private static Face boxFace(Vec3 fromPixels, Vec3 toPixels, CubeFace side, BlockTexture texture, FaceUv uv) {
        Vec3[] corners = BoxCorners.of(fromPixels.times(1D / MODEL_UNITS), toPixels.times(1D / MODEL_UNITS), side);
        return new Face(corners[0], corners[3].minus(corners[0]), corners[1].minus(corners[0]), side.direction(),
                texture, uv.mapping(), side.shade(), null);
    }

    private static double shadeOf(Face face, boolean shaded, CubeFace shadeSide, BlockRotation rotation) {
        if (!shaded) {
            return CubeFace.UP.shade();
        }
        if (shadeSide != null) {
            return CubeFace.nearest(rotation.transform().direction(shadeSide.direction())).shade();
        }
        return CubeFace.nearest(face.normal()).shade();
    }

    // A box can be tilted around a point, on one axis or, since 1.21.?, on all three; rescaling stretches a tilted
    // plant back to the full block width.
    private static Transform tiltOf(JsonObject element) {
        if (!element.has("rotation")) {
            return Transform.IDENTITY;
        }
        JsonObject rotation = element.getAsJsonObject("rotation");
        Vec3 origin = rotation.has("origin") ? vector(rotation.getAsJsonArray("origin")).times(1D / MODEL_UNITS) : new Vec3(BLOCK_CENTER, BLOCK_CENTER, BLOCK_CENTER);
        Transform around = Transform.IDENTITY.translate(origin.x(), origin.y(), origin.z());
        Transform back = Transform.IDENTITY.translate(-origin.x(), -origin.y(), -origin.z());
        if (!rotation.has("axis")) {
            return around
                    .rotateX(numberOr(rotation, "x", 0D))
                    .rotateY(numberOr(rotation, "y", 0D))
                    .rotateZ(numberOr(rotation, "z", 0D))
                    .then(back);
        }
        String axis = rotation.get("axis").getAsString();
        double angle = numberOr(rotation, "angle", 0D);
        boolean rescale = rotation.has("rescale") && rotation.get("rescale").getAsBoolean();
        double stretch = rescale ? 1D / Math.cos(Math.toRadians(angle)) : 1D;
        Transform stretched = around.scale(axis.equals("x") ? 1D : stretch, axis.equals("y") ? 1D : stretch, axis.equals("z") ? 1D : stretch);
        Transform tilted = switch (axis) {
            case "x" -> stretched.rotateX(angle);
            case "y" -> stretched.rotateY(angle);
            default -> stretched.rotateZ(angle);
        };
        return tilted.then(back);
    }

    private static double numberOr(JsonObject object, String key, double fallback) {
        return object.has(key) ? object.get(key).getAsDouble() : fallback;
    }

    private static Vec3 vector(JsonArray values) {
        return new Vec3(values.get(0).getAsDouble(), values.get(1).getAsDouble(), values.get(2).getAsDouble());
    }

    /**
     * How a blockstate turns its model: around the x axis first, then the y axis, both in quarter turns against the
     * right-hand rule. With uvlock the textures stay put while the shape turns.
     */
    private record BlockRotation(int x, int y, boolean uvlock) {
        static BlockRotation of(JsonObject placement) {
            int x = placement.has("x") ? Math.floorMod(placement.get("x").getAsInt(), 360) : 0;
            int y = placement.has("y") ? Math.floorMod(placement.get("y").getAsInt(), 360) : 0;
            return new BlockRotation(x, y, placement.has("uvlock") && placement.get("uvlock").getAsBoolean());
        }

        boolean locksTextures() {
            return uvlock && (x != 0 || y != 0);
        }

        Transform transform() {
            return Transform.IDENTITY
                    .translate(BLOCK_CENTER, BLOCK_CENTER, BLOCK_CENTER)
                    .then(turn())
                    .translate(-BLOCK_CENTER, -BLOCK_CENTER, -BLOCK_CENTER);
        }

        Transform turn() {
            return Transform.IDENTITY.rotateY(-y).rotateX(-x);
        }

        Transform inverseTurn() {
            return Transform.IDENTITY.rotateX(x).rotateY(y);
        }
    }

    /**
     * A face's corner UVs in model pixels and the quarter turn of the texture on it.
     */
    private record FaceUv(double u1, double v1, double u2, double v2, int rotation) {
        static FaceUv of(JsonObject face, CubeFace side, Vec3 from, Vec3 to) {
            int rotation = face.has("rotation") ? Math.floorMod(face.get("rotation").getAsInt(), 360) : 0;
            if (!face.has("uv")) {
                return byDefault(side, from, to).withRotation(rotation);
            }
            JsonArray uv = face.getAsJsonArray("uv");
            return new FaceUv(uv.get(0).getAsDouble(), uv.get(1).getAsDouble(), uv.get(2).getAsDouble(), uv.get(3).getAsDouble(), rotation);
        }

        // The part of the texture the face would cover if the texture were laid over the whole block side.
        static FaceUv byDefault(CubeFace side, Vec3 from, Vec3 to) {
            double full = MODEL_UNITS;
            return switch (side) {
                case DOWN -> new FaceUv(from.x(), full - to.z(), to.x(), full - from.z(), 0);
                case UP -> new FaceUv(from.x(), from.z(), to.x(), to.z(), 0);
                case NORTH -> new FaceUv(full - to.x(), full - to.y(), full - from.x(), full - from.y(), 0);
                case SOUTH -> new FaceUv(from.x(), full - to.y(), to.x(), full - from.y(), 0);
                case WEST -> new FaceUv(from.z(), full - to.y(), to.z(), full - from.y(), 0);
                case EAST -> new FaceUv(full - to.z(), full - to.y(), full - from.z(), full - from.y(), 0);
            };
        }

        FaceUv withRotation(int changedRotation) {
            return new FaceUv(u1, v1, u2, v2, changedRotation);
        }

        // Each corner takes the UV a quarter turn further along for every quarter turn of the texture.
        TextureMapping mapping() {
            double[] origin = cornerUv(0);
            double[] alongU = cornerUv(3);
            double[] alongV = cornerUv(1);
            return new TextureMapping(origin[0] / MODEL_UNITS, origin[1] / MODEL_UNITS,
                    (alongU[0] - origin[0]) / MODEL_UNITS, (alongU[1] - origin[1]) / MODEL_UNITS,
                    (alongV[0] - origin[0]) / MODEL_UNITS, (alongV[1] - origin[1]) / MODEL_UNITS);
        }

        private double[] cornerUv(int corner) {
            return switch ((corner + rotation / QUARTER_TURN) % 4) {
                case 0 -> new double[]{u1, v1};
                case 1 -> new double[]{u1, v2};
                case 2 -> new double[]{u2, v2};
                default -> new double[]{u2, v1};
            };
        }

        /**
         * Minecraft's uvlock: the UVs are carried through the reverse of the blockstate's turn, so the texture keeps
         * its place in the world while the face moves to another side.
         */
        FaceUv lockedTo(CubeFace side, BlockRotation blockRotation) {
            CubeFace turnedSide = CubeFace.nearest(blockRotation.turn().direction(side.direction()));
            Transform uvTurn = Transform.IDENTITY
                    .translate(BLOCK_CENTER, BLOCK_CENTER, BLOCK_CENTER)
                    .then(faceFromWorld(side))
                    .then(blockRotation.inverseTurn())
                    .then(worldFromFace(turnedSide))
                    .translate(-BLOCK_CENTER, -BLOCK_CENTER, -BLOCK_CENTER);
            Vec3 first = uvTurn.point(new Vec3(u1 / MODEL_UNITS, v1 / MODEL_UNITS, 0D)).times(MODEL_UNITS);
            Vec3 second = uvTurn.point(new Vec3(u2 / MODEL_UNITS, v2 / MODEL_UNITS, 0D)).times(MODEL_UNITS);
            boolean keepsUOrder = Math.signum(u2 - u1) == Math.signum(second.x() - first.x());
            boolean keepsVOrder = Math.signum(v2 - v1) == Math.signum(second.y() - first.y());
            Vec3 textureRight = uvTurn.direction(new Vec3(Math.cos(Math.toRadians(rotation)), Math.sin(Math.toRadians(rotation)), 0D));
            long quarterTurns = Math.round(Math.toDegrees(Math.atan2(textureRight.y(), textureRight.x())) / QUARTER_TURN);
            return new FaceUv(
                    keepsUOrder ? first.x() : second.x(), keepsVOrder ? first.y() : second.y(),
                    keepsUOrder ? second.x() : first.x(), keepsVOrder ? second.y() : first.y(),
                    (int) Math.floorMod(-quarterTurns * QUARTER_TURN, 360L));
        }

        // Minecraft lays every side's texture out as if it were the south side, turned into place.
        private static Transform worldFromFace(CubeFace side) {
            return switch (side) {
                case SOUTH -> Transform.IDENTITY;
                case EAST -> Transform.IDENTITY.rotateY(90D);
                case WEST -> Transform.IDENTITY.rotateY(-90D);
                case NORTH -> Transform.IDENTITY.rotateY(180D);
                case UP -> Transform.IDENTITY.rotateX(-90D);
                case DOWN -> Transform.IDENTITY.rotateX(90D);
            };
        }

        private static Transform faceFromWorld(CubeFace side) {
            return switch (side) {
                case SOUTH -> Transform.IDENTITY;
                case EAST -> Transform.IDENTITY.rotateY(-90D);
                case WEST -> Transform.IDENTITY.rotateY(90D);
                case NORTH -> Transform.IDENTITY.rotateY(180D);
                case UP -> Transform.IDENTITY.rotateX(90D);
                case DOWN -> Transform.IDENTITY.rotateX(-90D);
            };
        }
    }
}
