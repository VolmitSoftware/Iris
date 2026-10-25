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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.lwjgl.opengl.GL33.GL_BLEND;
import static org.lwjgl.opengl.GL33.GL_MAX_TEXTURE_BUFFER_SIZE;
import static org.lwjgl.opengl.GL33.GL_MAX_TEXTURE_SIZE;
import static org.lwjgl.opengl.GL33.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL33.GL_POLYGON_OFFSET_FILL;
import static org.lwjgl.opengl.GL33.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL33.GL_TEXTURE0;
import static org.lwjgl.opengl.GL33.GL_TEXTURE1;
import static org.lwjgl.opengl.GL33.GL_TRIANGLES;
import static org.lwjgl.opengl.GL33.GL_TRIANGLE_STRIP;
import static org.lwjgl.opengl.GL33.glActiveTexture;
import static org.lwjgl.opengl.GL33.glBindVertexArray;
import static org.lwjgl.opengl.GL33.glBlendFunc;
import static org.lwjgl.opengl.GL33.glDepthMask;
import static org.lwjgl.opengl.GL33.glDrawArrays;
import static org.lwjgl.opengl.GL33.glEnable;
import static org.lwjgl.opengl.GL33.glGenVertexArrays;
import static org.lwjgl.opengl.GL33.glGetInteger;
import static org.lwjgl.opengl.GL33.glPolygonOffset;

/**
 * Draws noise terrain as block columns on the graphics card, looking the way {@link NoiseTerrainRenderer} draws them.
 * That one still draws on machines without a card. Faces and colours stay on the card while the scene shows them, so
 * a frame only sends the camera.
 */
final class GpuTerrainRenderer {
    private static final double DEPTH_MARGIN_BLOCKS = 16D;
    // A few full views' worth, well within what any card able to draw them has.
    private static final long MOST_UPLOADED_BYTES = 768L << 20;
    // Half a frame at 60 frames a second.
    private static final long UPLOAD_NANOS_PER_FRAME = 8_000_000L;
    private static final double FACE_OVERLAP_PIXELS = 0.05D;

    private static final String FACE_VERTEX_SHADER = """
            #version 330 core
            const int[6] CORNERS = int[6](0, 1, 2, 2, 1, 3);
            uniform isamplerBuffer faces;
            uniform int firstFace;
            uniform mat4 toClip;
            uniform ivec3 layerOrigin;
            uniform vec3 centerFraction;
            uniform int step;
            uniform ivec3 normal;
            uniform float overlap;
            uniform int hiddenCells[%1$d];
            uniform bool holeWall;
            out vec2 units;
            flat out ivec2 firstCell;
            flat out int run;

            bool hides(ivec2 column) {
                if (any(lessThan(column, ivec2(0))) || any(greaterThanEqual(column, ivec2(%2$d)))) {
                    return false;
                }
                int cell = column.y / %3$d * %4$d + column.x / %3$d;
                return (hiddenCells[cell / 32] & (1 << (cell %% 32))) != 0;
            }

            // The same as TerrainHole.shows: no face of a hidden column, and beside the hole its wall instead of the
            // usual side. No face crosses a cell's edge, so its first column decides for all of it.
            bool showsWithHole(ivec2 column) {
                if (hides(column)) {
                    return false;
                }
                return normal.y == 1 || holeWall == hides(column + normal.xz);
            }

            // Corners are worked out in whole blocks relative to the view centre, so far from the world's origin
            // nothing is lost to float precision. Faces meeting the middle of a longer face's edge leave hairline
            // cracks along it, so every face reaches a little past its edges. Faces the hole leaves out land outside
            // the view, where they're clipped away before any pixel is drawn.
            void main() {
                ivec4 face = texelFetch(faces, firstFace + gl_VertexID / 6);
                if (!showsWithHole(face.xy)) {
                    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
                    units = vec2(0.0);
                    firstCell = ivec2(0);
                    run = 1;
                    return;
                }
                int corner = CORNERS[gl_VertexID %% 6];
                int alongRun = corner & 1;
                int alongReach = corner >> 1;
                run = face.w & 0xFFFF;
                int reach = (face.w >> 16) & 0xFFFF;
                bool runsAlongX = normal.x == 0;
                bool isTop = normal.y == 1;
                ivec3 origin = ivec3((face.x + max(normal.x, 0)) * step, face.z, (face.y + max(normal.z, 0)) * step);
                ivec3 runEdge = runsAlongX ? ivec3(run * step, 0, 0) : ivec3(0, 0, run * step);
                ivec3 reachEdge = isTop ? ivec3(0, 0, reach * step) : ivec3(0, reach, 0);
                ivec3 position = layerOrigin + origin + alongRun * runEdge + alongReach * reachEdge;
                vec3 runDirection = runsAlongX ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 0.0, 1.0);
                vec3 reachDirection = isTop ? vec3(0.0, 0.0, 1.0) : vec3(0.0, 1.0, 0.0);
                vec3 outward = (float(alongRun) * 2.0 - 1.0) * runDirection + (float(alongReach) * 2.0 - 1.0) * reachDirection;
                gl_Position = toClip * vec4(vec3(position) - centerFraction + outward * overlap, 1.0);
                units = vec2(alongRun * run, alongReach * reach);
                firstCell = face.xy;
            }
            """.formatted(TerrainHole.WORDS, TerrainTile.COLUMNS, TerrainHole.CELL_COLUMNS, TerrainHole.CELLS_ACROSS);

