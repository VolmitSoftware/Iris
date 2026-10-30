package art.arcane.iris.pack.datapack.export.compile.object;

import java.util.List;
import java.util.Objects;

public record ObjectTemplates(String objectKey, List<TemplateChoice> choices) {
    public ObjectTemplates {
        Objects.requireNonNull(objectKey, "Object key");
        choices = List.copyOf(choices);
    }

    public int footprint() {
        int footprint = 0;
        for (TemplateChoice choice : choices) {
            footprint = Math.max(footprint, choice.variant().volume().footprint());
        }
        return footprint;
    }

    public long totalWeight() {
        long total = 0;
        for (TemplateChoice choice : choices) {
            total += choice.weight();
        }
        return total;
    }
}
