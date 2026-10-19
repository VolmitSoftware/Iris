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

import com.volmit.iris.core.gui.BlockModel.Face;
import com.volmit.iris.core.gui.BlockModel.TextureMapping;
import com.volmit.iris.core.gui.BlockTextures.BlockStateId;
import com.volmit.iris.core.gui.BlockTextures.BlockTexture;
import com.volmit.iris.core.gui.BlockTextures.ModelDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Shapes for the blocks Minecraft draws in code instead of from a model file, like chests, heads, banners and
 * fluids, which fill their block. Chests, heads, banners and the bell use their entity textures laid out the way Minecraft's model parts are;
 * rarer ones are a simple box in their particle texture.
 */
final class EntityBlockShapes {
    private static final Set<String> INVISIBLE = Set.of("air", "cave_air", "void_air", "barrier", "light",
            "structure_void", "moving_piston", "pitcher_crop");
    private static final int END_PORTAL_COLOR = 0x0E1219;
    private static final double SKULL_WALL_OFFSET = 0.25D;
    private static final double ROTATION_SEGMENT_DEGREES = 22.5D;
    private static final double BANNER_SCALE = 2D / 3D;
    private static final double MODEL_UNITS = 16D;
    private static final Map<String, Integer> DYE_COLORS = Map.ofEntries(
            Map.entry("white", 0xF9FFFE), Map.entry("orange", 0xF9801D), Map.entry("magenta", 0xC74EBD),
            Map.entry("light_blue", 0x3AB3DA), Map.entry("yellow", 0xFED83D), Map.entry("lime", 0x80C71F),
            Map.entry("pink", 0xF38BAA), Map.entry("gray", 0x474F52), Map.entry("light_gray", 0x9D9D97),
            Map.entry("cyan", 0x169C9C), Map.entry("purple", 0x8932B8), Map.entry("blue", 0x3C44AA),
            Map.entry("brown", 0x835432), Map.entry("green", 0x5E7C16), Map.entry("red", 0xB02E26),
            Map.entry("black", 0x1D1D21));

    private final BlockTextures textures;

    EntityBlockShapes(BlockTextures textures) {
        this.textures = textures;
    }

    /**
     * The faces of a block whose model has no boxes of its own.
     */
    List<Face> facesOf(BlockStateId state, ModelDefinition model) {
        String name = state.name();
        if (INVISIBLE.contains(name)) {
            return List.of();
        }
        if (name.endsWith("chest")) {
            return chest(state);
        }
        if (name.endsWith("_skull") || name.endsWith("_head")) {
            return head(state, model);
        }
        if (name.endsWith("banner")) {
            return banner(state);
        }
        return switch (name) {
            case "conduit" -> particleBox(state, model, new Vec3(5, 5, 5), new Vec3(11, 11, 11), false);
            case "decorated_pot" -> decoratedPot(state, model);
            case "end_portal" -> BlockModelBuilder.box(new Vec3(0, 0, 0), new Vec3(16, 12, 16), BlockTexture.solid(END_PORTAL_COLOR), true);
            case "end_gateway" -> BlockModelBuilder.box(new Vec3(0, 0, 0), new Vec3(16, 16, 16), BlockTexture.solid(END_PORTAL_COLOR), true);
            default -> name.endsWith("copper_golem_statue")
                    ? particleBox(state, model, new Vec3(4, 0, 4), new Vec3(12, 14, 12), false)
                    : particleBox(state, model, new Vec3(0, 0, 0), new Vec3(16, 16, 16), true);
        };
    }

