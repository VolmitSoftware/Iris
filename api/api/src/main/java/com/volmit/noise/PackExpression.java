package com.volmit.noise;

import com.volmit.resource.ResourceId;
import java.util.Objects;

/** An expression of the loaded pack, which a {@link NoiseStyle} can use instead of a base style. The host compiles it
 * and builds its noise ({@link NoiseFactory}); a module only names it, and can see that the pack has it through
 * {@code PackResources.find(PackResources.EXPRESSIONS, id)}.
 */
public record PackExpression(ResourceId id) {
    public PackExpression {
        Objects.requireNonNull(id, "id");
    }
}
