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

package art.arcane.iris.generation.runtime;

import art.arcane.volmlib.util.atomics.AtomicRollingSequence;
import art.arcane.volmlib.util.collection.KMap;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

@Data
public class EngineMetrics {
    private final StageTimer total;
    private final StageTimer updates;
    private final StageTimer terrain;
    private final StageTimer biome;
    private final StageTimer parallax;
    private final StageTimer parallaxInsert;
    private final StageTimer post;
    private final StageTimer perfection;
    private final StageTimer api;
    private final StageTimer decoration;
    private final StageTimer cave;
    private final StageTimer ravine;
    private final StageTimer deposit;
    private final StageTimer carveResolve;
    private final StageTimer carveApply;
    private final StageTimer noiseHeightInterpolate;
    private final StageTimer noiseHeightGenerator;
    private final StageTimer contextPrefill;
    private final StageTimer contextPrefillHeight;
    private final StageTimer contextPrefillBiome;
    private final StageTimer contextPrefillRock;
    private final StageTimer contextPrefillFluid;
    private final StageTimer contextPrefillRegion;
    private final StageTimer contextPrefillCave;
    private final StageTimer pregenWaitPermit;
    private final StageTimer pregenWaitAdaptive;

    public EngineMetrics(int mem) {
        this.total = new StageTimer(mem);
        this.terrain = new StageTimer(mem);
        this.api = new StageTimer(mem);
        this.biome = new StageTimer(mem);
        this.perfection = new StageTimer(mem);
        this.parallax = new StageTimer(mem);
        this.parallaxInsert = new StageTimer(mem);
        this.post = new StageTimer(mem);
        this.decoration = new StageTimer(mem);
        this.updates = new StageTimer(mem);
        this.cave = new StageTimer(mem);
        this.ravine = new StageTimer(mem);
        this.deposit = new StageTimer(mem);
        this.carveResolve = new StageTimer(mem);
        this.carveApply = new StageTimer(mem);
        this.noiseHeightInterpolate = new StageTimer(mem);
        this.noiseHeightGenerator = new StageTimer(mem);
        this.contextPrefill = new StageTimer(mem);
        this.contextPrefillHeight = new StageTimer(mem);
        this.contextPrefillBiome = new StageTimer(mem);
        this.contextPrefillRock = new StageTimer(mem);
        this.contextPrefillFluid = new StageTimer(mem);
        this.contextPrefillRegion = new StageTimer(mem);
        this.contextPrefillCave = new StageTimer(mem);
        this.pregenWaitPermit = new StageTimer(mem);
        this.pregenWaitAdaptive = new StageTimer(mem);
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

    public Map<String, Double> telemetryAverages() {
        Map<String, Double> averages = new LinkedHashMap<>();
        averages.put("total", total.getAverage());
        averages.put("updates", updates.getAverage());
        averages.put("terrain", terrain.getAverage());
        averages.put("biome", biome.getAverage());
        averages.put("post", post.getAverage());
        averages.put("perfection", perfection.getAverage());
        averages.put("decoration", decoration.getAverage());
        averages.put("cave", cave.getAverage());
        averages.put("deposit", deposit.getAverage());
        averages.put("carve.resolve", carveResolve.getAverage());
        averages.put("carve.apply", carveApply.getAverage());
        averages.put("context.prefill", contextPrefill.getAverage());
        averages.put("pregen.wait.permit", pregenWaitPermit.getAverage());
        averages.put("pregen.wait.adaptive", pregenWaitAdaptive.getAverage());
        averages.entrySet().removeIf(entry -> !Double.isFinite(entry.getValue()) || entry.getValue() < 0D);
        return Map.copyOf(averages);
    }

    public Map<String, StageTotal> stageTotals() {
        Map<String, StageTotal> totals = new LinkedHashMap<>();
        totals.put("total", total.snapshot());
        totals.put("terrain", terrain.snapshot());
        totals.put("biome", biome.snapshot());
        totals.put("parallax", parallax.snapshot());
        totals.put("parallax.insert", parallaxInsert.snapshot());
        totals.put("post", post.snapshot());
        totals.put("perfection", perfection.snapshot());
        totals.put("decoration", decoration.snapshot());
        totals.put("api", api.snapshot());
        totals.put("updates", updates.snapshot());
        totals.put("cave", cave.snapshot());
        totals.put("ravine", ravine.snapshot());
        totals.put("deposit", deposit.snapshot());
        totals.put("carve.resolve", carveResolve.snapshot());
        totals.put("carve.apply", carveApply.snapshot());
        totals.put("noise.height.interpolate", noiseHeightInterpolate.snapshot());
        totals.put("noise.height.generator", noiseHeightGenerator.snapshot());
        totals.put("context.prefill", contextPrefill.snapshot());
        totals.put("context.prefill.height", contextPrefillHeight.snapshot());
        totals.put("context.prefill.biome", contextPrefillBiome.snapshot());
        totals.put("context.prefill.rock", contextPrefillRock.snapshot());
        totals.put("context.prefill.fluid", contextPrefillFluid.snapshot());
        totals.put("context.prefill.region", contextPrefillRegion.snapshot());
        totals.put("context.prefill.cave", contextPrefillCave.snapshot());
        totals.put("pregen.wait.permit", pregenWaitPermit.snapshot());
        totals.put("pregen.wait.adaptive", pregenWaitAdaptive.snapshot());
        return totals;
    }

    public static final class StageTimer extends AtomicRollingSequence {
        private final DoubleAdder totalMillis = new DoubleAdder();
        private final LongAdder samples = new LongAdder();

        public StageTimer(int size) {
            super(size);
        }

        @Override
        public void put(double millis) {
            super.put(millis);
            totalMillis.add(millis);
            samples.increment();
        }

        public StageTotal snapshot() {
            return new StageTotal(totalMillis.sum(), samples.sum());
        }
    }

    public record StageTotal(double millis, long samples) {
        public StageTotal since(StageTotal earlier) {
            return new StageTotal(millis - earlier.millis(), samples - earlier.samples());
        }
    }
}
