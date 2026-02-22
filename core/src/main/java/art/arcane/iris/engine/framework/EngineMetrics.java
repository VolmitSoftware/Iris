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

package art.arcane.iris.engine.framework;

import art.arcane.volmlib.util.atomics.AtomicRollingSequence;
import art.arcane.volmlib.util.collection.KMap;
import lombok.Data;

@Data
public class EngineMetrics {
    private final AtomicRollingSequence total;
    private final AtomicRollingSequence updates;
    private final AtomicRollingSequence terrain;
    private final AtomicRollingSequence biome;
    private final AtomicRollingSequence parallax;
    private final AtomicRollingSequence parallaxInsert;
    private final AtomicRollingSequence post;
    private final AtomicRollingSequence perfection;
    private final AtomicRollingSequence api;
    private final AtomicRollingSequence decoration;
    private final AtomicRollingSequence cave;
    private final AtomicRollingSequence ravine;
    private final AtomicRollingSequence deposit;
    private final AtomicRollingSequence carveResolve;
    private final AtomicRollingSequence carveApply;

    public EngineMetrics(int mem) {
        this.total = new AtomicRollingSequence(mem);
        this.terrain = new AtomicRollingSequence(mem);
        this.api = new AtomicRollingSequence(mem);
        this.biome = new AtomicRollingSequence(mem);
        this.perfection = new AtomicRollingSequence(mem);
        this.parallax = new AtomicRollingSequence(mem);
        this.parallaxInsert = new AtomicRollingSequence(mem);
        this.post = new AtomicRollingSequence(mem);
        this.decoration = new AtomicRollingSequence(mem);
        this.updates = new AtomicRollingSequence(mem);
        this.cave = new AtomicRollingSequence(mem);
        this.ravine = new AtomicRollingSequence(mem);
        this.deposit = new AtomicRollingSequence(mem);
        this.carveResolve = new AtomicRollingSequence(mem);
        this.carveApply = new AtomicRollingSequence(mem);
    }

    public KMap<String, Double> pull() {
        KMap<String, Double> v = new KMap<>();
        v.put("total", total.getAverage());
        v.put("terrain", terrain.getAverage());
        v.put("biome", biome.getAverage());
        v.put("parallax", parallax.getAverage());
        v.put("parallax.insert", parallaxInsert.getAverage());
        v.put("post", post.getAverage());
        v.put("perfection", perfection.getAverage());
        v.put("decoration", decoration.getAverage());
        v.put("api", api.getAverage());
        v.put("updates", updates.getAverage());
        v.put("cave", cave.getAverage());
        v.put("ravine", ravine.getAverage());
        v.put("deposit", deposit.getAverage());
        v.put("carve.resolve", carveResolve.getAverage());
        v.put("carve.apply", carveApply.getAverage());

        return v;
    }
}
