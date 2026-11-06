package com.volmit.service;

import com.volmit.world.PackWorld;

/**
 * Whether the lowest layer of the world, the blocks at height 0, belongs to the module that shapes the terrain, such as
 * a bedrock floor it writes itself. When it does, the host discards the blocks that placements and other stages write
 * there, so that nothing replaces the floor. A pack has one provider, since two modules could not both own the floor.
 * Without a provider the host protects nothing.
 */
@Service(Providers.ONE)
public interface ProtectedFloor {
    /**
     * Whether the host discards blocks written at height 0 of {@code world}. The answer depends on the world and its
     * settings only: the host asks once when it starts the world and keeps it while the world runs.
     */
    boolean protectsFloor(PackWorld world);
}
