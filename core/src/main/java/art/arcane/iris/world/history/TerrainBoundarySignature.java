package art.arcane.iris.world.history;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

public final class TerrainBoundarySignature {
    private final Column column;
    private final Samples samples;
    private final BoundaryColumnGeometry geometry;

    public TerrainBoundarySignature(Column column, Samples samples, BoundaryColumnGeometry geometry) {
        this.column = Objects.requireNonNull(column, "Boundary column");
        this.samples = Objects.requireNonNull(samples, "Boundary samples");
        this.geometry = Objects.requireNonNull(geometry, "Boundary geometry");
    }

    public BoundaryColumnGeometry geometry() {
        return geometry;
    }

    public Column column() {
        return column;
    }

    public Samples samples() {
        return samples;
    }

    public int blockX() {
        return column.blockX();
    }

    public int blockZ() {
        return column.blockZ();
    }

    public int surfaceHeight() {
        return column.surfaceHeight();
    }

    public int oceanFloorHeight() {
        return column.oceanFloorHeight();
    }

    public OptionalInt fluidHeight() {
        return column.fluidHeight();
    }

    public OptionalInt upperCeilingDepth() {
        return column.upperCeilingDepth();
    }

    public int sampleCount() {
        return samples.layout().sampleCount();
    }

    public int sampleY(int sampleIndex) {
        return samples.layout().sampleY(sampleIndex);
    }

    public String biomeAtSample(int sampleIndex) {
        return samples.biomes().biomeAtSample(sampleIndex);
    }

    public record Column(
            int blockX,
            int blockZ,
            int surfaceHeight,
            int oceanFloorHeight,
            OptionalInt fluidHeight,
            OptionalInt upperCeilingDepth
    ) {
        public Column {
            Objects.requireNonNull(fluidHeight, "Fluid height");
            Objects.requireNonNull(upperCeilingDepth, "Upper ceiling depth");
            if (upperCeilingDepth.isPresent() && upperCeilingDepth.getAsInt() <= 0) {
                throw new IllegalArgumentException("Present upper ceiling depth must be positive");
            }
        }
    }

    public record Samples(VerticalLayout layout, BiomeEncoding biomes) {
        public Samples {
            Objects.requireNonNull(layout, "Vertical layout");
            Objects.requireNonNull(biomes, "Biome samples");
            if (biomes.sampleCount() != layout.sampleCount()) {
                throw new IllegalArgumentException("Biome sample count must match the vertical layout");
            }
        }
    }

    public record VerticalLayout(int minimumY, int sampleStep, int sampleCount) {
        public VerticalLayout {
            if (sampleStep <= 0) {
                throw new IllegalArgumentException("Sample step must be positive");
            }
            if (sampleCount < 0) {
                throw new IllegalArgumentException("Sample count cannot be negative");
            }
            if (sampleCount > 0) {
                Math.toIntExact((long) minimumY + (long) (sampleCount - 1) * sampleStep);
            }
        }

        public int sampleY(int sampleIndex) {
            validateSampleIndex(sampleIndex, sampleCount);
            return Math.toIntExact((long) minimumY + (long) sampleIndex * sampleStep);
        }
    }

    public static final class BiomeEncoding {
        private final List<String> palette;
        private final short[] paletteIndices;

        public BiomeEncoding(List<String> palette, short[] paletteIndices) {
            this.palette = List.copyOf(Objects.requireNonNull(palette, "Biome palette"));
            this.paletteIndices = Objects.requireNonNull(paletteIndices, "Biome palette indices").clone();
            validatePalette();
        }

        public List<String> palette() {
            return palette;
        }

        public short[] paletteIndices() {
            return paletteIndices.clone();
        }

        public int sampleCount() {
            return paletteIndices.length;
        }

        public String biomeAtSample(int sampleIndex) {
            validateSampleIndex(sampleIndex, paletteIndices.length);
            return palette.get(paletteIndices[sampleIndex]);
        }

        @Override
        public boolean equals(Object compared) {
            if (this == compared) {
                return true;
            }
            if (!(compared instanceof BiomeEncoding encoding)) {
                return false;
            }
            return palette.equals(encoding.palette) && Arrays.equals(paletteIndices, encoding.paletteIndices);
        }

        @Override
        public int hashCode() {
            return 31 * palette.hashCode() + Arrays.hashCode(paletteIndices);
        }

        private void validatePalette() {
            if (paletteIndices.length > 0 && palette.isEmpty()) {
                throw new IllegalArgumentException("Biome palette is required when samples exist");
            }
            if (palette.size() > Short.MAX_VALUE + 1) {
                throw new IllegalArgumentException("Biome palette exceeds compact index capacity");
            }
            for (short paletteIndex : paletteIndices) {
                if (paletteIndex < 0 || paletteIndex >= palette.size()) {
                    throw new IllegalArgumentException("Biome palette index is outside the palette");
                }
            }
        }
    }

    private static void validateSampleIndex(int sampleIndex, int sampleCount) {
        if (sampleIndex < 0 || sampleIndex >= sampleCount) {
            throw new IndexOutOfBoundsException("Sample index " + sampleIndex + " outside sample count " + sampleCount);
        }
    }
}
