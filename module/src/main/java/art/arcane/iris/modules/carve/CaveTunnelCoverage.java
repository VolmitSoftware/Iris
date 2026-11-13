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

package art.arcane.iris.modules.carve;

import art.arcane.iris.modules.carve.CaveValue.Bounds;
import com.volmit.config.Desc;
import com.volmit.config.JsonForms;
import com.volmit.noise.NoiseStyle;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Where the tunnels of a {@link CaveTunnel} occur: the share of the underground that has them, in areas a noise shapes,
 * and how gradually they thin out at the edge of an area. Written as the share alone, {@code 0.4}, or as
 * {@code {"share": 0.4, "softness": 0.3, "style": {...}}} with each member optional.
 */
@Desc("Where tunnels occur: a share, or {share, softness, style}.")
@JsonForms(CaveTunnelCoverage.Forms.class)
public final class CaveTunnelCoverage {
    /** The softness that thins tunnels out over the same stretch they always did before softness was a key. */
    public static final double DEFAULT_SOFTNESS = 0.3D;
    static final CaveTunnelCoverage EVERYWHERE = new CaveTunnelCoverage(1D, CaveValue.of(1), CaveValue.of(DEFAULT_SOFTNESS), null);
    private static final Bounds SHARE = new Bounds(0, 1);
    private static final Bounds SOFTNESS = new Bounds(0, 1);

    private final Object written;
    private final CaveValue share;
    private final CaveValue softness;
    private final NoiseStyle style;

    private CaveTunnelCoverage(Object written, CaveValue share, CaveValue softness, NoiseStyle style) {
        this.written = written;
        this.share = share;
        this.softness = softness;
        this.style = style;
    }

    /** Share of the underground that has tunnels, from 0 to 1. */
    public CaveValue share() {
        return share;
    }

    /** How gradually tunnels thin out at the edge of an area, from 0 for abrupt ends to 1 for long tails. */
    public CaveValue softness() {
        return softness;
    }

    /** The noise that shapes the areas with tunnels; empty where the pack wrote none. */
    public Optional<NoiseStyle> style() {
        return Optional.ofNullable(style);
    }

    void requireValuesWithinBounds(String path, List<String> problems) {
        share.requireWithin(SHARE, path + "." + Forms.SHARE, problems);
        softness.requireWithin(SOFTNESS, path + "." + Forms.SOFTNESS, problems);
    }

    @Override
    public String toString() {
        return String.valueOf(written);
    }

    /** Reads the forms a pack writes and writes a coverage back exactly as it was read. */
    static final class Forms implements JsonForms.Codec<CaveTunnelCoverage> {
        static final String SHARE = "share";
        static final String SOFTNESS = "softness";
        static final String STYLE = "style";
        private static final Set<String> MEMBERS = Set.of(SHARE, SOFTNESS, STYLE);

        @Override
        public CaveTunnelCoverage read(Object json, JsonForms.Parts parts) {
            return switch (json) {
                case Number number -> new CaveTunnelCoverage(json, CaveValue.of(number.doubleValue()), EVERYWHERE.softness, null);
                case Map<?, ?> object -> spelledOut(object, parts);
                default -> throw new IllegalArgumentException("A tunnel coverage is a share or {share, softness, style}, not " + json);
            };
        }

        @Override
        public Object write(CaveTunnelCoverage value) {
            return value.written;
        }

        private static CaveTunnelCoverage spelledOut(Map<?, ?> object, JsonForms.Parts parts) {
            for (Object member : object.keySet()) {
                if (!MEMBERS.contains(member)) {
                    throw new IllegalArgumentException("A tunnel coverage has share, softness and style, not " + member);
                }
            }
            CaveValue share = valueOrDefault(object.get(SHARE), EVERYWHERE.share, parts);
            CaveValue softness = valueOrDefault(object.get(SOFTNESS), EVERYWHERE.softness, parts);
            Object writtenStyle = object.get(STYLE);
            NoiseStyle style = writtenStyle == null ? null : parts.read(writtenStyle, NoiseStyle.class);
            return new CaveTunnelCoverage(object, share, softness, style);
        }

        private static CaveValue valueOrDefault(Object written, CaveValue fallback, JsonForms.Parts parts) {
            return written == null ? fallback : parts.read(written, CaveValue.class);
        }
    }
}
