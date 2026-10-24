package com.volmit.stage;

import com.volmit.resource.ResourceId;
import java.util.Objects;

/** A stage named by id only, for soft ordering against stages that may not be installed. */
public record StageRef(ResourceId id) {
    public StageRef {
        Objects.requireNonNull(id, "id");
    }

    public static StageRef of(String id) {
        return new StageRef(ResourceId.parse(id));
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