    private static final String FACE_FRAGMENT_SHADER = """
            #version 330 core
            uniform sampler2D colors;
            uniform ivec2 runDirection;
            uniform float shade;
            in vec2 units;
            flat in ivec2 firstCell;
            flat in int run;
            out vec4 fragment;

            float outlineStrength(float pixelsPerUnit) {
                return clamp((pixelsPerUnit - %1$s) / (%2$s - %1$s), 0.0, 1.0);
            }

            float lineStrength(float along, float unitsPerPixel) {
                float pixelsPerUnit = 1.0 / max(unitsPerPixel, 1e-6);
                if (pixelsPerUnit < %1$s || abs(along - round(along)) * pixelsPerUnit >= %3$s) {
                    return 0.0;
                }
                return outlineStrength(pixelsPerUnit);
            }

            void main() {
                int along = clamp(int(floor(units.x)), 0, run - 1);
                vec3 color = min(texelFetch(colors, firstCell + runDirection * along, 0).rgb * shade, vec3(1.0));
                vec2 unitsPerPixel = fwidth(units);
                float strength = lineStrength(units.x, unitsPerPixel.x);
                if (strength == 0.0) {
                    strength = lineStrength(units.y, unitsPerPixel.y);
                }
                float luminance = dot(color, vec3(0.3, 0.59, 0.11)) * 255.0;
                vec3 outline = luminance < %4$s ? mix(color, vec3(1.0), %5$s) : color * %6$s;
                fragment = vec4(mix(color, outline, strength), 1.0);
            }
            """.formatted(NoiseTerrainRenderer.LEAST_OUTLINE_SPACING_PIXELS, NoiseTerrainRenderer.FULL_OUTLINE_SPACING_PIXELS,
            NoiseTerrainRenderer.OUTLINE_HALF_WIDTH_PIXELS, NoiseTerrainRenderer.DARK_LUMINANCE,
            NoiseTerrainRenderer.OUTLINE_LIGHTENING, NoiseTerrainRenderer.OUTLINE_SHADE);

    private static final String FLAT_VERTEX_SHADER = """
            #version 330 core
            uniform mat4 toClip;
            uniform vec3 corner;
            uniform vec3 edgeU;
            uniform vec3 edgeV;

            void main() {
                float alongU = float(gl_VertexID & 1);
                float alongV = float(gl_VertexID >> 1);
                gl_Position = toClip * vec4(corner + alongU * edgeU + alongV * edgeV, 1.0);
            }
            """;

    private static final String FLAT_FRAGMENT_SHADER = """
            #version 330 core
            uniform vec4 color;
            out vec4 fragment;

            void main() {
                fragment = color;
            }
            """;

    private static GpuTerrainRenderer onCard;

    private final GpuProgram facesProgram = GpuProgram.of(FACE_VERTEX_SHADER, FACE_FRAGMENT_SHADER);
    private final GpuProgram flatProgram = GpuProgram.of(FLAT_VERTEX_SHADER, FLAT_FRAGMENT_SHADER);
    private final int attributelessVertexArray = glGenVertexArrays();
    // Asked once: asking the card waits for everything sent to it so far.
    private final int mostFacesPerLayer = glGetInteger(GL_MAX_TEXTURE_BUFFER_SIZE);
    private final int largestColorsSide = glGetInteger(GL_MAX_TEXTURE_SIZE);
    private final GpuTileCache uploadedTiles = new GpuTileCache();

    /**
     * @return the picture, or nothing when the card can't draw this scene and the caller should draw it without one
     */
    static Optional<OrbitPicture> render(NoiseTerrainScene scene, OrbitCamera camera, int width, int height, int coveredLeftWidth) {
        return GpuFrames.drawPicture(target -> onCard().draw(scene, camera, width, height, coveredLeftWidth, target));
    }

