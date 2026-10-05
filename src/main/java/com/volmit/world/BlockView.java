package com.volmit.world;

import com.volmit.block.BlockState;
import com.volmit.mantle.BlockBox;

/** Read-only prepared generation blocks in world coordinates, not the live server world.
 * Empty space is a non-null air state. Out-of-area access throws IndexOutOfBoundsException.
 * Views expire with the owning invocation; access afterwards throws IllegalStateException.
 */
public interface BlockView {
    BlockBox bounds();
    BlockState get(int worldX, int worldY, int worldZ);
}
