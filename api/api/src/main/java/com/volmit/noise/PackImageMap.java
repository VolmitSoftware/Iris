package com.volmit.noise;

import com.volmit.resource.ResourceId;
import java.util.Objects;

/** An image map of the loaded pack, which a {@link NoiseStyle} can use instead of a base style. The host reads the
 * image and builds its noise ({@link NoiseFactory}); a module only names it, and can see that the pack has it through
 * {@code PackResources.find(PackResources.IMAGE_MAPS, id)}.
 */
public record PackImageMap(ResourceId id) {
    public PackImageMap {
        Objects.requireNonNull(id, "id");
    }
}
