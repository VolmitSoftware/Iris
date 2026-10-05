package com.volmit.host;

import com.volmit.FeatureAttachment;
import com.volmit.IrisFeature;
import com.volmit.config.OptionsMetadata;
import com.volmit.resource.ResourceId;
import java.util.Objects;

/** Validated registration metadata. It describes configuration, not executable ordering. */
public record FeatureDescriptor<F extends IrisFeature>(
        ResourceId id,
        String name,
        Class<F> type,
        FeatureAttachment attachment,
        OptionsMetadata options) {
    public FeatureDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Blank feature name");
        }
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(attachment, "attachment");
        Objects.requireNonNull(options, "options");
    }
}
