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

package com.volmit.iris.engine.framework;

import com.volmit.iris.engine.object.NoiseStyle;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.noise.CNG;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

@Data
public class SeedManager {
    ////////////////////////////////////////////////////////////////////
    private static final String IRIS_SIGNATURE = "Iris World Generator";
    private static final long IRIS_TERRAIN_VERSION = 1;
    ////////////////////////////////////////////////////////////////////
    private final RNG rlock;
    private final CNG soup;
    private final long seed;
    private final long complex;
    private final long complexStreams;
    private final long basic;
    private final long height;
    private final long component;
    private final long script;
    private final long mantle;
    private final long entity;
    private final long biome;
    private final long decorator;
    private final long terrain;
    private final long spawn;
    private final long jigsaw;
    private final long carve;
    private final long deposit;
    private final long post;
    private final long bodies;
    private final long mode;
    @Setter(AccessLevel.NONE)
    private long fullMixedSeed;

    public SeedManager(long seed) {
        soup = createSoup(seed);
        rlock = new RNG(Double.doubleToLongBits(soup.fitDouble(Double.MIN_VALUE, Double.MAX_VALUE, seed + 1337, seed * 69, seed)));
        this.seed = seed;
        complex = of("complex");
        complexStreams = of("complex_streams");
        basic = of("basic");
        height = of("height");
        component = of("component");
        script = of("script");
        mantle = of("mantle");
        entity = of("entity");
        biome = of("biome");
        decorator = of("decorator");
        terrain = of("terrain");
        spawn = of("spawn");
        jigsaw = of("jigsaw");
        carve = of("carve");
        deposit = of("deposit");
        post = of("post");
        bodies = of("bodies");
        mode = of("mode");
    }

    private long of(String name) {
        RNG rng = new RNG(name + IRIS_SIGNATURE + "::" + IRIS_TERRAIN_VERSION + ((seed + rlock.imax()) * rlock.lmax()));
        long f = rlock.imax() * ((rlock.chance(0.5) ? 1 : -1) * (name.hashCode() + Double.doubleToLongBits(soup.fitDouble(Double.MIN_VALUE, Double.MAX_VALUE, rng.imax(), rng.imax(), rng.imax()))));
        fullMixedSeed += (f * rlock.imax());
        return f;
    }

    private CNG createSoup(long seed) {
        RNG a = new RNG((seed - 2043905) * 4_385_677_888L);
        RNG b = new RNG((seed * -305) + 45_858_458_555L);
        RNG c = new RNG((seed * (a.lmax() - b.lmax())) + IRIS_SIGNATURE.hashCode());
        RNG d = new RNG((seed - (c.lmax() * -IRIS_TERRAIN_VERSION)) + IRIS_TERRAIN_VERSION);
        RNG e = new RNG((IRIS_TERRAIN_VERSION * 42) + IRIS_SIGNATURE);
        double gsoup = 0;
        int gk = a.i(1_000, 10_000);
        for (char i : (a.s(4) + b.s(4) + c.s(4) + d.s(4) + e.s(4)).toCharArray()) {
            gsoup += ((gk * b.d(3, Math.PI)) / c.d(10, 18 * Math.E)) + 6_549;
            gsoup *= d.d(90.5, 1_234_567);
            gsoup += e.d(39.95, 99.25);
        }

        return NoiseStyle.STATIC.create(new RNG(4_966_866 * Double.doubleToLongBits((gsoup * a.imax() + b.imax() + c.lmax() + d.lmax()) * e.lmax())));
    }
}
