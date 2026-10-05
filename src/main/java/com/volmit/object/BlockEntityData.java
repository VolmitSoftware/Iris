package com.volmit.object;

import com.volmit.resource.ResourceId;
import java.util.Arrays;
import java.util.Objects;

/** Immutable versioned payload. The host's adapter owns interpretation and size limits.
 * Unsupported formats/versions fail explicitly; native objects and mutable buffers never escape.
 */
public final class BlockEntityData {
    private final ResourceId format;
    private final int version;
    private final byte[] payload;

    public BlockEntityData(ResourceId format, int version, byte[] payload) {
        this.format = Objects.requireNonNull(format, "format");
        if (version < 0) throw new IllegalArgumentException("Negative block entity format version");
        this.version = version;
        this.payload = Objects.requireNonNull(payload, "payload").clone();
    }

    public ResourceId format() { return format; }
    public int version() { return version; }
    public byte[] payload() { return payload.clone(); }

    @Override
    public boolean equals(Object other) {
        return other instanceof BlockEntityData data && format.equals(data.format)
                && version == data.version && Arrays.equals(payload, data.payload);
    }

    @Override
    public int hashCode() { return 31 * Objects.hash(format, version) + Arrays.hashCode(payload); }
}
