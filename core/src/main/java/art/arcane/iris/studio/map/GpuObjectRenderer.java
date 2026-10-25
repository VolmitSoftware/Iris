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

import java.util.Optional;
import java.util.Set;

import static org.lwjgl.opengl.GL33.*;

/**
 * Draws an object mesh on the graphics card, looking the way {@link ObjectRenderer} draws it. That one still draws on
 * machines without a card, and the thumbnails. The mesh stays on the card while it is shown, so a frame only sends the
 * camera and the see-through quads' order.
 */
final class GpuObjectRenderer {
    private static final double DEPTH_MARGIN_BLOCKS = 4D;
    // Several large objects, well within what any card able to draw them has.
    private static final long MOST_UPLOADED_BYTES = 256L << 20;
    private static final int CORNERS_PER_QUAD = 6;
    private static final int ENDS_PER_LINE = 2;
    private static final int GRID_DIRECTIONS = 2;

    private static final String QUAD_VERTEX_SHADER = """
            #version 330 core
            const int[6] CORNERS = int[6](0, 1, 2, 2, 1, 3);
            const int TEXELS_PER_QUAD = %d;
            uniform samplerBuffer quads;
            uniform isamplerBuffer order;
            uniform bool inOrder;
            uniform int firstQuad;
            uniform mat4 toClip;
            uniform vec3 anchorFromCenter;
            uniform vec3 towardCamera;
            out vec2 texturePosition;
            flat out int firstTexel;
            flat out ivec2 textureSize;
            flat out ivec4 texelRange;
            flat out float shade;
            flat out float opacity;

            // Faces looking away from the camera are hidden behind the ones looking toward it, so every corner of
            // theirs is put in the same spot outside the view, which draws nothing.
            void main() {
                int index = gl_VertexID / 6;
                int quad = inOrder ? texelFetch(order, index).r : firstQuad + index;
                int base = quad * TEXELS_PER_QUAD;
                vec4 origin = texelFetch(quads, base);
                vec4 edgeU = texelFetch(quads, base + 1);
                vec4 edgeV = texelFetch(quads, base + 2);
                vec4 normal = texelFetch(quads, base + 3);
                if (dot(normal.xyz, towardCamera) <= 0.0) {
                    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
                    return;
                }
                vec4 mappingU = texelFetch(quads, base + 4);
                vec4 mappingV = texelFetch(quads, base + 5);
                int corner = CORNERS[gl_VertexID %% 6];
                float alongU = float(corner & 1);
                float alongV = float(corner >> 1);
                gl_Position = toClip * vec4(anchorFromCenter + origin.xyz + alongU * edgeU.xyz + alongV * edgeV.xyz, 1.0);
                texturePosition = vec2(mappingU.x + alongU * mappingU.y + alongV * mappingU.z,
                        mappingV.x + alongU * mappingV.y + alongV * mappingV.z);
                firstTexel = floatBitsToInt(edgeV.w);
                textureSize = ivec2(floatBitsToInt(normal.w), floatBitsToInt(mappingU.w));
                texelRange = floatBitsToInt(texelFetch(quads, base + 6));
                shade = origin.w;
                opacity = edgeU.w;
            }
            """.formatted(GpuObjectMesh.TEXELS_PER_QUAD);

    private static final String QUAD_FRAGMENT_SHADER = """
            #version 330 core
            uniform samplerBuffer texels;
            uniform bool blended;
            in vec2 texturePosition;
            flat in int firstTexel;
            flat in ivec2 textureSize;
            flat in ivec4 texelRange;
            flat in float shade;
            flat in float opacity;
            out vec4 fragment;

            void main() {
                int column = clamp(int(floor(texturePosition.x * float(textureSize.x))), texelRange.x, texelRange.y);
                int row = clamp(int(floor(texturePosition.y * float(textureSize.y))), texelRange.z, texelRange.w);
                vec4 texel = texelFetch(texels, firstTexel + row * textureSize.x + column);
                if (texel.a == 0.0) {
                    discard;
                }
                fragment = vec4(min(texel.rgb * shade, vec3(1.0)), blended ? texel.a * opacity : 1.0);
            }
            """;

