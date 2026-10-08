package com.volmit.world;

import com.volmit.block.BlockState;
import com.volmit.mantle.BlockBox;

/** Read-only prepared generation blocks, not the live server world. X and Z are world coordinates;
 * Y is the height above the dimension's bottom (0 = bottom), also in {@link #bounds()}.
 * Empty space is a non-null air state. Out-of-area access throws IndexOutOfBoundsException.
 * Views expire with the owning invocation; access afterwards throws IllegalStateException.
 */
public interface BlockView {
    BlockBox bounds();
    BlockState get(int worldX, int y, int worldZ);
}