    /**
     * Parts a block draws in code on top of its model, like the bell hanging in its frame.
     */
    List<Face> facesAddedTo(BlockStateId state) {
        if (!state.name().equals("bell")) {
            return List.of();
        }
        Optional<BlockTexture> texture = textures.entityTexture("entity/bell/bell_body", MinecraftBlockTints.NO_TINT);
        if (texture.isEmpty()) {
            return List.of();
        }
        List<Face> faces = new ArrayList<>();
        faces.addAll(modelPartBox(texture.get(), 0, 0, new Vec3(5, 6, 5), new Vec3(6, 7, 6), Transform.IDENTITY));
        faces.addAll(modelPartBox(texture.get(), 0, 13, new Vec3(4, 4, 4), new Vec3(8, 2, 8), Transform.IDENTITY));
        return faces;
    }

    private List<Face> particleBox(BlockStateId state, ModelDefinition model, Vec3 from, Vec3 to, boolean culledBySides) {
        String particle = BlockTextures.dereference("#particle", model.textures());
        if (particle == null) {
            return List.of();
        }
        return textures.faceTexture(state, particle, BlockTextures.NO_TINT_INDEX)
                .map(texture -> BlockModelBuilder.box(from, to, texture, culledBySides))
                .orElse(List.of());
    }

    private List<Face> decoratedPot(BlockStateId state, ModelDefinition model) {
        List<Face> faces = new ArrayList<>(particleBox(state, model, new Vec3(1, 0, 1), new Vec3(15, 16, 15), false));
        faces.addAll(particleBox(state, model, new Vec3(4, 16, 4), new Vec3(12, 20, 12), false));
        return faces;
    }

    // Single chests are 14 pixels wide; each half of a double chest is 15, reaching over to its partner.
    private List<Face> chest(BlockStateId state) {
        String type = state.properties().getOrDefault("type", "single");
        String suffix = type.equals("single") ? "" : "_" + type;
        Optional<BlockTexture> texture = textures.entityTexture("entity/chest/" + chestTextureName(state.name()) + suffix, MinecraftBlockTints.NO_TINT);
        if (texture.isEmpty()) {
            return List.of();
        }
        Transform place = Transform.IDENTITY
                .translate(0.5D, 0.5D, 0.5D)
                .rotateY(-yRotationOf(state.properties().getOrDefault("facing", "north")))
                .translate(-0.5D, -0.5D, -0.5D);
        double left = type.equals("left") ? 0D : 1D;
        double width = type.equals("single") ? 14D : 15D;
        double lockX = switch (type) {
            case "left" -> 0D;
            case "right" -> 15D;
            default -> 7D;
        };
        double lockWidth = type.equals("single") ? 2D : 1D;
        List<Face> faces = new ArrayList<>();
        faces.addAll(modelPartBox(texture.get(), 0, 19, new Vec3(left, 0, 1), new Vec3(width, 10, 14), place));
        faces.addAll(modelPartBox(texture.get(), 0, 0, new Vec3(left, 9, 1), new Vec3(width, 5, 14), place));
        faces.addAll(modelPartBox(texture.get(), 0, 0, new Vec3(lockX, 7, 15), new Vec3(lockWidth, 4, 1), place));
        return faces;
    }

    private static String chestTextureName(String blockName) {
        String name = blockName.startsWith("waxed_") ? blockName.substring("waxed_".length()) : blockName;
        return switch (name) {
            case "trapped_chest" -> "trapped";
            case "ender_chest" -> "ender";
            case "copper_chest" -> "copper";
            case "exposed_copper_chest" -> "copper_exposed";
            case "weathered_copper_chest" -> "copper_weathered";
            case "oxidized_copper_chest" -> "copper_oxidized";
            default -> "normal";
        };
    }

