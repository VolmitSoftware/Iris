package art.arcane.iris.engine.object.placement;

import art.arcane.iris.spi.PlatformBlockState;

/** A completed object write, after its tile data and listener have been handled. */
public record PlacementBlockWrite(int x, int y, int z, PlatformBlockState data) {}
