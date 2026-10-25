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

package art.arcane.iris.studio.map;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How a noise picks one of several regions or biomes, measured against the noise values in view: rarity splits the
 * noise from 0 to 1 into one band per option, and each spot gets the option whose band its noise value falls in.
 */
final class NoisePickSpread {
    static final int HISTOGRAM_BINS = 50;

    private final String title;
    private final List<Band> bands;
    private final long[] histogram = new long[HISTOGRAM_BINS];
    private final long[] samplesByBand;
    private long sampleCount;

    private NoisePickSpread(String title, List<Band> bands) {
        this.title = title;
        this.bands = List.copyOf(bands);
        this.samplesByBand = new long[bands.size()];
    }

    static NoisePickSpread of(String title, List<Option> options) {
        return new NoisePickSpread(title, splitNoise(options));
    }

    /**
     * The band of noise each option gets, in the order the noise meets them.
     */
    static List<Band> splitNoise(List<Option> options) {
        return bands(options);
    }

    void add(double noise) {
        if (bands.isEmpty() || !Double.isFinite(noise)) {
            return;
        }
        sampleCount++;
        histogram[Math.clamp((int) (noise * HISTOGRAM_BINS), 0, HISTOGRAM_BINS - 1)]++;
        samplesByBand[bandIndexOf(noise)]++;
    }

    String title() {
        return title;
    }

    List<Band> bands() {
        return bands;
    }

    long[] histogram() {
        return histogram;
    }

    long sampleCount() {
        return sampleCount;
    }

    /**
     * Every option once, with its bands' widths and samples added up, in the order the noise meets them.
     */
    List<Row> rows() {
        Map<String, Row> rows = new LinkedHashMap<>();
        for (int index = 0; index < bands.size(); index++) {
            Band band = bands.get(index);
            Row row = new Row(band.option(), band.width(), share(samplesByBand[index]));
            rows.merge(band.option().key(), row, Row::plus);
        }
        return new ArrayList<>(rows.values());
    }

    private double share(long count) {
        return sampleCount == 0 ? 0D : count / (double) sampleCount;
    }

    // The engine gives values below 0 the first option and values above 1 the last, the same as these bands.
    private int bandIndexOf(double noise) {
        for (int index = 0; index < bands.size(); index++) {
            if (noise <= bands.get(index).toNoise()) {
                return index;
            }
        }
        return bands.size() - 1;
    }

    // Mirrors RarityStream: each option gets a share of the noise in proportion to one over its rarity.
    private static List<Band> bands(List<Option> options) {
        double total = 0D;
        for (Option option : options) {
            total += 1D / option.rarity();
        }
        List<Band> bands = new ArrayList<>();
        double from = 0D;
        for (Option option : options) {
            double to = options.size() == 1 ? 1D : from + (1D / option.rarity()) / total;
            bands.add(new Band(option, from, to));
            from = to;
        }
        return bands;
    }

    /**
     * A region or biome the noise can pick.
     */
    record Option(String key, String name, int color, int rarity) {
    }

    /**
     * The noise values from {@code fromNoise} to {@code toNoise} that pick the option.
     */
    record Band(Option option, double fromNoise, double toNoise) {
        double width() {
            return toNoise - fromNoise;
        }
    }

    /**
     * @param weightShare how much of the noise from 0 to 1 picks the option
     * @param actualShare how much of the noise in view picks the option
     */
    record Row(Option option, double weightShare, double actualShare) {
        private static final double FAR_FROM_WEIGHT_FACTOR = 2D;

        Row plus(Row other) {
            return new Row(option, weightShare + other.weightShare, actualShare + other.actualShare);
        }

        boolean isFarBelowWeight() {
            return actualShare * FAR_FROM_WEIGHT_FACTOR < weightShare;
        }

        boolean isFarAboveWeight() {
            return actualShare > weightShare * FAR_FROM_WEIGHT_FACTOR;
        }
    }
}
