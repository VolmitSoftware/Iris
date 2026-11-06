package com.volmit.world;

import com.volmit.block.BlockState;
import com.volmit.geometry.BlockBox;

/** Blocks of an area to read, such as, through {@link BlockAccess}, the live blocks of the platform's world. X and Z are world coordinates;
 * Y is the height above the dimension's bottom (0 = bottom), also in {@link #bounds()}.
 * Empty space is a non-null air state. Out-of-area access throws IndexOutOfBoundsException.
 * Views expire with the call or invocation that handed them out; access afterwards throws IllegalStateException.
 */
public interface BlockView {
    BlockBox bounds();
    BlockState get(int worldX, int y, int worldZ);
}
