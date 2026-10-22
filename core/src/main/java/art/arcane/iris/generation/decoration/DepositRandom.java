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

package art.arcane.iris.generation.decoration;

import art.arcane.volmlib.util.math.RNG;

public final class DepositRandom extends RNG {
    private static final long MULTIPLIER = 0x5DEECE66DL;
    private static final long ADDEND = 0xBL;
    private static final long MASK = (1L << 48) - 1L;
    private static final int JUMP_TABLE_SIZE = 48;
    private static final long[] JUMP_MULTIPLIERS = new long[JUMP_TABLE_SIZE];
    private static final long[] JUMP_ADDENDS = new long[JUMP_TABLE_SIZE];

    static {
        long multiplier = MULTIPLIER;
        long addend = ADDEND;
        for (int bit = 0; bit < JUMP_TABLE_SIZE; bit++) {
            JUMP_MULTIPLIERS[bit] = multiplier;
            JUMP_ADDENDS[bit] = addend;
            addend = (addend * multiplier + addend) & MASK;
            multiplier = (multiplier * multiplier) & MASK;
        }
    }

    private long state;

    public DepositRandom() {
        super(0L);
    }

    public DepositRandom(long seed) {
        super(seed);
    }

    @Override
    public void setSeed(long seed) {
        state = (seed ^ MULTIPLIER) & MASK;
    }

    @Override
    protected int next(int bits) {
        long advanced = (state * MULTIPLIER + ADDEND) & MASK;
        state = advanced;
        return (int) (advanced >>> (48 - bits));
    }

    public long state() {
        return state;
    }

    public void restore(long state) {
        this.state = state & MASK;
    }

    public void skip(long steps) {
        long remaining = steps;
        long advanced = state;
        for (int bit = 0; remaining != 0L && bit < JUMP_TABLE_SIZE; bit++, remaining >>>= 1) {
            if ((remaining & 1L) != 0L) {
                advanced = (advanced * JUMP_MULTIPLIERS[bit] + JUMP_ADDENDS[bit]) & MASK;
            }
        }
        state = advanced;
    }
}