    /**
     * @return the frame, or nothing when it can't go straight into the window and the caller should draw a picture
     */
    static Optional<OrbitPicture> renderOnScreen(NoiseTerrainScene scene, ScreenTarget screen, OrbitCamera camera, int width, int height,
                                                 int coveredLeftWidth) {
        return GpuFrames.drawOnScreen(screen, target -> onCard().draw(scene, camera, width, height, coveredLeftWidth, target));
    }

    private static GpuTerrainRenderer onCard() {
        if (onCard == null) {
            onCard = new GpuTerrainRenderer();
        }
        return onCard;
    }

    /**
     * Draws what of the scene is on the card. What isn't yet is put there over the next frames, as the picture asks to
     * be drawn again, so turning the camera never waits on new ground being put on the card.
     */
    private Optional<OrbitPicture> draw(NoiseTerrainScene scene, OrbitCamera camera, int width, int height, int coveredLeftWidth,
                                        GpuFrameTarget target) {
        Uploads uploads = uploadWithinBudget(scene);
        if (uploads == null) {
            return Optional.empty();
        }
        OrbitPicture picture = drawLayers(scene, uploads.layers(), camera, width, height, coveredLeftWidth, target);
        return Optional.of(uploads.complete() ? picture : picture.stillCatchingUp());
    }

    private OrbitPicture drawLayers(NoiseTerrainScene scene, List<UploadedLayer> layers, OrbitCamera camera, int width, int height,
                                    int coveredLeftWidth, GpuFrameTarget target) {
        OrbitProjection projection = scene.projection(camera, width, height, coveredLeftWidth);
        NoiseTerrainScene.Extent extent = scene.extent();
        double depthRange = extent.farthestFrom(projection.center()) + DEPTH_MARGIN_BLOCKS;
        float[] toClip = projection.clipMatrix(width, height, depthRange);
        target.bindSized(width, height);
        GpuFrames.clearToBackground();
        for (UploadedLayer layer : layers) {
            drawFaces(layer, projection, toClip);
        }
        if (scene.hasWater()) {
            drawWater(extent, BlockFaces.blockTop(scene.waterLevel()), projection, toClip);
        }
        GpuFrames.failOnError();
        return target.finish(projection, depthRange);
    }

    /**
     * Puts as many of the scene's layers on the card as fit in one frame's time, reusing what is already there, and
     * says how to draw what is there. A place whose newest ground isn't on the card yet is drawn with what is, such as
     * the ground from before the heights changed. Tiles the scene doesn't show stay on the card while they fit and are
     * still in memory, as turning back to them is likely.
     *
     * @return the layers on the card, or null when one is too large for it
     */
    private Uploads uploadWithinBudget(NoiseTerrainScene scene) {
        UploadProgress progress = uploadCoarsestFirst(scene.layers());
        if (progress == UploadProgress.TOO_LARGE) {
            return null;
        }
        List<UploadedLayer> layers = layersOnCard(scene.layers());
        Set<GpuTileCache.Place> shown = new HashSet<>();
        for (HoledLayer layer : scene.layers()) {
            shown.add(GpuTileCache.Place.of(layer.terrain()));
        }
        uploadedTiles.forgetBeyond(MOST_UPLOADED_BYTES, shown);
        return new Uploads(layers, progress == UploadProgress.COMPLETE);
    }

    // Coarse tiles cover the most ground for their upload, so they go first; finer ones then only cut their holes.
    private UploadProgress uploadCoarsestFirst(List<HoledLayer> layers) {
        long deadline = System.nanoTime() + UPLOAD_NANOS_PER_FRAME;
        List<HoledLayer> coarsestFirst = new ArrayList<>(layers);
        coarsestFirst.sort(Comparator.comparingInt((HoledLayer layer) -> layer.terrain().step()).reversed());
        UploadProgress progress = UploadProgress.COMPLETE;
        for (HoledLayer layer : coarsestFirst) {
            GpuTileCache.Place place = GpuTileCache.Place.of(layer.terrain());
            GpuTile tile = uploadedTiles.get(place);
            if (tile != null && tile.shows(layer.layer())) {
                continue;
            }
            if (System.nanoTime() > deadline) {
                progress = UploadProgress.CATCHING_UP;
                continue;
            }
            if (upload(layer.layer(), place, tile) == null) {
                return UploadProgress.TOO_LARGE;
            }
        }
        return progress;
    }

    // A hole is only cut where what fills it is on the card, so no ground is ever left out with nothing in its place.
    // Tiles whose fillers are all on the card show nothing, so they aren't drawn at all.
    private List<UploadedLayer> layersOnCard(List<HoledLayer> layers) {
        List<UploadedLayer> onCard = new ArrayList<>(layers.size());
        for (HoledLayer layer : layers) {
            GpuTile tile = uploadedTiles.get(GpuTileCache.Place.of(layer.terrain()));
            if (tile == null) {
                continue;
            }
            TerrainHole hole = layer.holeAmong(filler -> uploadedTiles.get(GpuTileCache.Place.of(filler)) != null);
            if (!hole.hidesAll()) {
                onCard.add(new UploadedLayer(layer.terrain(), tile.faces(), tile.colors(), hole));
            }
        }
        return onCard;
    }

