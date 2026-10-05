package com.volmit.host;

import com.volmit.generation.Dependencies;
import com.volmit.generation.GenerationContext;
import com.volmit.generation.GenerationModifier;
import com.volmit.hunk.Hunk;
import com.volmit.resource.ResourceId;
import java.util.Objects;

/** Pack-load snapshot of modifier identity, requirements, type and prepared reach.
 * The occurrence identifies one configuration, independent of its shared ordering identity.
 */
public final class ConfiguredModifier<T> {
    private final ResourceId occurrence;
    private final Class<T> outputType;
    private final GenerationModifier<T> modifier;
    private final ResourceId id;
    private final Dependencies dependencies;
    private final int mantleRadius;

    public ConfiguredModifier(
            ResourceId occurrence,
            Class<T> outputType,
            GenerationModifier<T> modifier) {
        this.occurrence = Objects.requireNonNull(occurrence, "occurrence");
        this.outputType = Objects.requireNonNull(outputType, "outputType");
        if (outputType.isPrimitive()) {
            throw new IllegalArgumentException("Output requires a reference type");
        }
        this.modifier = Objects.requireNonNull(modifier, "modifier");
        id = ResourceId.parse(modifier.id());
        dependencies = Objects.requireNonNull(modifier.depends(), "dependencies");
        mantleRadius = modifier.mantleRadius();
        if (mantleRadius < 0) {
            throw new IllegalArgumentException("Negative mantle radius: " + id);
        }
    }

    public ResourceId occurrence() {
        return occurrence;
    }

    public Class<T> outputType() {
        return outputType;
    }

    public ResourceId id() {
        return id;
    }

    public Dependencies dependencies() {
        return dependencies;
    }

    public int mantleRadius() {
        return mantleRadius;
    }

    public Class<?> implementationType() {
        return modifier.getClass();
    }

    /** Host must prepare views/predecessor work and abort publication if this throws. */
    public void modify(GenerationContext context, Hunk<T> output) {
        modifier.modify(
                Objects.requireNonNull(context, "context"),
                Objects.requireNonNull(output, "output"));
    }
}
