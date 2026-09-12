/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.platform.protocol;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.studio.render.IrisRenderer;
import art.arcane.iris.studio.render.RenderType;
import art.arcane.iris.spi.protocol.IrisMessage;
import art.arcane.iris.spi.protocol.IrisProtocol;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;

public final class IrisTileEncoder {
    public static final int TILE_PIXELS = 128;
    public static final int MAX_ZOOM_LEVEL = 8;
    public static final int MODE_RAW_RGB = 0;
    public static final int MODE_PALETTE = 1;
    private static final int MAX_PALETTE_COLORS = 256;
    private static final int RGB_MASK = 0xFFFFFF;

    private IrisTileEncoder() {
    }

    public static List<IrisMessage.VisionTile> encode(Engine engine, int tileX, int tileZ, int zoomLevel, int sequence) {
        int zoom = clampZoom(zoomLevel);
        int blocksPerPixel = 1 << zoom;
        double size = (double) TILE_PIXELS * blocksPerPixel;
        double sx = (double) tileX * size;
        double sz = (double) tileZ * size;
        BufferedImage image = new IrisRenderer(engine).render(sx, sz, size, TILE_PIXELS, RenderType.BIOME);
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        byte[] blob = encodePixels(pixels, TILE_PIXELS, TILE_PIXELS);
        return splitIntoChunks(blob, tileX, tileZ, zoom, sequence);
    }

    public static byte[] encodePixels(int[] pixels, int width, int height) {
        Map<Integer, Integer> palette = buildPalette(pixels);
        ByteArrayOutputStream raw = new ByteArrayOutputStream(pixels.length);
        try (DataOutputStream out = new DataOutputStream(raw)) {
            out.writeInt(width);
            out.writeInt(height);
            if (palette != null) {
                out.writeByte(MODE_PALETTE);
                out.writeInt(palette.size());
                for (Integer color : palette.keySet()) {
                    out.writeByte(color >> 16 & 0xFF);
                    out.writeByte(color >> 8 & 0xFF);
                    out.writeByte(color & 0xFF);
                }
                for (int pixel : pixels) {
                    out.writeByte(palette.get(pixel & RGB_MASK));
                }
            } else {
                out.writeByte(MODE_RAW_RGB);
                for (int pixel : pixels) {
                    out.writeByte(pixel >> 16 & 0xFF);
                    out.writeByte(pixel >> 8 & 0xFF);
                    out.writeByte(pixel & 0xFF);
                }
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("vision tile pixel encode failed", failure);
        }
        return deflate(raw.toByteArray());
    }

    public static List<IrisMessage.VisionTile> splitIntoChunks(byte[] blob, int tileX, int tileZ, int zoomLevel, int sequence) {
        int chunkBytes = IrisProtocol.VISION_TILE_MAX_CHUNK_BYTES;
        int chunkCount = Math.max(1, (blob.length + chunkBytes - 1) / chunkBytes);
        List<IrisMessage.VisionTile> chunks = new ArrayList<>(chunkCount);
        for (int index = 0; index < chunkCount; index++) {
            int start = index * chunkBytes;
            int end = Math.min(start + chunkBytes, blob.length);
            byte[] slice = new byte[end - start];
            System.arraycopy(blob, start, slice, 0, slice.length);
            chunks.add(new IrisMessage.VisionTile(tileX, tileZ, zoomLevel, sequence, index, chunkCount, slice));
        }
        return chunks;
    }

    private static Map<Integer, Integer> buildPalette(int[] pixels) {
        LinkedHashMap<Integer, Integer> palette = new LinkedHashMap<>();
        for (int pixel : pixels) {
            int color = pixel & RGB_MASK;
            if (!palette.containsKey(color)) {
                if (palette.size() >= MAX_PALETTE_COLORS) {
                    return null;
                }
                palette.put(color, palette.size());
            }
        }
        return palette;
    }

    private static byte[] deflate(byte[] input) {
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        deflater.setInput(input);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, input.length / 2));
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) {
            int produced = deflater.deflate(buffer);
            out.write(buffer, 0, produced);
        }
        deflater.end();
        return out.toByteArray();
    }

    private static int clampZoom(int zoomLevel) {
        return Math.max(0, Math.min(zoomLevel, MAX_ZOOM_LEVEL));
    }
}
