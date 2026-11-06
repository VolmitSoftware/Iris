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
package art.arcane.iris.engine.procedural;

/**
 * How long baking a pool of variants took, stage by stage, added up over every variant. {@link #NONE} times nothing,
 * so world generation bakes at full speed.
 */
public class BakeProfile {
    public static final BakeProfile NONE = new BakeProfile() {
        @Override
        public void startVariant() {
        }

        @Override
        public void lap(Stage stage) {
        }
    };

    /** The steps a variant is baked in, in order. */
    public enum Stage {
        PARTS("Parts"),
        PAINT("Paint"),
        DECORATE("Decorate"),
        LEAVES("Leaves"),
        BUILD("Build");

        private final String label;

        Stage(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final long[] nanosByStage = new long[Stage.values().length];
    private long lapStartNanos;
    private int variantCount;

    public void startVariant() {
        variantCount++;
        lapStartNanos = System.nanoTime();
    }

    /** Adds the time since the variant started, or since the last lap, to {@code stage}. */
    public void lap(Stage stage) {
        long now = System.nanoTime();
        nanosByStage[stage.ordinal()] += now - lapStartNanos;
        lapStartNanos = now;
    }

    public long nanos(Stage stage) {
        return nanosByStage[stage.ordinal()];
    }

    public long totalNanos() {
        long total = 0L;
        for (long nanos : nanosByStage) {
            total += nanos;
        }
        return total;
    }

    public int variantCount() {
        return variantCount;
    }
}
