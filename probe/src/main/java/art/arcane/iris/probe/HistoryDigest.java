/*
 * Iris is a World Generator for Minecraft Servers
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

package art.arcane.iris.probe;

import art.arcane.iris.world.history.ChunkGenerationSemantics;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.world.history.SavedBiomeChunk;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;

final class HistoryDigest {
    private final GenerationHistoryRuntimeRouter router;
    private final Map<Long, byte[]> chunks = new ConcurrentSkipListMap<>();

    HistoryDigest(GenerationHistoryRuntimeRouter router) {
        this.router = router;
    }

    void record(int chunkX, int chunkZ) throws IOException {
        StringBuilder canonical = new StringBuilder(4096);
        Optional<ChunkGenerationSemantics> semantics = router.history().semantics(chunkX, chunkZ);
        canonical.append(semantics.map(value -> value + "|" + value.pointsOfInterest()).orElse("none")).append('\n');
        Optional<SavedBiomeChunk> biomes = router.biomes().snapshot(chunkX, chunkZ);
        if (biomes.isEmpty()) {
            canonical.append("none");
        } else {
            SavedBiomeChunk chunk = biomes.get();
            canonical.append(chunk.header());
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    canonical.append('\n').append(chunk.column(localX, localZ));
                }
            }
        }
        chunks.put(((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL), sha256(canonical.toString().getBytes(StandardCharsets.UTF_8)));
    }

    String summary() {
        MessageDigest digest = newDigest();
        ByteBuffer key = ByteBuffer.allocate(Long.BYTES);
        for (Map.Entry<Long, byte[]> entry : chunks.entrySet()) {
            key.clear();
            key.putLong(entry.getKey());
            digest.update(key.array());
            digest.update(entry.getValue());
        }
        return "IRIS_GENPROBE_HISTORY version=1 chunks=" + chunks.size()
                + " digest=" + HexFormat.of().formatHex(digest.digest(), 0, 8);
    }

    private static byte[] sha256(byte[] bytes) {
        return newDigest().digest(bytes);
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("SHA-256 is unavailable", missing);
        }
    }
}