    // Heads are drawn upside down and mirrored in their model, and turn in sixteenths like signs.
    private List<Face> head(BlockStateId state, ModelDefinition model) {
        boolean onWall = state.name().contains("_wall_");
        String kind = state.name().replace("_wall_", "_");
        Optional<HeadLook> look = HeadLook.of(kind);
        Optional<BlockTexture> texture = look.flatMap(found -> textures.entityTexture(found.texture(), MinecraftBlockTints.NO_TINT));
        if (texture.isEmpty()) {
            return onWall
                    ? particleBox(state, model, new Vec3(4, 4, 8), new Vec3(12, 12, 16), false)
                    : particleBox(state, model, new Vec3(4, 0, 4), new Vec3(12, 8, 12), false);
        }
        Transform place;
        if (onWall) {
            CubeFace facing = CubeFace.named(state.properties().getOrDefault("facing", "north"));
            place = Transform.IDENTITY
                    .translate(0.5D - facing.stepX() * SKULL_WALL_OFFSET, SKULL_WALL_OFFSET, 0.5D - facing.stepZ() * SKULL_WALL_OFFSET)
                    .scale(-1D, -1D, 1D)
                    .rotateY(yRotationOf(facing.opposite().modelName()));
        } else {
            int rotation = Integer.parseInt(state.properties().getOrDefault("rotation", "0"));
            place = Transform.IDENTITY
                    .translate(0.5D, 0D, 0.5D)
                    .scale(-1D, -1D, 1D)
                    .rotateY(rotation * ROTATION_SEGMENT_DEGREES);
        }
        HeadLook found = look.get();
        return modelPartBox(texture.get(), 0, 0, new Vec3(-found.width() / 2D, -8, -4), new Vec3(found.width(), 8, 8), place);
    }

    private List<Face> banner(BlockStateId state) {
        boolean onWall = state.name().endsWith("_wall_banner");
        String color = state.name().substring(0, state.name().length() - (onWall ? "_wall_banner" : "_banner").length());
        Optional<BlockTexture> frame = textures.entityTexture("entity/banner/banner_base", MinecraftBlockTints.NO_TINT);
        Optional<BlockTexture> cloth = textures.entityTexture("entity/banner/base", DYE_COLORS.getOrDefault(color, MinecraftBlockTints.NO_TINT));
        if (frame.isEmpty() || cloth.isEmpty()) {
            return List.of();
        }
        Transform place;
        if (onWall) {
            CubeFace facing = CubeFace.named(state.properties().getOrDefault("facing", "north"));
            place = Transform.IDENTITY
                    .translate(0.5D, -1D / 6D, 0.5D)
                    .rotateY(-yRotationOf(facing.modelName()))
                    .translate(0D, -0.3125D, -0.4375D)
                    .scale(BANNER_SCALE, -BANNER_SCALE, -BANNER_SCALE);
        } else {
            int rotation = Integer.parseInt(state.properties().getOrDefault("rotation", "0"));
            place = Transform.IDENTITY
                    .translate(0.5D, 0.5D, 0.5D)
                    .rotateY(-rotation * ROTATION_SEGMENT_DEGREES)
                    .scale(BANNER_SCALE, -BANNER_SCALE, -BANNER_SCALE);
        }
        List<Face> faces = new ArrayList<>();
        if (!onWall) {
            faces.addAll(modelPartBox(frame.get(), 44, 0, new Vec3(-1, -30, -1), new Vec3(2, 42, 2), place));
        }
        faces.addAll(modelPartBox(frame.get(), 0, 42, new Vec3(-10, -32, -1), new Vec3(20, 2, 2), place));
        faces.addAll(modelPartBox(cloth.get(), 0, 0, new Vec3(-10, -32, -2), new Vec3(20, 40, 1), place));
        return faces;
    }

    // Minecraft's facing angle: south is 0 and each quarter turn clockwise seen from above adds 90 degrees.
    private static double yRotationOf(String facing) {
        return switch (facing) {
            case "west" -> 90D;
            case "north" -> 180D;
            case "east" -> 270D;
            default -> 0D;
        };
    }

