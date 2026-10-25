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

import art.arcane.iris.studio.map.BlockModel.Face;
import art.arcane.iris.studio.map.BlockModel.TextureMapping;
import art.arcane.iris.studio.map.BlockTextures.BlockTexture;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL33.*;

/**
 * An object mesh's quads and the texels of their textures on the graphics card, each in a buffer texture the shaders
 * read from. Quads come solid first, then the cut-away ghost, then the see-through ones, whose drawing order is sent
 * again every frame, farthest first. Only for the GPU thread.
 */
final class GpuObjectMesh implements GpuCache.Upload {
    static final int TEXELS_PER_QUAD = 7;
    private static final int FLOATS_PER_QUAD = TEXELS_PER_QUAD * 4;
    private static final int OPAQUE_ALPHA = 0xFF;

    private final Vec3 anchor;
    private final int solidCount;
    private final int ghostCount;
    private final int seeThroughCount;
    private final float[] seeThroughCenters;
    private final long bytes;
    private final int quadBuffer = glGenBuffers();
    private final int quadTexture = glGenTextures();
    private final int texelBuffer = glGenBuffers();
    private final int texelTexture = glGenTextures();
    private final int orderBuffer = glGenBuffers();
    private final int orderTexture = glGenTextures();

    private GpuObjectMesh(ObjectMesh mesh, int texelCount) {
        ObjectMesh.Bounds bounds = mesh.bounds();
        anchor = new Vec3(bounds.minX(), bounds.minY(), bounds.minZ());
        solidCount = mesh.solidQuads().size();
        ghostCount = mesh.cutAwayQuads().size();
        seeThroughCount = mesh.translucentQuads().size();
        seeThroughCenters = centersOf(mesh.translucentQuads());
        bytes = (long) quadCount() * FLOATS_PER_QUAD * Float.BYTES + (long) texelCount * Integer.BYTES + (long) seeThroughCount * Integer.BYTES;
    }

    /**
     * @param mostTexels the card's GL_MAX_TEXTURE_BUFFER_SIZE
     * @return the uploaded mesh, or null when it is more than the card's buffer textures hold
     */
    static GpuObjectMesh upload(ObjectMesh mesh, int mostTexels) {
        Map<BlockTexture, Integer> textureOffsets = new IdentityHashMap<>();
        int texelCount = placeTextures(mesh, textureOffsets);
        long quadTexels = (long) (mesh.solidQuads().size() + mesh.cutAwayQuads().size() + mesh.translucentQuads().size()) * TEXELS_PER_QUAD;
        if (quadTexels > mostTexels || texelCount > mostTexels || quadTexels * 6L > Integer.MAX_VALUE) {
            return null;
        }
        GpuObjectMesh uploaded = new GpuObjectMesh(mesh, texelCount);
        try {
            uploaded.fillQuads(mesh, textureOffsets);
            uploaded.fillTexels(textureOffsets, texelCount);
            uploaded.prepareOrder();
        } catch (RuntimeException failure) {
            uploaded.delete();
            throw failure;
        }
        return uploaded;
    }

    private static int placeTextures(ObjectMesh mesh, Map<BlockTexture, Integer> offsets) {
        int texelCount = 0;
        for (List<ObjectMesh.Quad> quads : List.of(mesh.solidQuads(), mesh.cutAwayQuads(), mesh.translucentQuads())) {
            for (ObjectMesh.Quad quad : quads) {
                BlockTexture texture = quad.face().texture();
                if (!offsets.containsKey(texture)) {
                    offsets.put(texture, texelCount);
                    texelCount += texture.width() * texture.height();
                }
            }
        }
        return texelCount;
    }

