package com.volmit.support;

import com.volmit.block.BlockRegistry;
import com.volmit.item.ItemRegistry;
import com.volmit.noise.NoiseFactory;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.resource.ResourceKey;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** A pack {@code test:pack} without resources. */
public final class EmptyResources implements PackResources {
    private final UUID snapshot = UUID.randomUUID();

    @Override
    public ResourceId packId() {
        return new ResourceId("test", "pack");
    }

    @Override
    public UUID snapshotId() {
        return snapshot;
    }

    @Override
    public BlockRegistry blocks() {
        throw new AssertionError("Unused blocks");
    }

    @Override
    public ItemRegistry items() {
        throw new AssertionError("Unused items");
    }

    @Override
    public NoiseFactory noise() {
        throw new AssertionError("Unused noise");
    }

    @Override
    public <T> Optional<T> find(ResourceKey<T> key) {
        return Optional.empty();
    }

    @Override
    public List<ResourceId> ids(Class<?> type) {
        return List.of();
    }
}
