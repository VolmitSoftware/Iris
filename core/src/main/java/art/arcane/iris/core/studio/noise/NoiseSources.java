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

package art.arcane.iris.core.studio.noise;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.world.biome.IrisBiomeGeneratorLink;
import art.arcane.iris.engine.object.noise.NoiseStyle;
import art.arcane.iris.engine.object.noise.expression.IrisExpression;
import art.arcane.iris.engine.object.terrain.IrisGenerator;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.volmlib.util.function.NoiseProvider;
import art.arcane.volmlib.util.math.RNG;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Every noise the noise view can show for a world: the dimension's overlay noise, the pack's generators and
 * expressions, and each built-in noise style.
 */
public final class NoiseSources {
    static final String OVERLAY_NOISE_ID = "world:overlay";
    // Generators mix their seed the way the Swing explorer did, so a seed shows the same noise in both.
    private static final long GENERATOR_SEED_SALT = 3245L;

    private final List<Entry> entries;

    private NoiseSources(List<Entry> entries) {
        this.entries = entries.stream().sorted(Comparator.comparing(Entry::category)).toList();
    }

    public static NoiseSources of(Engine engine) {
        List<Entry> entries = new ArrayList<>();
        addOverlayNoise(engine, entries);
        addPackGenerators(engine.getData(), entries);
        addPackExpressions(engine, entries);
        addStyles(entries);
        return new NoiseSources(entries);
    }

    static NoiseSources builtIn() {
        List<Entry> entries = new ArrayList<>();
        addStyles(entries);
        return new NoiseSources(entries);
    }

    public List<Source> list() {
        return entries.stream().map(Entry::source).toList();
    }

    /** The noise of a listed source at a seed, which sources that keep the world's seed ignore. */
    public NoiseProvider sampler(String id, long seed) {
        return entries.stream()
                .filter(entry -> entry.source().id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("No noise " + id))
                .factory()
                .create(seed);
    }

    // The overlay is summed the way the world adds it, with the world's seed, in blocks above the fluid.
    private static void addOverlayNoise(Engine engine, List<Entry> entries) {
        List<IrisBiomeGeneratorLink> overlay = engine.getDimension().getOverlayNoise();
        if (overlay.isEmpty()) {
            return;
        }
        BlockRange blocks = new BlockRange(
                overlay.stream().mapToInt(IrisBiomeGeneratorLink::getMin).sum(),
                overlay.stream().mapToInt(IrisBiomeGeneratorLink::getMax).sum());
        Source source = new Source(OVERLAY_NOISE_ID, "Overlay noise", NoiseCategory.PACK_GENERATORS.label(), false, blocks);
        entries.add(new Entry(source, NoiseCategory.PACK_GENERATORS, seed -> engine.getComplex().getOverlayStream()::get));
    }

    private static void addPackGenerators(IrisData data, List<Entry> entries) {
        for (String key : sortedKeys(data.getGeneratorLoader().getPossibleKeys())) {
            Source source = new Source("pack:" + key, displayName(key), NoiseCategory.PACK_GENERATORS.label(), true, null);
            entries.add(new Entry(source, NoiseCategory.PACK_GENERATORS, seed -> {
                IrisGenerator generator = data.getGeneratorLoader().load(key);
                if (generator == null) {
                    throw new NoSuchElementException("No pack generator " + key);
                }
                long mixedSeed = new RNG(seed).nextParallelRNG(GENERATOR_SEED_SALT).lmax();
                return (x, z) -> generator.getHeight(x, z, mixedSeed);
            }));
        }
    }

    // An expression the world uses keeps the random the world gives it, so it lines up with the height map; any other takes the view's seed.
    private static void addPackExpressions(Engine engine, List<Entry> entries) {
        IrisData data = engine.getData();
        Map<String, RNG> worldRandoms = ExpressionSeeds.of(engine);
        for (String key : sortedKeys(data.getExpressionLoader().getPossibleKeys())) {
            RNG worldRandom = worldRandoms.get(key);
            Source source = new Source("expression:" + key, displayName(key), NoiseCategory.PACK_EXPRESSIONS.label(), worldRandom == null, null);
            entries.add(new Entry(source, NoiseCategory.PACK_EXPRESSIONS, seed -> {
                IrisExpression expression = data.getExpressionLoader().load(key);
                if (expression == null) {
                    throw new NoSuchElementException("No pack expression " + key);
                }
                RNG rng = worldRandom == null ? new RNG(seed) : worldRandom;
                requireParses(expression, rng);
                return (x, z) -> expression.evaluate(rng, x, z);
            }));
        }
    }

    // An expression that doesn't parse fails on every sample; checking one turns that into a single readable error.
    private static void requireParses(IrisExpression expression, RNG rng) {
        try {
            expression.evaluate(rng, 0, 0);
        } catch (NullPointerException unparsed) {
            throw new IllegalStateException(expression.getLoadKey() + " doesn't parse; the server log has the details");
        }
    }

    private static void addStyles(List<Entry> entries) {
        for (NoiseStyle style : NoiseStyle.values()) {
            NoiseCategory category = NoiseCategory.of(style);
            Source source = new Source("style:" + style.name(), displayName(style.name()), category.label(), true, null);
            entries.add(new Entry(source, category, seed -> {
                CNG noise = style.create(new RNG(seed));
                return noise::noiseFast2D;
            }));
        }
    }

    private static List<String> sortedKeys(String[] keys) {
        if (keys == null) {
            return List.of();
        }
        return Arrays.stream(keys).sorted().toList();
    }

    private static String displayName(String rawName) {
        String spaced = rawName.toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    /**
     * One noise the view lists.
     *
     * @param usesSeed   whether the view's seed changes it; the overlay and the expressions the world uses keep the world's
     * @param blockRange where the noise is measured in blocks above the fluid, what it spans; null for noise of 0 to 1
     */
    public record Source(String id, String name, String category, boolean usesSeed, BlockRange blockRange) {
    }

    public record BlockRange(double lowest, double highest) {
    }

    @FunctionalInterface
    private interface SamplerFactory {
        NoiseProvider create(long seed);
    }

    private record Entry(Source source, NoiseCategory category, SamplerFactory factory) {
    }
}
