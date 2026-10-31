package art.arcane.iris.pack.datapack.export.compile.object;

import java.util.List;
import java.util.Objects;

public record TemplateChoice(BakedVariant variant, List<Integer> quarters, long weight) {
    public TemplateChoice {
        Objects.requireNonNull(variant, "Template choice variant");
        quarters = List.copyOf(quarters);
        if (quarters.isEmpty()) {
            throw new IllegalArgumentException("Template choice " + variant.id() + " needs at least one rotation");
        }
        for (int quarter : quarters) {
            if (quarter < 0 || quarter > 3) {
                throw new IllegalArgumentException("Template rotation quarter must be within [0, 3], got " + quarter);
            }
        }
        if (weight < 1) {
            throw new IllegalArgumentException("Template choice " + variant.id() + " needs a positive weight, got " + weight);
        }
    }
}
