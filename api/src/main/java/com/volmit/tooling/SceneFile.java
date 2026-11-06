package com.volmit.tooling;

import com.volmit.resource.ResourceId;
import com.volmit.resource.ResourceKind;

import java.util.Objects;

/** The pack file an editable scene entry is written in: resource {@code id} of {@code kind}. */
public record SceneFile(ResourceKind<?> kind, ResourceId id) {
    public SceneFile {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
    }
}
