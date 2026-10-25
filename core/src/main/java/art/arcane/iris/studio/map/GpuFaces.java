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

import org.lwjgl.system.MemoryUtil;

import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL33.*;

/**
 * A layer's block faces on the graphics card, four ints each: first column, first row, bottom, and run with height.
 * The vertex shader reads them from a buffer texture and works out each face's two triangles itself, which cards draw
 * far faster than millions of tiny instances. Only for the GPU thread.
 */
final class GpuFaces {
    static final int CORNERS_PER_FACE = 6;
    private static final int INTS_PER_FACE = 4;
    private static final int BYTES_PER_FACE = INTS_PER_FACE * Integer.BYTES;
    private static final int FACES_PER_UPLOAD = 1 << 20;
    private static final int LARGEST_PACKED = 0xFFFF;
    private static final BlockFaces.Facing[] FACINGS = BlockFaces.Facing.values();
    private static final BlockFaces.Kind[] KINDS = BlockFaces.Kind.values();

    // Copied rather than kept, so the faces left in memory can be dropped once nothing else uses them.
    private final int[][] firstOf = new int[KINDS.length][FACINGS.length];
    private final int[][] countOf = new int[KINDS.length][FACINGS.length];
    private final long bytes;
    private final int buffer = glGenBuffers();
    private final int texture = glGenTextures();

    private GpuFaces(BlockFaces faces) {
        for (BlockFaces.Kind kind : KINDS) {
            for (BlockFaces.Facing facing : FACINGS) {
                firstOf[kind.ordinal()][facing.ordinal()] = faces.first(kind, facing);
                countOf[kind.ordinal()][facing.ordinal()] = faces.count(kind, facing);
            }
        }
        bytes = (long) faces.count() * BYTES_PER_FACE;
    }

    /**
     * @throws IllegalArgumentException when there are more faces than the card's buffer textures hold, or a face runs
     *                                  or reaches further than four ints can hold
     * @param mostFaces the card's GL_MAX_TEXTURE_BUFFER_SIZE
     */
    static GpuFaces upload(BlockFaces faces, int mostFaces) {
        if (faces.count() > mostFaces || (long) faces.count() * CORNERS_PER_FACE > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(faces.count() + " faces are more than the graphics card can hold");
        }
        GpuFaces uploaded = new GpuFaces(faces);
        try {
            uploaded.fill(faces);
        } catch (RuntimeException failure) {
            uploaded.delete();
            throw failure;
        }
        return uploaded;
    }

    // Packed a slice at a time, so large terrain doesn't need all of it twice in memory.
    private void fill(BlockFaces faces) {
        glBindBuffer(GL_TEXTURE_BUFFER, buffer);
        glBufferData(GL_TEXTURE_BUFFER, (long) Math.max(1, faces.count()) * BYTES_PER_FACE, GL_STATIC_DRAW);
        IntBuffer packed = MemoryUtil.memAllocInt(Math.max(1, Math.min(faces.count(), FACES_PER_UPLOAD)) * INTS_PER_FACE);
        try {
            for (int from = 0; from < faces.count(); from += FACES_PER_UPLOAD) {
                int to = Math.min(faces.count(), from + FACES_PER_UPLOAD);
                pack(faces, packed, from, to);
                glBufferSubData(GL_TEXTURE_BUFFER, (long) from * BYTES_PER_FACE, packed);
            }
        } finally {
            MemoryUtil.memFree(packed);
        }
        glBindTexture(GL_TEXTURE_BUFFER, texture);
        glTexBuffer(GL_TEXTURE_BUFFER, GL_RGBA32I, buffer);
    }

    private static void pack(BlockFaces faces, IntBuffer packed, int fromFace, int toFace) {
        packed.clear();
        for (int face = fromFace; face < toFace; face++) {
            int run = faces.columns(face);
            int reach = faces.height(face);
            if (run > LARGEST_PACKED || reach > LARGEST_PACKED) {
                throw new IllegalArgumentException("A face of " + run + " by " + reach + " is too large to draw on the graphics card");
            }
            packed.put(faces.firstColumn(face)).put(faces.firstRow(face)).put(faces.bottomY(face)).put(run | reach << 16);
        }
        packed.flip();
    }

    void bind() {
        glBindTexture(GL_TEXTURE_BUFFER, texture);
    }

    int first(BlockFaces.Kind kind, BlockFaces.Facing facing) {
        return firstOf[kind.ordinal()][facing.ordinal()];
    }

    int count(BlockFaces.Kind kind, BlockFaces.Facing facing) {
        return countOf[kind.ordinal()][facing.ordinal()];
    }

    long bytes() {
        return bytes;
    }

    void delete() {
        glDeleteTextures(texture);
        glDeleteBuffers(buffer);
    }
}
