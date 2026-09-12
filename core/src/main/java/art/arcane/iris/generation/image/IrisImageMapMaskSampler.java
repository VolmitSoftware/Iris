package art.arcane.iris.generation.image;


import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class IrisImageMapMaskSampler {
    private static final IrisImageMapMaskSampler EMPTY = new IrisImageMapMaskSampler(List.of());

    private final List<Layer> layers;

    public IrisImageMapMaskSampler(List<Layer> layers) {
        this.layers = List.copyOf(Objects.requireNonNull(layers, "Image-map mask layers"));
    }

    public static IrisImageMapMaskSampler empty() {
        return EMPTY;
    }

    public static Layer layer(CompiledIrisImageMap compiled, IrisImageMapMask definition) {
        Objects.requireNonNull(definition, "Image-map mask definition");
        if (definition.getOperation() == null) {
            throw new IrisImageMapValidationException("Image-map mask operation is required");
        }
        if (!unitRange(definition.getThreshold()) || !unitRange(definition.getFalloff())) {
            throw new IrisImageMapValidationException("Image-map mask threshold and falloff must be within 0..1");
        }
        return new Layer(
                Objects.requireNonNull(compiled, "Compiled image-map mask"),
                definition.getOperation(),
                definition.isInverted(),
                definition.getThreshold(),
                definition.getFalloff()
        );
    }

    public static IrisImageMapMaskSampler of(
            List<CompiledIrisImageMap> compiled,
            List<IrisImageMapMask> definitions
    ) {
        if (compiled.size() != definitions.size()) {
            throw new IllegalArgumentException("Compiled image-map masks and definitions must have equal sizes");
        }
        List<Layer> layers = new ArrayList<>(compiled.size());
        for (int index = 0; index < compiled.size(); index++) {
            layers.add(layer(compiled.get(index), definitions.get(index)));
        }
        return layers.isEmpty() ? empty() : new IrisImageMapMaskSampler(layers);
    }

    public boolean isEmpty() {
        return layers.isEmpty();
    }

    public double sample(double worldX, double worldZ) {
        double weight = 1D;
        for (Layer layer : layers) {
            double value = layer.sample(worldX, worldZ);
            weight = switch (layer.operation()) {
                case MULTIPLY -> weight * value;
                case MINIMUM -> Math.min(weight, value);
                case MAXIMUM -> Math.max(weight, value);
                case ADD -> weight + value;
                case SUBTRACT -> weight - value;
            };
            weight = clamp01(weight);
        }
        return weight;
    }

    private static boolean unitRange(double value) {
        return Double.isFinite(value) && value >= 0D && value <= 1D;
    }

    private static double clamp01(double value) {
        return Math.max(0D, Math.min(1D, value));
    }

    public record Layer(
            CompiledIrisImageMap compiled,
            IrisImageMapMaskOperation operation,
            boolean inverted,
            double threshold,
            double falloff
    ) {
        public Layer {
            Objects.requireNonNull(compiled, "Compiled image-map mask");
            Objects.requireNonNull(operation, "Image-map mask operation");
            if (!unitRange(threshold) || !unitRange(falloff)) {
                throw new IrisImageMapValidationException(
                        "Image-map mask threshold and falloff must be within 0..1"
                );
            }
        }

        private double sample(double worldX, double worldZ) {
            double value = compiled.sampleNormalized(worldX, worldZ);
            if (inverted) {
                value = 1D - value;
            }
            if (threshold == 0D && falloff == 0D) {
                return value;
            }
            if (falloff == 0D) {
                return value >= threshold ? 1D : 0D;
            }
            return clamp01((value - threshold) / falloff);
        }
    }
}
