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
    private final AtomicRollingSequence noiseHeightInterpolate;
    private final AtomicRollingSequence noiseHeightGenerator;
    private final AtomicRollingSequence contextPrefill;
    private final AtomicRollingSequence contextPrefillHeight;
    private final AtomicRollingSequence contextPrefillBiome;
    private final AtomicRollingSequence contextPrefillRock;
    private final AtomicRollingSequence contextPrefillFluid;
    private final AtomicRollingSequence contextPrefillRegion;
    private final AtomicRollingSequence contextPrefillCave;
    private final AtomicRollingSequence pregenWaitPermit;
    private final AtomicRollingSequence pregenWaitAdaptive;

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
        this.noiseHeightInterpolate = new AtomicRollingSequence(mem);
        this.noiseHeightGenerator = new AtomicRollingSequence(mem);
        this.contextPrefill = new AtomicRollingSequence(mem);
        this.contextPrefillHeight = new AtomicRollingSequence(mem);
        this.contextPrefillBiome = new AtomicRollingSequence(mem);
        this.contextPrefillRock = new AtomicRollingSequence(mem);
        this.contextPrefillFluid = new AtomicRollingSequence(mem);
        this.contextPrefillRegion = new AtomicRollingSequence(mem);
        this.contextPrefillCave = new AtomicRollingSequence(mem);
        this.pregenWaitPermit = new AtomicRollingSequence(mem);
        this.pregenWaitAdaptive = new AtomicRollingSequence(mem);
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
        v.put("noise.height.interpolate", noiseHeightInterpolate.getAverage());
        v.put("noise.height.generator", noiseHeightGenerator.getAverage());
        v.put("context.prefill", contextPrefill.getAverage());
        v.put("context.prefill.height", contextPrefillHeight.getAverage());
        v.put("context.prefill.biome", contextPrefillBiome.getAverage());
        v.put("context.prefill.rock", contextPrefillRock.getAverage());
        v.put("context.prefill.fluid", contextPrefillFluid.getAverage());
        v.put("context.prefill.region", contextPrefillRegion.getAverage());
        v.put("context.prefill.cave", contextPrefillCave.getAverage());
        v.put("pregen.wait.permit", pregenWaitPermit.getAverage());
        v.put("pregen.wait.adaptive", pregenWaitAdaptive.getAverage());

        return v;
    }
}
