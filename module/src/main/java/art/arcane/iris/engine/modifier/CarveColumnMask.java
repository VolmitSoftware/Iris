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

package art.arcane.iris.engine.modifier;

import java.util.Arrays;

final class CarveColumnMask {
    private long[] words = new long[8];
    private int maxWord = -1;

    void add(int y) {
        if (y < 0) {
            return;
        }

        int wordIndex = y >> 6;
        if (wordIndex >= words.length) {
            words = Arrays.copyOf(words, Math.max(words.length << 1, wordIndex + 1));
        }

        words[wordIndex] |= 1L << (y & 63);
        if (wordIndex > maxWord) {
            maxWord = wordIndex;
        }
    }

    int nextSetBit(int fromBit) {
        if (maxWord < 0) {
            return -1;
        }

        int startBit = Math.max(0, fromBit);
        int wordIndex = startBit >> 6;
        if (wordIndex > maxWord) {
            return -1;
        }

        long word = words[wordIndex] & (-1L << (startBit & 63));
        while (true) {
            if (word != 0L) {
                return (wordIndex << 6) + Long.numberOfTrailingZeros(word);
            }

            wordIndex++;
            if (wordIndex > maxWord) {
                return -1;
            }
            word = words[wordIndex];
        }
    }

    boolean isEmpty() {
        return maxWord < 0;
    }

    boolean contains(int y) {
        if (y < 0) {
            return false;
        }

        int wordIndex = y >> 6;
        if (wordIndex > maxWord) {
            return false;
        }

        return (words[wordIndex] & (1L << (y & 63))) != 0L;
    }

    void clear() {
        if (maxWord < 0) {
            return;
        }

        for (int index = 0; index <= maxWord; index++) {
            words[index] = 0L;
        }
        maxWord = -1;
    }
}
