package com.volmit.stage;

import com.volmit.object.ObjectBlock;

/** The current object block of a block pass, in object-local coordinates. Every pass starts
 * from the request's object blocks; replacements and skips are visible only to later stages
 * of the same pass for the same block.
 */
public interface BlockStageContext extends PlacementStageContext {
    ObjectBlock block();

    void replace(ObjectBlock block);

    /** Later stages of this pass do not see the current block. */
    void skip();
}