    private GpuTile upload(TerrainLayer layer, GpuTileCache.Place place, GpuTile previous) {
        if (previous != null) {
            return previous.showInstead(layer, largestColorsSide, mostFacesPerLayer) ? previous : null;
        }
        GpuTile tile = GpuTile.upload(layer, largestColorsSide, mostFacesPerLayer);
        if (tile != null) {
            uploadedTiles.put(place, tile);
        }
        return tile;
    }

    // Faces looking away from the camera are hidden behind the ones looking toward it, so they're never drawn.
    private void drawFaces(UploadedLayer layer, OrbitProjection projection, float[] toClip) {
        Vec3 center = projection.center();
        int wholeX = (int) Math.floor(center.x());
        int wholeY = (int) Math.floor(center.y());
        int wholeZ = (int) Math.floor(center.z());
        facesProgram.use();
        facesProgram.setMatrix("toClip", toClip);
        facesProgram.set("colors", 0);
        facesProgram.set("faces", 1);
        facesProgram.set("layerOrigin", layer.terrain().originX() - wholeX, -wholeY, layer.terrain().originZ() - wholeZ);
        facesProgram.set("centerFraction", new Vec3(center.x() - wholeX, center.y() - wholeY, center.z() - wholeZ));
        facesProgram.set("step", layer.terrain().step());
        facesProgram.set("overlap", FACE_OVERLAP_PIXELS / projection.pixelsPerBlock());
        facesProgram.set("hiddenCells", layer.hole().hiddenCells());
        glActiveTexture(GL_TEXTURE0);
        layer.colors().bind();
        glActiveTexture(GL_TEXTURE1);
        layer.faces().bind();
        glBindVertexArray(attributelessVertexArray);
        for (BlockFaces.Kind kind : BlockFaces.Kind.values()) {
            facesProgram.set("holeWall", kind == BlockFaces.Kind.HOLE_WALL ? 1 : 0);
            for (BlockFaces.Facing facing : BlockFaces.Facing.values()) {
                drawFacing(layer.faces(), kind, facing, projection);
            }
        }
        glActiveTexture(GL_TEXTURE0);
    }

    private void drawFacing(GpuFaces faces, BlockFaces.Kind kind, BlockFaces.Facing facing, OrbitProjection projection) {
        int count = faces.count(kind, facing);
        if (count == 0 || !projection.facesCamera(facing.normalX, facing.normalY, facing.normalZ)) {
            return;
        }
        facesProgram.set("firstFace", faces.first(kind, facing));
        facesProgram.set("normal", facing.normalX, facing.normalY, facing.normalZ);
        facesProgram.set("runDirection", facing.runsAlongX() ? 1 : 0, facing.runsAlongX() ? 0 : 1);
        facesProgram.set("shade", NoiseTerrainRenderer.faceShadeAt(facing, projection.pixelsPerBlock()));
        glDrawArrays(GL_TRIANGLES, 0, count * GpuFaces.CORNERS_PER_FACE);
    }

    // Water is blended over what lies below its surface, and hides nothing behind it. Ground level with the water's
    // surface stays dry rather than flickering between the two.
    private void drawWater(NoiseTerrainScene.Extent extent, int level, OrbitProjection projection, float[] toClip) {
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(false);
        glEnable(GL_POLYGON_OFFSET_FILL);
        glPolygonOffset(1F, 1F);
        Vec3 center = projection.center();
        flatProgram.use();
        flatProgram.setMatrix("toClip", toClip);
        flatProgram.set("corner", new Vec3(extent.minX() - center.x(), level - center.y(), extent.minZ() - center.z()));
        flatProgram.set("edgeU", new Vec3(extent.sizeX(), 0D, 0D));
        flatProgram.set("edgeV", new Vec3(0D, 0D, extent.sizeZ()));
        int color = NoiseTerrainRenderer.WATER_COLOR;
        flatProgram.set("color", GpuFrames.channel(color, 16), GpuFrames.channel(color, 8), GpuFrames.channel(color, 0),
                NoiseTerrainRenderer.WATER_OPACITY);
        glBindVertexArray(attributelessVertexArray);
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    }

    private record Uploads(List<UploadedLayer> layers, boolean complete) {
    }

    private enum UploadProgress {
        COMPLETE,
        CATCHING_UP,
        TOO_LARGE
    }

    private record UploadedLayer(NoiseTerrain terrain, GpuFaces faces, GpuColors colors, TerrainHole hole) {
    }
}