    /**
     * A box of a block entity's model, in model pixels, with its sides laid out on the texture the way Minecraft's
     * model parts are: the top and bottom in a row above the four sides.
     */
    static List<Face> modelPartBox(BlockTexture texture, int textureU, int textureV, Vec3 from, Vec3 size, Transform place) {
        double minX = from.x() / MODEL_UNITS;
        double minY = from.y() / MODEL_UNITS;
        double minZ = from.z() / MODEL_UNITS;
        double maxX = (from.x() + size.x()) / MODEL_UNITS;
        double maxY = (from.y() + size.y()) / MODEL_UNITS;
        double maxZ = (from.z() + size.z()) / MODEL_UNITS;
        Vec3 v0 = new Vec3(minX, minY, minZ);
        Vec3 v1 = new Vec3(maxX, minY, minZ);
        Vec3 v2 = new Vec3(maxX, maxY, minZ);
        Vec3 v3 = new Vec3(minX, maxY, minZ);
        Vec3 v4 = new Vec3(minX, minY, maxZ);
        Vec3 v5 = new Vec3(maxX, minY, maxZ);
        Vec3 v6 = new Vec3(maxX, maxY, maxZ);
        Vec3 v7 = new Vec3(minX, maxY, maxZ);
        double u = textureU;
        double v = textureV;
        double width = size.x();
        double height = size.y();
        double depth = size.z();
        List<Face> faces = new ArrayList<>(6);
        faces.add(polygon(texture, place, CubeFace.DOWN, v5, v4, v0, u + depth, v, u + depth + width, v + depth));
        faces.add(polygon(texture, place, CubeFace.UP, v2, v3, v7, u + depth + width, v + depth, u + depth + width + width, v));
        faces.add(polygon(texture, place, CubeFace.WEST, v0, v4, v7, u, v + depth, u + depth, v + depth + height));
        faces.add(polygon(texture, place, CubeFace.NORTH, v1, v0, v3, u + depth, v + depth, u + depth + width, v + depth + height));
        faces.add(polygon(texture, place, CubeFace.EAST, v5, v1, v2, u + depth + width, v + depth, u + depth + width + depth, v + depth + height));
        faces.add(polygon(texture, place, CubeFace.SOUTH, v4, v5, v6, u + depth + width + depth, v + depth, u + depth + width + depth + width, v + depth + height));
        return faces;
    }

    // A model part's polygon gives its first corner (u2, v1), its second (u1, v1) and its third (u1, v2).
    private static Face polygon(BlockTexture texture, Transform place, CubeFace side, Vec3 first, Vec3 second, Vec3 third,
                                double u1, double v1, double u2, double v2) {
        double textureWidth = texture.width();
        double textureHeight = texture.height();
        TextureMapping mapping = new TextureMapping(u1 / textureWidth, v1 / textureHeight,
                (u2 - u1) / textureWidth, 0D, 0D, (v2 - v1) / textureHeight);
        Face face = new Face(second, first.minus(second), third.minus(second), side.direction(), texture, mapping, side.shade(), null)
                .transformed(place);
        return face.withShade(CubeFace.nearest(face.normal()).shade());
    }

    /**
     * The entity texture of a mob head and how wide its head is.
     */
    private record HeadLook(String texture, double width) {
        private static final double HEAD_WIDTH = 8D;
        private static final double PIGLIN_HEAD_WIDTH = 10D;

        static Optional<HeadLook> of(String kind) {
            return Optional.ofNullable(switch (kind) {
                case "skeleton_skull" -> new HeadLook("entity/skeleton/skeleton", HEAD_WIDTH);
                case "wither_skeleton_skull" -> new HeadLook("entity/skeleton/wither_skeleton", HEAD_WIDTH);
                case "zombie_head" -> new HeadLook("entity/zombie/zombie", HEAD_WIDTH);
                case "creeper_head" -> new HeadLook("entity/creeper/creeper", HEAD_WIDTH);
                case "player_head" -> new HeadLook("entity/player/wide/steve", HEAD_WIDTH);
                case "piglin_head" -> new HeadLook("entity/piglin/piglin", PIGLIN_HEAD_WIDTH);
                default -> null;
            });
        }
    }
}
