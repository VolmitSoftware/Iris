/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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

package com.volmit.iris.util.math;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

public class RNGV2 extends SecureRandom {
    private static final long serialVersionUID = 5222938581174415179L;
    private static final char[] CHARGEN = "1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ-=!@#$%^&*()_+`~[];',./<>?:\\\"{}|\\\\".toCharArray();
    private final long sx;

    // Constructor with no seed
    public RNGV2() {
        super();
        sx = 0;
    }

    public RNGV2(long seed) {
        super();
        this.setSeed(seed);
        this.sx = seed;
    }

    // Constructor with a string seed
    public RNGV2(String seed) {
        this(UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).getLeastSignificantBits() +
                UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).getMostSignificantBits() +
                (seed.length() * 32564L));
    }

    public RNGV2 nextParallelRNG(int signature) {
        return new RNGV2(sx + signature);
    }

    public RNGV2 nextParallelRNG(long signature) {
        return new RNGV2(sx + signature);
    }

    public String s(int length) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < length; i++) {
            sb.append(c());
        }

        return sb.toString();
    }

    public char c() {
        return CHARGEN[i(CHARGEN.length - 1)];
    }

    // Pick a random enum
    public <T> T e(Class<T> t) {
        T[] c = t.getEnumConstants();
        return c[i(c.length)];
    }

    public boolean b() {
        return nextBoolean();
    }

    public boolean b(double percent) {
        return d() > percent;
    }

    public short si(int lowerBound, int upperBound) {
        return (short) (lowerBound + (nextFloat() * ((upperBound - lowerBound) + 1)));
    }

    public short si(int upperBound) {
        return si(0, upperBound);
    }

    public short si() {
        return si(1);
    }

    public float f(float lowerBound, float upperBound) {
        return lowerBound + (nextFloat() * ((upperBound - lowerBound)));
    }

    public float f(float upperBound) {
        return f(0, upperBound);
    }

    public float f() {
        return f(1);
    }

    public double d(double lowerBound, double upperBound) {
        return lowerBound + (nextDouble() * (upperBound - lowerBound));
    }

    public double d(double upperBound) {
        return d(0, upperBound);
    }

    public double d() {
        return nextDouble();
    }

    public int i(int lowerBound, int upperBound) {
        if (lowerBound >= upperBound) {
            throw new IllegalArgumentException("Upper bound must be greater than lower bound");
        }
        return lowerBound + this.nextInt(upperBound - lowerBound + 1);
    }

    public int i(int upperBound) {
        return i(0, upperBound);
    }

    public long l(long lowerBound, long upperBound) {
        return Math.round(d(lowerBound, upperBound));
    }

    public long l(long upperBound) {
        return l(0, upperBound);
    }

    public int imax() {
        return i(Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    public long lmax() {
        return l(Long.MIN_VALUE, Long.MAX_VALUE);
    }

    public float fmax() {
        return f(Float.MIN_VALUE, Float.MAX_VALUE);
    }

    public double dmax() {
        return d(Double.MIN_VALUE, Double.MAX_VALUE);
    }

    public short simax() {
        return si(Short.MIN_VALUE, Short.MAX_VALUE);
    }

    public boolean chance(double chance) {
        return nextDouble() <= chance;
    }

    public <T> T pick(List<T> pieces) {
        if (pieces.isEmpty()) {
            return null;
        }

        if (pieces.size() == 1) {
            return pieces.get(0);
        }

        return pieces.get(this.nextInt(pieces.size()));
    }

    public long getSeed() {
        return sx;
    }
}