    private static final String GRID_VERTEX_SHADER = """
            #version 330 core
            uniform mat4 toClip;
            uniform vec3 gridCenter;
            uniform int reach;
            uniform int step;
            uniform int linesPerDirection;

            void main() {
                int line = gl_VertexID / 2;
                float across = float(-reach + (line % linesPerDirection) * step);
                float along = float((gl_VertexID % 2) * 2 - 1) * float(reach);
                vec3 offset = line < linesPerDirection ? vec3(across, 0.0, along) : vec3(along, 0.0, across);
                gl_Position = toClip * vec4(gridCenter + offset, 1.0);
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

    private static GpuObjectRenderer onCard;

    private final GpuProgram quadProgram = GpuProgram.of(QUAD_VERTEX_SHADER, QUAD_FRAGMENT_SHADER);
    private final GpuProgram gridProgram = GpuProgram.of(GRID_VERTEX_SHADER, FLAT_FRAGMENT_SHADER);
    private final int attributelessVertexArray = glGenVertexArrays();
    // Asked once: asking the card waits for everything sent to it so far.
    private final int mostBufferTexels = glGetInteger(GL_MAX_TEXTURE_BUFFER_SIZE);
    private final GpuCache<GpuObjectMesh> uploadedMeshes = new GpuCache<>();

    /**
     * @return the picture, or nothing when the card can't draw this mesh and the caller should draw it without one
     */
    static Optional<OrbitPicture> render(ObjectMesh mesh, OrbitCamera camera, int width, int height, int coveredLeftWidth) {
        return GpuFrames.drawPicture(target -> onCard().draw(mesh, camera, width, height, coveredLeftWidth, target));
    }

    /**
     * @return the frame, or nothing when it can't go straight into the window and the caller should draw a picture
     */
    static Optional<OrbitPicture> renderOnScreen(ObjectMesh mesh, ScreenTarget screen, OrbitCamera camera, int width, int height,
                                                 int coveredLeftWidth) {
        return GpuFrames.drawOnScreen(screen, target -> onCard().draw(mesh, camera, width, height, coveredLeftWidth, target));
    }

    private static GpuObjectRenderer onCard() {
        if (onCard == null) {
            onCard = new GpuObjectRenderer();
        }
        return onCard;
    }

    private Optional<OrbitPicture> draw(ObjectMesh mesh, OrbitCamera camera, int width, int height, int coveredLeftWidth,
                                        GpuFrameTarget target) {
        GpuObjectMesh uploaded = uploaded(mesh);
        if (uploaded == null) {
            return Optional.empty();
        }
        OrbitProjection projection = ObjectRenderer.projection(mesh, camera, width, height, coveredLeftWidth);
        ObjectRenderer.FloorGrid grid = ObjectRenderer.FloorGrid.under(mesh.bounds());
        double depthRange = farthestFrom(projection.center(), mesh.bounds(), grid) + DEPTH_MARGIN_BLOCKS;
        float[] toClip = projection.clipMatrix(width, height, depthRange);
        target.bindSized(width, height);
        GpuFrames.clearToBackground();
        glDepthFunc(GL_LEQUAL);
        glBindVertexArray(attributelessVertexArray);
        drawGrid(grid, projection, toClip);
        drawMesh(uploaded, projection, toClip);
        GpuFrames.failOnError();
        return Optional.of(target.finish(projection, depthRange));
    }

    private GpuObjectMesh uploaded(ObjectMesh mesh) {
        uploadedMeshes.forgetCollected();
        GpuObjectMesh uploaded = uploadedMeshes.get(mesh);
        if (uploaded == null) {
            uploaded = GpuObjectMesh.upload(mesh, mostBufferTexels);
            if (uploaded == null) {
                return null;
            }
            uploadedMeshes.put(mesh, uploaded);
        }
        uploadedMeshes.forgetBeyond(MOST_UPLOADED_BYTES, Set.of(mesh));
        return uploaded;
    }

    // No point of the scene is further from the projection's centre than the farthest corner of a box around it all.
    private static double farthestFrom(Vec3 center, ObjectMesh.Bounds bounds, ObjectRenderer.FloorGrid grid) {
        double reachX = Math.max(Math.abs(bounds.minX() - center.x()), Math.abs(bounds.maxX() - center.x()));
        double reachZ = Math.max(Math.abs(bounds.minZ() - center.z()), Math.abs(bounds.maxZ() - center.z()));
        double gridReachX = Math.abs(grid.centerX() - center.x()) + grid.reach();
        double gridReachZ = Math.abs(grid.centerZ() - center.z()) + grid.reach();
        double reachY = Math.max(Math.abs(Math.min(bounds.minY(), grid.floorY()) - center.y()), Math.abs(bounds.maxY() - center.y()));
        return Math.sqrt(square(Math.max(reachX, gridReachX)) + square(reachY) + square(Math.max(reachZ, gridReachZ)));
    }

    private static double square(double value) {
        return value * value;
    }

    private void drawGrid(ObjectRenderer.FloorGrid grid, OrbitProjection projection, float[] toClip) {
        Vec3 center = projection.center();
        int color = grid.color();
        gridProgram.use();
        gridProgram.setMatrix("toClip", toClip);
        gridProgram.set("gridCenter", new Vec3(grid.centerX() - center.x(), grid.floorY() - center.y(), grid.centerZ() - center.z()));
        gridProgram.set("reach", grid.reach());
        gridProgram.set("step", grid.step());
        gridProgram.set("linesPerDirection", grid.linesPerDirection());
        gridProgram.set("color", GpuFrames.channel(color, 16), GpuFrames.channel(color, 8), GpuFrames.channel(color, 0), 1D);
        glDrawArrays(GL_LINES, 0, grid.linesPerDirection() * GRID_DIRECTIONS * ENDS_PER_LINE);
    }

    /**
     * The cut-away ghost is drawn first, hiding nothing, so the solid quads drawn over it replace it wherever the object
     * shows. Later quads win ties, so an overlay on the same box, like grass on a grass block's side, shows on top.
     */
    private void drawMesh(GpuObjectMesh mesh, OrbitProjection projection, float[] toClip) {
        Vec3 towardCamera = new Vec3(projection.depthStep(1D, 0D, 0D), projection.depthStep(0D, 1D, 0D), projection.depthStep(0D, 0D, 1D));
        Vec3 anchor = mesh.anchor();
        Vec3 center = projection.center();
        quadProgram.use();
        quadProgram.setMatrix("toClip", toClip);
        quadProgram.set("anchorFromCenter", new Vec3(anchor.x() - center.x(), anchor.y() - center.y(), anchor.z() - center.z()));
        quadProgram.set("towardCamera", towardCamera);
        quadProgram.set("quads", 0);
        quadProgram.set("texels", 1);
        quadProgram.set("order", 2);
        glActiveTexture(GL_TEXTURE0);
        mesh.bindQuads();
        glActiveTexture(GL_TEXTURE1);
        mesh.bindTexels();
        glActiveTexture(GL_TEXTURE2);
        mesh.bindOrder();
        glActiveTexture(GL_TEXTURE0);

        startBlending();
        drawQuads(mesh.firstGhost(), mesh.ghostCount(), true);
        glDisable(GL_BLEND);
        glDepthMask(true);
        drawQuads(0, mesh.solidCount(), false);
        startBlending();
        mesh.sortSeeThroughFarthestFirst(towardCamera);
        quadProgram.set("inOrder", 1);
        drawQuads(0, mesh.seeThroughCount(), true);
        quadProgram.set("inOrder", 0);
    }

    // Blended quads hide nothing behind them. The window's own alpha is left whole, so nothing behind it shows through.
    private static void startBlending() {
        glEnable(GL_BLEND);
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ZERO, GL_ONE);
        glDepthMask(false);
    }

    private void drawQuads(int firstQuad, int count, boolean blended) {
        if (count == 0) {
            return;
        }
        quadProgram.set("firstQuad", firstQuad);
        quadProgram.set("blended", blended ? 1 : 0);
        glDrawArrays(GL_TRIANGLES, 0, count * CORNERS_PER_QUAD);
    }
}
