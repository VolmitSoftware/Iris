package art.arcane.iris.pack.datapack.export.compile.object;

import art.arcane.iris.pack.datapack.export.compile.decoration.GenerationStep;
import art.arcane.iris.pack.datapack.export.ir.JsonResource;

import java.util.List;
import java.util.Objects;

public record CompiledPlacement(GenerationStep step, List<String> placedFeatures, List<String> structures, List<JsonResource> resources) {
    public CompiledPlacement {
        Objects.requireNonNull(step, "Placement step");
        placedFeatures = List.copyOf(placedFeatures);
        structures = List.copyOf(structures);
        resources = List.copyOf(resources);
    }
}
