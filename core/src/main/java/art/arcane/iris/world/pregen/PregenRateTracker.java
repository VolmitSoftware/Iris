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

package art.arcane.iris.world.pregen;

final class PregenRateTracker {
    private static final int CAPACITY = 64;
    private static final long TEN_SECONDS_MILLIS = 10_000L;
    private static final long THIRTY_SECONDS_MILLIS = 30_000L;
    private static final long SIXTY_SECONDS_MILLIS = 60_000L;

    private final long[] timestamps = new long[CAPACITY];
    private final long[] completed = new long[CAPACITY];
    private long startedAtMillis;
    private long startedCompleted;
    private int next;
    private int size;

    PregenRateTracker(long startedAtMillis, long startedCompleted) {
        reset(startedAtMillis, startedCompleted);
    }

    synchronized void reset(long atMillis, long completedCount) {
        startedAtMillis = atMillis;
        startedCompleted = completedCount;
        next = 0;
        size = 0;
        append(atMillis, completedCount);
    }

    synchronized PregenRates sample(long completedCount, long atMillis) {
        int latestIndex = latestIndex();
        long normalizedTime = Math.max(atMillis, timestamps[latestIndex]);
        if (normalizedTime == timestamps[latestIndex]) {
            completed[latestIndex] = completedCount;
        } else {
            append(normalizedTime, completedCount);
        }

        return new PregenRates(
                rate(startedCompleted, startedAtMillis, completedCount, normalizedTime),
                windowRate(completedCount, normalizedTime, TEN_SECONDS_MILLIS),
                windowRate(completedCount, normalizedTime, THIRTY_SECONDS_MILLIS),
                windowRate(completedCount, normalizedTime, SIXTY_SECONDS_MILLIS));
    }

    private double windowRate(long completedCount, long atMillis, long windowMillis) {
        long cutoff = atMillis - windowMillis;
        int selectedIndex = oldestIndex();
        for (int offset = 0; offset < size; offset++) {
            int index = (oldestIndex() + offset) % CAPACITY;
            if (timestamps[index] > cutoff) {
                break;
            }
            selectedIndex = index;
        }
        return rate(completed[selectedIndex], timestamps[selectedIndex], completedCount, atMillis);
    }

    private static double rate(long fromCompleted, long fromMillis, long toCompleted, long toMillis) {
        long elapsedMillis = toMillis - fromMillis;
        if (elapsedMillis <= 0L) {
            return 0D;
        }
        long delta = Math.max(0L, toCompleted - fromCompleted);
        return (double) delta * 1_000D / (double) elapsedMillis;
    }

    private void append(long atMillis, long completedCount) {
        timestamps[next] = atMillis;
        completed[next] = completedCount;
        next = (next + 1) % CAPACITY;
        if (size < CAPACITY) {
            size++;
        }
    }

    private int latestIndex() {
        return (next - 1 + CAPACITY) % CAPACITY;
    }

    private int oldestIndex() {
        return (next - size + CAPACITY) % CAPACITY;
    }
}
