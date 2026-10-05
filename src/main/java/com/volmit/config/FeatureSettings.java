package com.volmit.config;

import com.volmit.FeatureAttachment;
import com.volmit.IrisFeature;
import com.volmit.resource.ResourceId;
import java.util.List;

/** Invocation-scoped lookup of applicable settings by attachment and world position.
 * Results are immutable lists in pack declaration order, after the host applies inheritance
 * rules. Targets remain separate: this interface never silently merges dimension/region/biome
 * values. OBJECT_PLACEMENT is read from the current placement, or returns an empty list.
 * Values must be treated as read-only. Bounds and lifetime match the invocation's sampler.
 */
public interface FeatureSettings {
    <F extends IrisFeature> List<Entry<F>> at(Class<F> featureType, FeatureAttachment.Target target,
                                            int worldX, int worldY, int worldZ);

    /** source identifies the configuration owner; occurrence is unique within the pack snapshot. */
    record Entry<F extends IrisFeature>(ResourceId source, ResourceId occurrence, F value) {
        public Entry {
            java.util.Objects.requireNonNull(source, "source");
            java.util.Objects.requireNonNull(occurrence, "occurrence");
            java.util.Objects.requireNonNull(value, "value");
        }
    }
}
