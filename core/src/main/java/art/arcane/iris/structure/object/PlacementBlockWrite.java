package art.arcane.iris.structure.object;

import art.arcane.volmlib.nativelib.terrain.NativeBlockState;

/** A completed object write, after its tile data and listener have been handled. */
public record PlacementBlockWrite(int x, int y, int z, NativeBlockState data) {}
