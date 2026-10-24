package com.volmit.host.schema;

/** How a string option is written. */
public enum TextFormat {
    /** Any string: {@code String} fields. */
    PLAIN,
    /** {@code namespace:path}, as {@link com.volmit.resource.ResourceId} parses it. */
    RESOURCE_ID,
    /** {@code namespace:path}, optionally followed by {@code [name=value,...]}, as {@link com.volmit.block.BlockState#key()} writes it. */
    BLOCK_STATE
}