    private float[] centersOf(List<ObjectMesh.Quad> quads) {
        float[] centers = new float[quads.size() * 3];
        for (int index = 0; index < quads.size(); index++) {
            ObjectMesh.Quad quad = quads.get(index);
            Face face = quad.face();
            centers[index * 3] = (float) (quad.originX() - anchor.x() + (face.edgeU().x() + face.edgeV().x()) / 2D);
            centers[index * 3 + 1] = (float) (quad.originY() - anchor.y() + (face.edgeU().y() + face.edgeV().y()) / 2D);
            centers[index * 3 + 2] = (float) (quad.originZ() - anchor.z() + (face.edgeU().z() + face.edgeV().z()) / 2D);
        }
        return centers;
    }

    private void fillQuads(ObjectMesh mesh, Map<BlockTexture, Integer> textureOffsets) {
        FloatBuffer packed = MemoryUtil.memAllocFloat(Math.max(1, quadCount()) * FLOATS_PER_QUAD);
        try {
            for (List<ObjectMesh.Quad> quads : List.of(mesh.solidQuads(), mesh.cutAwayQuads(), mesh.translucentQuads())) {
                for (ObjectMesh.Quad quad : quads) {
                    pack(quad, textureOffsets.get(quad.face().texture()), packed);
                }
            }
            packed.flip();
            glBindBuffer(GL_TEXTURE_BUFFER, quadBuffer);
            glBufferData(GL_TEXTURE_BUFFER, packed, GL_STATIC_DRAW);
        } finally {
            MemoryUtil.memFree(packed);
        }
        glBindTexture(GL_TEXTURE_BUFFER, quadTexture);
        glTexBuffer(GL_TEXTURE_BUFFER, GL_RGBA32F, quadBuffer);
    }

    // Whole numbers ride along as float bits, read back exactly by the shader.
    private void pack(ObjectMesh.Quad quad, int textureOffset, FloatBuffer packed) {
        Face face = quad.face();
        BlockTexture texture = face.texture();
        TextureMapping mapping = face.mapping();
        ObjectRenderer.TexelRange columns = ObjectRenderer.TexelRange.of(mapping.u(), mapping.uAlongU(), mapping.uAlongV(), texture.width());
        ObjectRenderer.TexelRange rows = ObjectRenderer.TexelRange.of(mapping.v(), mapping.vAlongU(), mapping.vAlongV(), texture.height());
        put(packed, quad.originX() - anchor.x(), quad.originY() - anchor.y(), quad.originZ() - anchor.z()).put((float) face.shade());
        put(packed, face.edgeU()).put((float) quad.opacity());
        put(packed, face.edgeV()).put(Float.intBitsToFloat(textureOffset));
        put(packed, face.normal()).put(Float.intBitsToFloat(texture.width()));
        put(packed, mapping.u(), mapping.uAlongU(), mapping.uAlongV()).put(Float.intBitsToFloat(texture.height()));
        put(packed, mapping.v(), mapping.vAlongU(), mapping.vAlongV()).put(0F);
        packed.put(Float.intBitsToFloat(columns.first())).put(Float.intBitsToFloat(columns.last()))
                .put(Float.intBitsToFloat(rows.first())).put(Float.intBitsToFloat(rows.last()));
    }

    private static FloatBuffer put(FloatBuffer packed, Vec3 vector) {
        return put(packed, vector.x(), vector.y(), vector.z());
    }

    private static FloatBuffer put(FloatBuffer packed, double x, double y, double z) {
        return packed.put((float) x).put((float) y).put((float) z);
    }

    // Texels the game doesn't draw are fully transparent, so the shader skips them.
    private void fillTexels(Map<BlockTexture, Integer> textureOffsets, int texelCount) {
        IntBuffer packed = MemoryUtil.memAllocInt(Math.max(1, texelCount));
        try {
            textureOffsets.forEach((texture, offset) -> {
                int[] alphas = texture.alphas();
                for (int texel = 0; texel < texture.width() * texture.height(); texel++) {
                    int alpha = !texture.opaque()[texel] ? 0 : alphas == null ? OPAQUE_ALPHA : alphas[texel];
                    packed.put(offset + texel, rgba(texture.pixels()[texel], alpha));
                }
            });
            glBindBuffer(GL_TEXTURE_BUFFER, texelBuffer);
            glBufferData(GL_TEXTURE_BUFFER, packed, GL_STATIC_DRAW);
        } finally {
            MemoryUtil.memFree(packed);
        }
        glBindTexture(GL_TEXTURE_BUFFER, texelTexture);
        glTexBuffer(GL_TEXTURE_BUFFER, GL_RGBA8, texelBuffer);
    }

    // GL_RGBA8 takes red from the lowest byte, which is the first in memory on the little-endian machines LWJGL runs on.
    private static int rgba(int rgb, int alpha) {
        return ((rgb >>> 16) & 0xFF) | (rgb & 0xFF00) | ((rgb & 0xFF) << 16) | (alpha << 24);
    }

    private void prepareOrder() {
        glBindBuffer(GL_TEXTURE_BUFFER, orderBuffer);
        glBufferData(GL_TEXTURE_BUFFER, (long) Math.max(1, seeThroughCount) * Integer.BYTES, GL_STREAM_DRAW);
        glBindTexture(GL_TEXTURE_BUFFER, orderTexture);
        glTexBuffer(GL_TEXTURE_BUFFER, GL_R32I, orderBuffer);
    }

    /**
     * Sends the see-through quads' drawing order, farthest from the camera first, keeping the mesh's order between
     * quads at the same depth.
     *
     * @param towardCamera how far each block along x, y and z brings a point toward the camera
     */
    void sortSeeThroughFarthestFirst(Vec3 towardCamera) {
        if (seeThroughCount == 0) {
            return;
        }
        long[] depthThenIndex = new long[seeThroughCount];
        for (int index = 0; index < seeThroughCount; index++) {
            float depth = (float) (seeThroughCenters[index * 3] * towardCamera.x() + seeThroughCenters[index * 3 + 1] * towardCamera.y()
                    + seeThroughCenters[index * 3 + 2] * towardCamera.z());
            depthThenIndex[index] = (long) sortableBits(depth) << 32 | index;
        }
        Arrays.sort(depthThenIndex);
        IntBuffer order = MemoryUtil.memAllocInt(seeThroughCount);
        try {
            int firstSeeThrough = firstSeeThrough();
            for (long entry : depthThenIndex) {
                order.put(firstSeeThrough + (int) entry);
            }
            order.flip();
            glBindBuffer(GL_TEXTURE_BUFFER, orderBuffer);
            glBufferSubData(GL_TEXTURE_BUFFER, 0L, order);
        } finally {
            MemoryUtil.memFree(order);
        }
    }

    // Ordered as signed ints the way the floats are ordered.
    private static int sortableBits(float value) {
        int bits = Float.floatToIntBits(value);
        return bits < 0 ? bits ^ Integer.MAX_VALUE : bits;
    }

    void bindQuads() {
        glBindTexture(GL_TEXTURE_BUFFER, quadTexture);
    }

    void bindTexels() {
        glBindTexture(GL_TEXTURE_BUFFER, texelTexture);
    }

    void bindOrder() {
        glBindTexture(GL_TEXTURE_BUFFER, orderTexture);
    }

    Vec3 anchor() {
        return anchor;
    }

    int solidCount() {
        return solidCount;
    }

    int firstGhost() {
        return solidCount;
    }

    int ghostCount() {
        return ghostCount;
    }

    int firstSeeThrough() {
        return solidCount + ghostCount;
    }

    int seeThroughCount() {
        return seeThroughCount;
    }

    private int quadCount() {
        return solidCount + ghostCount + seeThroughCount;
    }

    @Override
    public long bytes() {
        return bytes;
    }

    @Override
    public void delete() {
        glDeleteTextures(new int[]{quadTexture, texelTexture, orderTexture});
        glDeleteBuffers(new int[]{quadBuffer, texelBuffer, orderBuffer});
    }
}
